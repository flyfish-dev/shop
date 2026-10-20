package group.flyfish.dev.xianyu.jobs;

import group.flyfish.dev.xianyu.XianyuProperties;
import group.flyfish.dev.xianyu.store.XianyuStore;
import group.flyfish.dev.xianyu.trade.ListingService;
import group.flyfish.dev.xianyu.trade.TradeService;
import group.flyfish.dev.xianyu.top.TopGateway.TopFailure;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class XianyuJobWorker {
    private final XianyuStore store;
    private final XianyuProperties properties;
    private final ListingService listings;
    private final TradeService trades;
    @Scheduled(fixedDelay=5000)
    public Mono<Void> tick() {
        return Mono.defer(() -> {
            if (!properties.isProcessingEnabled()) return Mono.empty();
            properties.requireInfrastructure();
            return store.recoverLeases().thenMany(store.ready()).concatMap(store::claim)
                    .concatMap(job -> (job.kind().equals("IMPORT_ORDER")
                            ? trades.importNative(job.seller(),String.valueOf(job.payload().get("remoteOrderId")))
                            : job.kind().startsWith("ITEM_") ? listings.execute(job) : trades.execute(job))
                            .then(store.finish(job,"SUCCESS",null)).onErrorResume(error -> {
                                boolean mutationUncertain=error instanceof TopFailure f && f.indeterminate
                                        || java.util.Set.of("REFUND","ORDER_CREATE","ITEM_PUBLISH").contains(job.kind())
                                        && !(error instanceof TopFailure);
                                String state=mutationUncertain ? "UNKNOWN" : job.attempts()>=8 ? "FAILED" : "READY";
                                String code=error.getMessage()!=null && error.getMessage().matches("[A-Z][A-Z0-9_]{0,80}")
                                        ? error.getMessage() : "PROCESSING_REQUIRES_ATTENTION";
                                Mono<Void> mark=Mono.empty();
                                if(mutationUncertain && job.kind().equals("ORDER_CREATE")) mark=store.database()
                                        .sql("UPDATE xy_order SET creation_state='UNKNOWN',revision=revision+1 WHERE order_key=:key AND remote_order_id IS NULL")
                                        .bind("key",String.valueOf(job.payload().get("orderKey"))).fetch().rowsUpdated().then();
                                if(mutationUncertain && job.kind().equals("ITEM_PUBLISH")) mark=store.database()
                                        .sql("UPDATE xy_listing SET status='UNKNOWN',revision=revision+1 WHERE listing_key=:key AND remote_item_id=''")
                                        .bind("key",String.valueOf(job.payload().get("listingKey"))).fetch().rowsUpdated().then();
                                return mark.then(store.finish(job,state,code));
                            })).then();
        });
    }
    @Scheduled(fixedDelay=300000)
    public Mono<Void> reconcileListings() {
        return Mono.defer(() -> !properties.isProcessingEnabled() ? Mono.empty() : store.database()
                .sql("SELECT l.listing_key FROM xy_listing l JOIN xy_capability c ON c.seller_key=l.seller_key AND c.operation='ITEM_QUERY' JOIN xy_seller s ON s.seller_key=l.seller_key AND s.application=c.application WHERE l.remote_item_id<>'' AND c.granted=TRUE AND c.verified=TRUE AND c.expires_at>:now AND s.enabled=TRUE AND s.expires_at>:now AND l.updated_at<:cutoff ORDER BY l.updated_at LIMIT 50")
                .bind("now",store.now()).bind("cutoff",store.now()-300).map((r,m)->r.get("listing_key",String.class)).all()
                .concatMap(key->listings.schedule(key,group.flyfish.dev.xianyu.top.TopOperation.ITEM_QUERY,"system")
                        .onErrorResume(e->store.audit("system","ITEM_RECONCILIATION_FAILED",key))).then());
    }
    @Scheduled(fixedDelay=60000)
    public Mono<Void> reconcileActiveOrders() {
        return Mono.defer(() -> !properties.isProcessingEnabled() ? Mono.empty()
                : store.database().sql("SELECT order_key,seller_key FROM xy_order WHERE creation_state='PREPARING' ORDER BY updated_at LIMIT 100")
                .map((r,m)->java.util.Map.entry(r.get("order_key",String.class),r.get("seller_key",String.class))).all()
                .concatMap(e->store.enqueue("PREPARE:"+e.getKey()+":"+(store.now()/60),e.getValue(),"PREPARE",java.util.Map.of("orderKey",e.getKey())))
                .thenMany(store.database().sql("SELECT order_key FROM xy_order WHERE remote_order_id IS NOT NULL AND (platform_state IN (1,2,3) OR refund_state IN ('PENDING','PROCESSING') OR fulfillment_state<>'SUCCESS' OR shipment_state<>'CONFIRMED') ORDER BY updated_at LIMIT 100")
                .map((r,m)->r.get("order_key",String.class)).all()).concatMap(trades::reconcile).then());
    }
}

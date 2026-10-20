package group.flyfish.dev.xianyu.trade;

import group.flyfish.dev.xianyu.XianyuProperties;
import group.flyfish.dev.xianyu.store.XianyuStore;
import group.flyfish.dev.xianyu.store.XianyuModels.*;
import group.flyfish.dev.xianyu.top.TopGateway;
import group.flyfish.dev.xianyu.top.TopOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListingService {
    private final XianyuStore store;
    private final TopGateway top;
    private final XianyuProperties properties;
    private final TransactionalOperator transaction;
    private final group.flyfish.dev.xianyu.bridge.ApprovedBridgeClient bridge;
    public Mono<Listing> save(Listing input, String actor) {
        if (input.seller()==null || input.localItemId()==null || !input.localItemId().matches("[0-9]+"))
            return Mono.error(new IllegalArgumentException("请选择商家及本地商品"));
        ListingValidator.validate(input.payload());
        if (input.nativeEnabled() && (!Boolean.TRUE.equals(input.payload().get("claim_entry_approved"))
                || properties.getMiniappEntryUrl()==null || properties.getMiniappEntryUrl().isBlank()
                || !properties.getMiniappEntryUrl().equals(input.payload().get("claim_entry_url"))))
            return Mono.error(new IllegalArgumentException("原生交易需配置已获准的领取入口"));
        String key=input.key()==null ? UUID.randomUUID().toString() : input.key();
        Listing draft=new Listing(key,input.seller(),input.localItemId(),input.remoteItemId(),input.revision(),"DRAFT",
                input.nativeEnabled(),input.payload(),Map.of());
        Mono<Void> eligibility=Mono.empty();
        if (input.nativeEnabled()) {
            java.util.List<String> skuIds=input.payload().get("item_sku_list") instanceof java.util.List<?> skus && !skus.isEmpty()
                    ? skus.stream().map(v -> String.valueOf(((Map<?,?>)v).get("outer_id"))).toList()
                    : java.util.List.of(String.valueOf(input.payload().getOrDefault("single_sku_id","")));
            eligibility=reactor.core.publisher.Flux.fromIterable(skuIds).concatMap(sku -> bridge.shop("eligible",
                            Map.of("itemId",input.localItemId(),"skuId",sku)))
                    .doOnNext(eligible -> { if (!eligible.asBoolean()) throw new IllegalArgumentException("该SKU须通过小程序完成购买前置条件"); }).then();
        }
        return eligibility.then(store.listing(key).flatMap(old -> {
            if ("UNKNOWN".equals(old.status())) return Mono.error(new IllegalArgumentException("发布结果待核查，先核对远端商品再编辑"));
            if (!old.seller().equals(input.seller()) || !old.localItemId().equals(input.localItemId())
                    || !java.util.Objects.equals(old.remoteItemId(),input.remoteItemId()))
                return Mono.error(new IllegalArgumentException("商品映射建立后不可直接更换"));
            if (old.remoteItemId()!=null && !old.remoteItemId().isBlank()
                    && !java.util.Objects.equals(old.payload().get("channel_cat_id"), input.payload().get("channel_cat_id")))
                return Mono.error(new IllegalArgumentException("已发布商品不可更换类目"));
            return persist(new Listing(draft.key(),draft.seller(),draft.localItemId(),old.remoteItemId(),draft.revision(),"DRAFT",
                    draft.nativeEnabled(),draft.payload(),old.remoteSnapshot()),false,actor);
        }).switchIfEmpty(Mono.defer(() -> persist(draft,true,actor))));
    }
    private Mono<Listing> persist(Listing input, boolean insert, String actor) {
        return store.saveListing(input,insert).flatMap(ok -> ok ? store.audit(actor,"LISTING_SAVE",input.key()).then(store.listing(input.key()))
                : Mono.error(new IllegalStateException("商品版本已变化，请刷新"))).as(transaction::transactional);
    }
    public Mono<Void> schedule(String key, TopOperation operation, String actor) {
        if (!java.util.Set.of(TopOperation.ITEM_PUBLISH,TopOperation.ITEM_EDIT,TopOperation.ITEM_QUERY,TopOperation.ITEM_DOWNSHELF).contains(operation))
            return Mono.error(new IllegalArgumentException("商品操作不支持"));
        if (operation==TopOperation.ITEM_PUBLISH || operation==TopOperation.ITEM_EDIT) properties.requireSales();
        return store.listing(key).switchIfEmpty(Mono.error(new IllegalArgumentException("商品映射不存在")))
                .flatMap(l -> {
                    if ("UNKNOWN".equals(l.status()) && operation!=TopOperation.ITEM_QUERY)
                        return Mono.error(new IllegalArgumentException("发布结果待核查，禁止再次发布"));
                    if (operation==TopOperation.ITEM_PUBLISH && l.remoteItemId()!=null && !l.remoteItemId().isBlank())
                        return Mono.error(new IllegalArgumentException("商品已发布，请使用编辑"));
                    if (operation!=TopOperation.ITEM_PUBLISH && (l.remoteItemId()==null || l.remoteItemId().isBlank()))
                        return Mono.error(new IllegalArgumentException("请先关联或发布闲鱼商品"));
                    return top.require(l.seller(),operation).flatMap(cap -> {
                        if (!cap.categoryIds().contains(String.valueOf(l.payload().get("channel_cat_id"))))
                            return Mono.error(new IllegalArgumentException("渠道类目尚未验证"));
                        return store.enqueue(operation.name()+":"+key+":"+l.revision(),l.seller(),operation.name(),
                                Map.of("listingKey",key,"revision",l.revision())).then(store.audit(actor,operation.name(),key));
                    });
                }).as(transaction::transactional);
    }
    public Mono<Listing> resolvePublish(String key,String remote,String evidence,String actor) {
        if(remote==null || !remote.matches("[0-9]+") || evidence==null || evidence.length()<20 || evidence.length()>1000)
            return Mono.error(new IllegalArgumentException("请提供远端商品号和完整核查依据"));
        return store.listing(key).filter(l->"UNKNOWN".equals(l.status()) && (l.remoteItemId()==null || l.remoteItemId().isBlank()))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("商品无需核查关联")))
                .flatMap(l->top.call(l.seller(),TopOperation.ITEM_QUERY,Map.of("item_id",remote,"need_sku",true)).flatMap(result->{
                    var item=result.path("data");
                    if(!remote.equals(item.path("item_id").asText()) || !externalCode(l.key()).equals(item.path("outer_id").asText())
                            || !String.valueOf(l.payload().get("channel_cat_id")).equals(item.path("channel_cat_id").asText()))
                        return Mono.error(new IllegalArgumentException("远端商品与发布请求不匹配，不能关联"));
                    Listing linked=new Listing(l.key(),l.seller(),l.localItemId(),remote,l.revision(),"AWAITING_CONFIRMATION",l.nativeEnabled(),l.payload(),store.read(result.toString()));
                    return store.saveListing(linked,false).flatMap(ok->ok ? store.enqueue("ITEM_QUERY:"+key+":"+(l.revision()+1),l.seller(),"ITEM_QUERY",Map.of("listingKey",key,"revision",l.revision()+1))
                            .then(store.database().sql("INSERT INTO xy_publish_resolution VALUES (:key,:actor,:remote,:evidence,:now)")
                                    .bind("key",key).bind("actor",actor).bind("remote",remote).bind("evidence",evidence).bind("now",store.now()).fetch().rowsUpdated())
                            .then(store.database().sql("UPDATE xy_job SET status='RESOLVED',last_error='REMOTE_RESULT_CONFIRMED' WHERE kind='ITEM_PUBLISH' AND status='UNKNOWN' AND job_key LIKE :prefix")
                                    .bind("prefix","ITEM_PUBLISH:"+key+":%").fetch().rowsUpdated())
                            .then(store.audit(actor,"PUBLISH_RESOLVE",key)).then(store.listing(key)) : Mono.error(new IllegalStateException("LISTING_REVISION_CONFLICT")))
                            .as(transaction::transactional);
                }));
    }
    public Mono<Void> execute(Job job) {
        return store.listing(String.valueOf(job.payload().get("listingKey"))).flatMap(l -> {
            TopOperation operation=TopOperation.valueOf(job.kind());
            if (operation!=TopOperation.ITEM_QUERY && l.revision()!=((Number)job.payload().get("revision")).longValue())
                return Mono.error(new IllegalStateException("LISTING_REVISION_CONFLICT"));
            Map<String,Object> p=new LinkedHashMap<>(l.payload());
            p.keySet().removeIf(k -> !java.util.Set.of("title","desc","images","channel_cat_id","pv_list","reserve_price",
                    "original_price","quantity","item_sku_list","stuff_status","division_id","item_biz_type","outer_id","transport_fee").contains(k));
            if (operation==TopOperation.ITEM_PUBLISH) {
                ListingValidator.validate(p); p.put("outer_id",externalCode(l.key()));
            } else if (operation==TopOperation.ITEM_EDIT) {
                p.put("item_id",l.remoteItemId());
                if (p.get("item_sku_list") instanceof java.util.List<?> skus && !skus.isEmpty()) {
                    if (!(l.remoteSnapshot().get("skuBindings") instanceof Map<?,?> bindings)) throw new IllegalStateException("SKU_BINDINGS_UNVERIFIED");
                    p.put("item_sku_list",skus.stream().map(value -> {
                        Map<String,Object> sku=new LinkedHashMap<>((Map<String,Object>)value);
                        String remoteSku=bindings.entrySet().stream().filter(e -> String.valueOf(e.getValue()).equals(String.valueOf(sku.get("outer_id"))))
                                .map(e -> String.valueOf(e.getKey())).findFirst().orElseThrow(()->new IllegalStateException("SKU_BINDINGS_UNVERIFIED"));
                        sku.put("sku_id",remoteSku);return sku;
                    }).toList());
                }
            }
            else p=new LinkedHashMap<>(operation==TopOperation.ITEM_QUERY
                    ? Map.of("item_id",l.remoteItemId(),"need_sku",true) : Map.of("item_id",l.remoteItemId()));
            Mono<Void> ownership=operation==TopOperation.ITEM_PUBLISH || operation==TopOperation.ITEM_EDIT
                    ? reactor.core.publisher.Flux.fromIterable((java.util.List<?>)l.payload().get("images"))
                    .concatMap(id -> store.database().sql("SELECT COUNT(*) AS total FROM xy_media WHERE media_id=:id AND seller_key=:seller")
                            .bind("id",String.valueOf(id)).bind("seller",l.seller()).map((r,m)->r.get("total",Long.class)).one()
                            .doOnNext(count -> { if (count!=1) throw new IllegalArgumentException("媒体文件不属于当前商家"); })).then() : Mono.empty();
            return ownership.then(top.call(l.seller(),operation,p)).flatMap(result -> {
                var data=result.has("data") ? result.path("data") : result.has("module") ? result.path("module") : result;
                String remote=l.remoteItemId();
                if (operation==TopOperation.ITEM_PUBLISH) {
                    remote=data.path("item_id").asText();
                    if (!remote.matches("[0-9]+")) throw new TopGateway.TopFailure("PUBLISH_RESULT_UNCONFIRMED",true);
                }
                if (operation==TopOperation.ITEM_DOWNSHELF && !data.asBoolean())
                    throw new TopGateway.TopFailure("DOWNSHELF_RESULT_UNCONFIRMED",true);
                if (operation==TopOperation.ITEM_QUERY && (!l.remoteItemId().equals(data.path("item_id").asText())
                        || !String.valueOf(l.payload().get("channel_cat_id")).equals(data.path("channel_cat_id").asText())))
                    throw new IllegalStateException("REMOTE_ITEM_MAPPING_MISMATCH");
                Map<String,Object> snapshot=new LinkedHashMap<>(store.read(result.toString()));
                Map<String,String> bindings=new LinkedHashMap<>();
                var skuList=data.path("item_sku_list");
                if (skuList.isObject()) skuList=skuList.path("idle_item_api_sku_do");
                if (skuList.isArray()) for (var sku:skuList) {
                    if (sku.path("sku_id").asText().matches("[0-9]+") && sku.path("outer_id").asText().matches("[0-9]+"))
                        bindings.put(sku.path("sku_id").asText(),sku.path("outer_id").asText());
                }
                if (!bindings.isEmpty()) snapshot.put("skuBindings",bindings);
                else if (l.remoteSnapshot().containsKey("skuBindings")) snapshot.put("skuBindings",l.remoteSnapshot().get("skuBindings"));
                String status="AWAITING_CONFIRMATION";
                if(operation==TopOperation.ITEM_QUERY) status=switch(data.path("status").asInt(99)) {
                    case 0 -> "SYNCED";case -2 -> "DOWN";case -1 -> "DELETED";default -> "REQUIRES_ATTENTION";
                };
                if(operation==TopOperation.ITEM_QUERY && "SYNCED".equals(status)
                        && (!String.valueOf(l.payload().get("title")).equals(data.path("title").asText())
                        || !String.valueOf(l.payload().get("desc")).equals(data.path("desc").asText())
                        || ListingValidator.money(l.payload().get("reserve_price"))!=ListingValidator.money(data.path("reserve_price").asText())
                        || Integer.parseInt(String.valueOf(l.payload().get("quantity")))!=data.path("quantity").asInt(-1))) status="DIFFERENT";
                Listing next=new Listing(l.key(),l.seller(),l.localItemId(),remote,l.revision(),status,l.nativeEnabled(),l.payload(),snapshot);
                return store.saveListing(next,false).flatMap(ok -> {
                    if (!ok) return Mono.error(new IllegalStateException("LISTING_REVISION_CONFLICT"));
                    return operation==TopOperation.ITEM_QUERY ? Mono.empty()
                            : store.enqueue("ITEM_QUERY:"+l.key()+":"+(l.revision()+1),l.seller(),"ITEM_QUERY",
                                    Map.of("listingKey",l.key(),"revision",l.revision()+1));
                }).as(transaction::transactional);
            });
        });
    }
    private String externalCode(String key) { return group.flyfish.dev.xianyu.security.XianyuIdentityService.hash(key).substring(0,30); }
}

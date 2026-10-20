package group.flyfish.dev.xianyu.jobs;

import group.flyfish.dev.xianyu.XianyuProperties;
import group.flyfish.dev.xianyu.store.XianyuStore;
import group.flyfish.dev.xianyu.security.XianyuIdentityService;
import group.flyfish.dev.xianyu.top.TopOperation;
import group.flyfish.dev.xianyu.top.TopGateway;
import group.flyfish.dev.xianyu.trade.TradeService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class XianyuMessageConsumer {
    private final XianyuProperties properties;
    private final XianyuStore store;
    private final TopGateway top;
    private final TradeService trades;
    private final JsonMapper json;
    private final TransactionalOperator transaction;
    @Scheduled(fixedDelay=10000)
    public Mono<Void> poll() {
        return Mono.defer(() -> {
            if (!properties.isProcessingEnabled()) return Mono.empty();
            return store.sellers().filter(s -> s.enabled()).distinct(s -> s.application())
                    .concatMap(s -> top.call(s.key(),TopOperation.MESSAGE_CONSUME,Map.of("quantity",20))
                            .flatMapMany(result -> {
                                var messages=result.path("messages");
                                if (messages.isObject()) messages=messages.path("tmc_message");
                                return messages.isArray() ? Flux.fromIterable(messages) : Flux.empty();
                            }).concatMap(message -> {
                                String id=message.path("id").asText(),topic=message.path("topic").asText();
                                String content=message.path("content").asText();
                                if (!id.matches("[0-9]+") || topic.isBlank() || content.length()>2_000_000)
                                    return Mono.error(new IllegalStateException("MESSAGE_RESPONSE_SCHEMA"));
                                return store.database().sql("SELECT seller_key FROM xy_seller_identity WHERE user_hash=:hash")
                                        .bind("hash",XianyuIdentityService.hash(message.path("user_id").asText()))
                                        .map((r,m)->r.get("seller_key",String.class)).one().defaultIfEmpty("unmapped-"+s.application())
                                        .flatMap(seller -> store.receive(id,seller,topic,content)
                                                .flatMap(fresh -> !fresh ? Mono.empty() : store.capability(seller,"MESSAGE_CONSUME",s.application())
                                                        .filter(cap -> cap.granted() && cap.verified() && cap.expiresAt()>store.now() && cap.topics().contains(topic)).flatMap(cap -> {
                                                            tools.jackson.databind.JsonNode payload;
                                                            try { payload=json.readTree(content); }
                                                            catch (RuntimeException e) { return store.messageState(id,seller,"QUARANTINED","MALFORMED_JSON").thenReturn(true); }
                                                            String remote=payload.path("order_id").asText();
                                                            if (!remote.matches("[0-9]+")) return store.messageState(id,seller,"QUARANTINED","ORDER_ID_MISSING").thenReturn(true);
                                                            return store.enqueue("IMPORT_ORDER:"+seller+":"+id,seller,"IMPORT_ORDER",
                                                                    Map.of("remoteOrderId",remote)).then(store.messageState(id,seller,"QUEUED","")).thenReturn(true);
                                                        }).defaultIfEmpty(false).flatMap(handled->handled ? Mono.empty()
                                                                : store.messageState(id,seller,"QUARANTINED","TOPIC_OR_SELLER_UNVERIFIED"))).as(transaction::transactional))
                                        .then(top.call(s.key(),TopOperation.MESSAGE_CONFIRM,Map.of("s_message_ids",id))).then();
                            }).onErrorResume(e -> store.audit("system","MESSAGE_POLL_FAILED",s.key()))).then();
        });
    }
}

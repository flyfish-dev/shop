package group.flyfish.dev.xianyu.trade;

import group.flyfish.dev.xianyu.XianyuProperties;
import group.flyfish.dev.xianyu.bridge.ApprovedBridgeClient;
import group.flyfish.dev.xianyu.security.XianyuIdentityService;
import group.flyfish.dev.xianyu.store.XianyuStore;
import group.flyfish.dev.xianyu.store.XianyuModels.*;
import group.flyfish.dev.xianyu.top.TopGateway;
import group.flyfish.dev.xianyu.top.TopOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.Map;
import java.util.LinkedHashMap;

@Service
@RequiredArgsConstructor
public class TradeService {
    private final XianyuStore store;
    private final TopGateway top;
    private final ApprovedBridgeClient bridge;
    private final XianyuIdentityService identities;
    private final XianyuProperties properties;
    private final TransactionalOperator transaction;
    private final ChannelCatalogService catalog;

    public Mono<Void> importNative(String seller, String remoteId) {
        if (!remoteId.matches("[0-9]+")) return Mono.error(new IllegalArgumentException("平台订单号不正确"));
        String key=XianyuIdentityService.hash(seller+":"+remoteId);
        return store.database().sql("SELECT order_key FROM xy_order WHERE seller_key=:seller AND remote_order_id=:remote")
                .bind("seller",seller).bind("remote",remoteId).map((r,m)->r.get("order_key",String.class)).one()
                .defaultIfEmpty("").flatMap(existingKey -> !existingKey.isBlank() ? reconcile(existingKey)
                : top.call(seller,TopOperation.ORDER_QUERY,Map.of("biz_order_id",remoteId)).flatMap(result -> {
                    JsonNode r=data(result); String itemId=r.path("item").path("item_id").asText();
                    if (!remoteId.equals(r.path("biz_order_id").asText()) || !r.has("encryption_buyer_id") || r.path("payment").asLong(-1)<=0)
                        return Mono.error(new IllegalStateException("NATIVE_ORDER_SCHEMA_UNVERIFIED"));
                    return store.nativeListing(seller,itemId)
                            .switchIfEmpty(Mono.error(new IllegalStateException("NATIVE_ITEM_UNMAPPED")))
                            .flatMap(l -> {
                                Map<String,Object> snapshot=new LinkedHashMap<>(); snapshot.put("remote",store.read(r.toString()));
                                snapshot.put("listingKey",l.key()); snapshot.put("localItemId",l.localItemId());
                                Order o=new Order(key,seller,null,remoteId,null,"XIANYU_NATIVE",r.path("payment").asLong(),"CREATED",
                                        r.path("order_status").asInt(),"NONE",0,"WAITING_BUYER","WAITING",snapshot,0,store.now()+900);
                                return store.saveOrder(o,true).then(identities.subjectForPlatformId(r.path("encryption_buyer_id").asText())
                                        .flatMap(subject -> identities.identity(subject)).flatMap(buyer -> claimNative(key,buyer)).then());
                            });
                }));
    }
    public Mono<Order> claimNative(String key, Identity buyer) {
        return store.order(key).flatMap(o -> {
            if (!"XIANYU_NATIVE".equals(o.channel())) return Mono.error(new IllegalArgumentException("订单渠道不正确"));
            Map<?,?> remote=(Map<?,?>)o.snapshot().get("remote");
            return identities.subjectForPlatformId(String.valueOf(remote.get("encryption_buyer_id")))
                    .filter(subject -> subject.equals(buyer.subject())).switchIfEmpty(Mono.error(new IllegalArgumentException("无权领取该订单")))
                    .flatMap(subject -> {
                        if (o.localOrderNo()!=null) return Mono.just(o);
                        return store.listing(String.valueOf(o.snapshot().get("listingKey"))).flatMap(l -> {
                            Map<String,Object> order=new LinkedHashMap<>(); order.put("itemId",l.localItemId());
                            order.put("count",remote.get("buy_amount"));
                            String sku=String.valueOf(remote.getOrDefault("sku",null));
                            String remoteSku=sku.split("\\|",2)[0];
                            if (l.remoteSnapshot().get("skuBindings") instanceof Map<?,?> bindings) {
                                Object local=bindings.get(remoteSku);
                                if (local==null) throw new IllegalStateException("NATIVE_SKU_UNMAPPED");
                                order.put("skuId",String.valueOf(local));
                            } else if (l.payload().get("item_sku_list") instanceof java.util.List<?> list && !list.isEmpty())
                                throw new IllegalStateException("NATIVE_SKU_UNMAPPED");
                            else if (l.payload().get("single_sku_id")!=null) order.put("skuId",l.payload().get("single_sku_id"));
                            return bridge.shop("prepare",Map.of("token",identities.portalToken(buyer),"channelKey",o.key(),
                                    "channel","XIANYU_NATIVE","amount",yuan(o.amountCents()),"order",order)).flatMap(prepared -> {
                                Map<String,Object> snapshot=new LinkedHashMap<>(o.snapshot());snapshot.put("local",store.read(prepared.toString()));
                                Order next=new Order(o.key(),o.seller(),prepared.path("order").path("orderNo").asText(),o.remoteOrderId(),buyer.subject(),
                                        o.channel(),o.amountCents(),o.creationState(),o.platformState(),o.refundState(),o.refundCents(),"WAITING",o.shipmentState(),snapshot,o.revision(),o.expiresAt());
                                return save(next,"ORDER_QUERY").then(store.order(key));
                            });
                        });
                    });
        });
    }

    public Mono<Map<String,Object>> prepare(Identity buyer, Map<String,Object> input, String idempotency) {
        properties.requireSales();
        if (!properties.isMiniappEnabled() || idempotency==null || !idempotency.matches("[a-zA-Z0-9-]{16,80}"))
            return Mono.error(new IllegalArgumentException("有效的请求编号和小程序配置必不可少"));
        String key=XianyuIdentityService.hash(buyer.subject()+":"+idempotency);
        String fingerprint=XianyuIdentityService.hash(store.write(input));
        return catalog.requireSelection(String.valueOf(input.get("itemId")),input.get("skuId")).then(store.order(key)).flatMap(existing -> {
            if (!fingerprint.equals(existing.snapshot().get("fingerprint")))
                return Mono.error(new IllegalArgumentException("相同请求编号不能用于不同商品信息"));
            return "PREPARING".equals(existing.creationState()) ? completePreparation(existing,buyer,input) : creationResponse(existing);
        }).switchIfEmpty(Mono.defer(() -> {
            String seller=properties.getMiniappSeller();
            if (seller==null || seller.isBlank() || properties.getGenericItemId()==null || properties.getGenericItemId().isBlank()
                    || properties.getBusinessCode()==null || properties.getBusinessCode().isBlank()
                    || properties.getVirtualItemCode()==null || properties.getVirtualItemCode().isBlank())
                return Mono.error(new IllegalStateException("MINIAPP_BUSINESS_NOT_CONFIGURED"));
            Order reserved=new Order(key,seller,null,null,buyer.subject(),"XIANYU_MINIAPP",0,"PREPARING",0,
                    "NONE",0,"WAITING","WAITING",Map.of("fingerprint",fingerprint,"input",input),0,store.now()+900);
            return top.require(seller,buyer.application(),TopOperation.ORDER_CREATE).then(store.saveOrder(reserved,true))
                    .onErrorResume(org.springframework.dao.DuplicateKeyException.class,e -> Mono.just(false))
                    .then(store.enqueue("PREPARE:"+key,seller,"PREPARE",Map.of("orderKey",key)))
                    .then(store.order(key)).flatMap(o -> {
                        if (!fingerprint.equals(o.snapshot().get("fingerprint"))) return Mono.error(new IllegalArgumentException("请求编号已被使用"));
                        return "PREPARING".equals(o.creationState()) ? completePreparation(o,buyer,input) : creationResponse(o);
                    });
        }));
    }
    private Mono<Map<String,Object>> completePreparation(Order reserved,Identity buyer,Map<String,Object> input) {
        return bridge.shop("prepare",Map.of("token",identities.portalToken(buyer),"channel","XIANYU_MINIAPP",
                "channelKey",reserved.key(),"order",input)).flatMap(prepared -> {
            if (prepared.has("success") && !prepared.path("success").asBoolean())
                return Mono.error(new IllegalArgumentException(prepared.path("message").asText("订单校验未通过")));
            JsonNode local=prepared.path("order");
            long amount=new BigDecimal(local.path("amount").asText()).movePointRight(2).longValueExact();
            if (amount<=0 || !"CNY".equals(local.path("currency").asText())) return Mono.error(new IllegalStateException("CHANNEL_AMOUNT_INVALID"));
            Map<String,Object> snapshot=merge(reserved.snapshot(),"local",store.read(prepared.toString()));
            Order order=new Order(reserved.key(),reserved.seller(),local.path("orderNo").asText(),null,buyer.subject(),"XIANYU_MINIAPP",amount,
                    "READY",1,"NONE",0,"WAITING","WAITING",snapshot,reserved.revision(),reserved.expiresAt());
            return store.saveOrder(order,false).flatMap(ok -> ok ? store.enqueue("ORDER_CREATE:"+reserved.key(),reserved.seller(),"ORDER_CREATE",Map.of("orderKey",reserved.key()))
                    : Mono.empty()).then(store.order(reserved.key())).flatMap(this::creationResponse).as(transaction::transactional);
        });
    }
    private Mono<Map<String,Object>> creationResponse(Order order) {
        Map<String,Object> local=order.snapshot().get("local") instanceof Map<?,?> m ? new LinkedHashMap<>((Map<String,Object>)m) : new LinkedHashMap<>();
        if (local.isEmpty()) return Mono.error(new IllegalStateException("ORDER_PREPARATION_REQUIRES_RECONCILIATION"));
        local.put("payment",payment(order)); return Mono.just(local);
    }
    public Map<String,Object> payment(Order order) {
        Map<String,Object> response=new LinkedHashMap<>(); response.put("provider","xianyu"); response.put("tradeType","miniapp");
        response.put("action",order.remoteOrderId()==null ? "XIANYU_PENDING" : "XIANYU_TRADE_PAY");
        response.put("platformOrderId",order.remoteOrderId()); response.put("creationState",order.creationState());
        return response;
    }
    public Mono<Order> owned(String localNo, Identity identity) {
        return store.database().sql("SELECT order_key FROM xy_order WHERE local_order_no=:no AND buyer_subject=:subject")
                .bind("no",localNo).bind("subject",identity.subject()).map((r,m)->r.get("order_key",String.class)).one()
                .switchIfEmpty(Mono.error(new IllegalArgumentException("订单不存在或无权访问"))).flatMap(store::order);
    }
    public Mono<Void> reconcile(String key) {
        return store.order(key).flatMap(o -> {
            if (o.remoteOrderId()==null) return Mono.error(new IllegalStateException("REMOTE_ORDER_UNKNOWN"));
            return store.enqueue("ORDER_QUERY:"+key+":"+(store.now()/30),o.seller(),"ORDER_QUERY",Map.of("orderKey",key));
        });
    }
    public Mono<Void> execute(Job job) {
        return store.order(String.valueOf(job.payload().get("orderKey"))).switchIfEmpty(Mono.error(new IllegalStateException("ORDER_NOT_FOUND")))
                .flatMap(o -> switch (job.kind()) {
                    case "PREPARE" -> !"PREPARING".equals(o.creationState()) ? Mono.empty()
                            : identities.identity(o.buyerSubject()).flatMap(buyer -> completePreparation(o,buyer,
                                    (Map<String,Object>)o.snapshot().get("input"))).then();
                    case "ORDER_CREATE" -> createRemote(o);
                    case "ORDER_QUERY" -> query(o);
                    case "FULFILL" -> fulfill(o);
                    case "SHIP" -> ship(o);
                    case "REFUND_QUERY" -> refundQuery(o);
                    case "REFUND" -> refund(o,job);
                    case "ORDER_CLOSE" -> close(o);
                    default -> Mono.error(new IllegalStateException("JOB_UNSUPPORTED"));
                });
    }
    private Mono<Void> createRemote(Order o) {
        if (o.remoteOrderId()!=null) return Mono.empty();
        if (!properties.isSalesEnabled()) return Mono.error(new TopGateway.TopFailure("SALES_DISABLED",false));
        Map<?,?> local=o.snapshot().get("local") instanceof Map<?,?> m ? m : Map.of();
        Map<?,?> localOrder=local.get("order") instanceof Map<?,?> m ? m : Map.of();
        int count=localOrder.get("count") instanceof Number n ? n.intValue() : 1;
        if(count<1 || o.amountCents()%count!=0) return Mono.error(new IllegalStateException("CHANNEL_UNIT_AMOUNT_INVALID"));
        return identities.identity(o.buyerSubject()).switchIfEmpty(Mono.error(new IllegalStateException("BUYER_AUTH_EXPIRED")))
                .flatMap(buyer -> top.buyerCall(o.seller(),buyer.application(),identities.buyerToken(buyer),TopOperation.ORDER_CREATE,
                        Map.of("item_id",properties.getGenericItemId(),"x_global_biz_code",properties.getBusinessCode(),
                                "amount",o.amountCents()/count,"buy_quantity",count,"virtual_item_order",true,"virtual_item_code",properties.getVirtualItemCode())))
                .flatMap(result -> {
                    String remote=data(result).path("biz_order_id").asText();
                    if (!remote.matches("[0-9]+")) throw new TopGateway.TopFailure("CREATE_RESULT_UNCONFIRMED",true);
                    return save(OrderTransitions.change(o,"CREATED",remote,o.fulfillmentState(),o.shipmentState(),o.refundState(),o.refundCents(),o.snapshot()),"ORDER_QUERY");
                });
    }
    private Mono<Void> query(Order o) {
        return top.call(o.seller(),TopOperation.ORDER_QUERY,Map.of("biz_order_id",o.remoteOrderId())).flatMap(result -> {
            JsonNode remote=data(result);
            if (!o.remoteOrderId().equals(remote.path("biz_order_id").asText())) throw new IllegalStateException("ORDER_ID_MISMATCH");
            long amount=remote.path("payment").asLong(-1);
            Order next=OrderTransitions.observe(o,remote.path("order_status").asInt(-1),amount,merge(o.snapshot(),"remote",store.read(remote.toString())));
            String follow=next.frozen() ? "REFUND_QUERY" : next.paid() && !"SUCCESS".equals(next.fulfillmentState()) ? "FULFILL"
                    : next.platformState()==1 && next.expiresAt()<store.now() ? "ORDER_CLOSE" : null;
            return save(next,follow)
                    .then(next.platformState()==5 && next.localOrderNo()!=null
                            ? bridge.shop("paid",Map.of("orderNo",next.localOrderNo(),"channelKey",next.key(),"amount",yuan(next.amountCents())))
                            .then(coreRefund(next,"REFUNDED",next.amountCents())) : Mono.empty())
                    .then(next.platformState()==6 && next.localOrderNo()!=null
                            ? bridge.shop("close",Map.of("orderNo",next.localOrderNo(),"channelKey",next.key())).then() : Mono.empty());
        });
    }
    private Mono<Void> close(Order o) {
        return top.call(o.seller(),TopOperation.ORDER_QUERY,Map.of("biz_order_id",o.remoteOrderId())).flatMap(result -> {
            if (data(result).path("order_status").asInt()!=1) return reconcile(o.key());
            return top.call(o.seller(),TopOperation.ORDER_CLOSE,Map.of("biz_order_id",o.remoteOrderId(),"close_reason","订单支付超时"))
                    .flatMap(closed -> {
                        if(!data(closed).asBoolean()) return Mono.error(new TopGateway.TopFailure("CLOSE_RESULT_UNCONFIRMED",true));
                        return reconcile(o.key());
                    });
        });
    }
    private Mono<Void> fulfill(Order o) {
        if (!o.paid() || o.frozen() || o.localOrderNo()==null) return Mono.empty();
        return bridge.shop("paid",Map.of("orderNo",o.localOrderNo(),"channelKey",o.key(),"amount",yuan(o.amountCents())))
                .then(bridge.shop("fulfill",Map.of("orderNo",o.localOrderNo()))).flatMap(result -> {
                    String state=result.path("deliveryStatus").asText();
                    if ("PROCESSING".equals(state)) return Mono.error(new IllegalStateException("DELIVERY_RESULT_PENDING"));
                    if (!"SUCCESS".equals(state)) return Mono.error(new IllegalStateException("DELIVERY_REQUIRES_ATTENTION"));
                    return save(OrderTransitions.change(o,o.creationState(),o.remoteOrderId(),"SUCCESS",o.shipmentState(),o.refundState(),o.refundCents(),o.snapshot()),"SHIP");
                });
    }
    private Mono<Void> ship(Order o) {
        if (!"SUCCESS".equals(o.fulfillmentState()) || o.frozen()) return Mono.empty();
        if ("CONFIRMED".equals(o.shipmentState())) return Mono.empty();
        TopOperation op="XIANYU_NATIVE".equals(o.channel()) ? TopOperation.DUMMY_CONSIGN : TopOperation.VIRTUAL_DELIVERY;
        Map<String,Object> body=op==TopOperation.DUMMY_CONSIGN
                ? Map.of("order_id",o.remoteOrderId(),"trade_text","数字服务交付已完成") : Map.of("biz_order_id",o.remoteOrderId());
        return top.call(o.seller(),op,body).flatMap(result -> {
            if (!data(result).asBoolean()) throw new TopGateway.TopFailure("SHIP_RESULT_UNCONFIRMED",true);
            return save(OrderTransitions.change(o,o.creationState(),o.remoteOrderId(),o.fulfillmentState(),"SUBMITTED",o.refundState(),o.refundCents(),o.snapshot()),"ORDER_QUERY");
        });
    }
    public Mono<Void> requestRefund(String key, long cents, String requestId, TopOperation operation, String actor, String decision, String reason) {
        if (!java.util.Set.of(TopOperation.PART_REFUND,TopOperation.AFTER_SEND_REFUND,TopOperation.DEAL_REFUND).contains(operation)
                || requestId==null || !requestId.matches("[a-zA-Z0-9-]{16,80}")) return Mono.error(new IllegalArgumentException("退款请求不正确"));
        if (!java.util.Set.of("AGREE_REFUND","REFUSE_REFUND").contains(decision) || reason==null || reason.isBlank() || reason.length()>500)
            return Mono.error(new IllegalArgumentException("请填写售后审核意见"));
        String jobKey="REFUND:"+key+":"+requestId;
        Map<String,Object> payload=Map.of("orderKey",key,"amount",cents,"idemId",requestId,"operation",operation.name(),"decision",decision,"reason",reason);
        return store.job(jobKey).flatMap(existing -> {
            if (!existing.payload().equals(store.read(store.write(payload)))) return Mono.error(new IllegalArgumentException("退款请求编号不可用于不同申请"));
            return Mono.just(true);
        }).defaultIfEmpty(false).flatMap(exists -> exists ? Mono.empty() : store.order(key).flatMap(o -> {
            boolean activeRefund=java.util.Set.of("PROCESSING","PENDING","REFUNDED").contains(o.refundState());
            if (operation==TopOperation.DEAL_REFUND) activeRefund=!"PENDING".equals(o.refundState());
            if (!o.paid() || activeRefund || cents<=0 || cents>o.amountCents()-o.refundCents()
                    || operation==TopOperation.AFTER_SEND_REFUND && (o.platformState()<3 || cents!=o.amountCents()-o.refundCents()))
                return Mono.error(new IllegalArgumentException("订单状态或可退款金额不正确"));
            return top.require(o.seller(),operation).then(coreRefund(o,"PROCESSING",o.refundCents()))
                    .then(save(OrderTransitions.change(o,o.creationState(),o.remoteOrderId(),o.fulfillmentState(),o.shipmentState(),"PROCESSING",o.refundCents(),o.snapshot()),null)
                            .then(store.enqueue(jobKey,o.seller(),"REFUND",payload))
                            .then(store.audit(actor,"REFUND_REQUEST",key)).as(transaction::transactional));
        }));
    }
    private Mono<Void> refund(Order o, Job j) {
        TopOperation op=TopOperation.valueOf(String.valueOf(j.payload().get("operation")));
        long cents=((Number)j.payload().get("amount")).longValue();
        if (!"PROCESSING".equals(o.refundState())) return Mono.error(new IllegalStateException("REFUND_STATE_CONFLICT"));
        Map<String,Object> body=new LinkedHashMap<>(); body.put("biz_order_id",o.remoteOrderId());
        if (op==TopOperation.PART_REFUND) { body.put("refund_fee",cents); body.put("idem_id",j.payload().get("idemId")); }
        if (op==TopOperation.DEAL_REFUND) { body.put("operation",j.payload().get("decision")); body.put("leave_message",j.payload().get("reason")); }
        return top.call(o.seller(),op,body).flatMap(result -> {
            if (!data(result).asBoolean() && !data(result).has("refund_status"))
                throw new TopGateway.TopFailure("REFUND_RESULT_UNCONFIRMED",true);
            if (op==TopOperation.PART_REFUND) {
                long amount=o.refundCents()+cents;
                String state=amount==o.amountCents() ? "REFUNDED" : "PARTIAL";
                return coreRefund(o,state,amount)
                        .then(save(OrderTransitions.change(o,o.creationState(),o.remoteOrderId(),o.fulfillmentState(),o.shipmentState(),state,amount,
                                merge(o.snapshot(),"refundRequest",j.payload())),null));
            }
            return save(OrderTransitions.change(o,o.creationState(),o.remoteOrderId(),o.fulfillmentState(),o.shipmentState(),
                    "PENDING",o.refundCents(),merge(o.snapshot(),"refundRequest",j.payload())),"REFUND_QUERY");
        });
    }
    private Mono<Void> refundQuery(Order o) {
        return top.call(o.seller(),TopOperation.REFUND_QUERY,Map.of("biz_order_id",o.remoteOrderId())).flatMap(result -> {
            JsonNode r=data(result); int state=r.path("refund_status").asInt(-1);
            String status=switch(state) { case 5 -> r.path("refund_fee").asLong()==o.amountCents() ? "REFUNDED" : "PARTIAL";
                case 4,11 -> "CLOSED"; case 6 -> "REJECTED"; case 9 -> java.util.Set.of("PROCESSING","PENDING","PARTIAL","REFUNDED").contains(o.refundState()) ? o.refundState() : "NONE"; case 1,2,3,8 -> "PENDING";
                default -> throw new IllegalStateException("REFUND_RESPONSE_SCHEMA"); };
            long amount=state==5 ? Math.max(o.refundCents(),r.path("refund_fee").asLong()) : o.refundCents();
            if (amount<0 || amount>o.amountCents()) throw new IllegalStateException("REFUND_AMOUNT_MISMATCH");
            return coreRefund(o,status,amount)
                    .then(save(OrderTransitions.change(o,o.creationState(),o.remoteOrderId(),o.fulfillmentState(),o.shipmentState(),status,amount,
                            merge(o.snapshot(),"refund",store.read(r.toString()))),null));
        });
    }
    private Mono<Void> coreRefund(Order o,String state,long cents) {
        return o.localOrderNo()==null ? Mono.empty() : bridge.shop("refund",Map.of("orderNo",o.localOrderNo(),"channelKey",o.key(),"state",state,"amount",yuan(cents),"revision",o.revision()+1)).then();
    }
    private Mono<Void> save(Order o, String nextJob) {
        return store.saveOrder(o,false).flatMap(ok -> !ok ? Mono.error(new IllegalStateException("ORDER_REVISION_CONFLICT"))
                : nextJob==null ? Mono.empty() : store.enqueue(nextJob+":"+o.key()+":"+(o.revision()+1),o.seller(),nextJob,Map.of("orderKey",o.key())))
                .as(transaction::transactional);
    }
    private Map<String,Object> merge(Map<String,Object> map, String key, Object value) {
        Map<String,Object> next=new LinkedHashMap<>(map); next.put(key,value); return next;
    }
    private JsonNode data(JsonNode result) { return result.has("api_module") ? result.path("api_module") : result.has("module") ? result.path("module") : result.has("data") ? result.path("data") : result; }
    private String yuan(long cents) { return BigDecimal.valueOf(cents,2).toPlainString(); }
}

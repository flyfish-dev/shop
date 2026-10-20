package group.flyfish.dev.xianyu.web;

import group.flyfish.dev.common.bean.Result;
import group.flyfish.dev.xianyu.XianyuProperties;
import group.flyfish.dev.common.bridge.BridgeRequest;
import group.flyfish.dev.common.bridge.XianyuBridgeGuard;
import group.flyfish.dev.xianyu.security.TokenCipher;
import group.flyfish.dev.xianyu.security.XianyuIdentityService;
import group.flyfish.dev.xianyu.store.XianyuModels.*;
import group.flyfish.dev.xianyu.store.XianyuStore;
import group.flyfish.dev.xianyu.top.TopGateway;
import group.flyfish.dev.xianyu.top.TopOperation;
import group.flyfish.dev.xianyu.trade.ListingService;
import group.flyfish.dev.xianyu.trade.TradeService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;
import java.util.Map;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.ArrayList;

@RestController
@RequestMapping("/internal/xianyu/manage")
@RequiredArgsConstructor
public class XianyuManagementController {
    private final XianyuBridgeGuard guard;
    private final XianyuStore store;
    private final XianyuProperties properties;
    private final TokenCipher cipher;
    private final TopGateway top;
    private final ListingService listings;
    private final TradeService trades;
    private final JsonMapper json;
    private final org.springframework.transaction.reactive.TransactionalOperator transaction;
    private final group.flyfish.dev.xianyu.bridge.ApprovedBridgeClient bridge;
    @PostMapping("/{operation}")
    public Mono<Result<Object>> execute(@PathVariable String operation, @RequestBody BridgeRequest request) {
        return guard.accept(request,"/internal/xianyu/manage/"+operation).flatMap(body -> {
            var p=json.readTree(body); String actor=p.path("actor").asText();
            if (!actor.matches("[0-9]+")) return Mono.error(new IllegalArgumentException("管理身份不正确"));
            Mono<?> response=switch(operation) {
                case "status" -> status();
                case "sellers" -> store.sellers().map(s -> Map.of("key",s.key(),"application",s.application(),"expiresAt",s.expiresAt(),"enabled",s.enabled())).collectList();
                case "capabilities" -> store.capabilities().collectList();
                case "listings" -> store.listings().map(this::publicListing).collectList();
                case "orders" -> store.orders().map(this::publicOrder).collectList();
                case "jobs" -> store.jobs().map(j -> Map.of("key",j.key(),"seller",j.seller(),"kind",j.kind(),"status",j.status(),"attempts",j.attempts(),"nextAt",j.nextAt(),"lastError",j.lastError()==null ? "" : j.lastError())).collectList();
                case "messages" -> store.database().sql("SELECT seller_key,topic,status,last_error,created_at FROM xy_message ORDER BY created_at DESC LIMIT 200")
                        .map((r,m)->Map.of("seller",r.get("seller_key",String.class),"topic",r.get("topic",String.class),"status",r.get("status",String.class),
                                "lastError",r.get("last_error",String.class)==null ? "" : r.get("last_error",String.class),"createdAt",r.get("created_at",Long.class))).all().collectList();
                case "save-capability" -> saveCapability(json.treeToValue(p.path("capability"),Capability.class),actor,p.path("verificationEvidence").asText());
                case "authorize-seller" -> authorizeSeller(p.path("key").asText(),p.path("application").asText(),p.path("code").asText(),actor);
                case "message-binding" -> top.call(p.path("seller").asText(),TopOperation.MESSAGE_BINDING_QUERY,Map.of())
                        .map(result->Map.of("bound",result.path("data").asBoolean()));
                case "upload-media" -> uploadMedia(p,actor);
                case "delivery-tasks" -> store.order(p.path("key").asText()).filter(o->o.localOrderNo()!=null)
                        .flatMap(o->bridge.shop("delivery-tasks",Map.of("orderNo",o.localOrderNo(),"channelKey",o.key())));
                case "resolve-delivery" -> store.order(p.path("key").asText()).filter(o->o.localOrderNo()!=null)
                        .flatMap(o->bridge.shop("resolve-delivery",Map.of("orderNo",o.localOrderNo(),"channelKey",o.key(),"actor",actor,
                                "taskKey",p.path("taskKey").asText(),"resolution",p.path("resolution").asText(),"evidence",p.path("evidence").asText())));
                case "save-listing" -> listings.save(json.treeToValue(p.path("listing"),Listing.class),actor).map(this::publicListing);
                case "resolve-listing" -> listings.resolvePublish(p.path("key").asText(),p.path("remoteItemId").asText(),p.path("evidence").asText(),actor).map(this::publicListing);
                case "listing-command" -> listings.schedule(p.path("key").asText(),TopOperation.valueOf(p.path("operation").asText()),actor).thenReturn(true);
                case "order-command" -> orderCommand(p.path("key").asText(),p.path("command").asText(),actor);
                case "refund" -> trades.requestRefund(p.path("key").asText(),p.path("amountCents").asLong(),p.path("requestId").asText(),TopOperation.valueOf(p.path("operation").asText()),actor,
                        p.path("decision").asText("AGREE_REFUND"),p.path("reason").asText()).thenReturn(true);
                case "retry-job" -> retry(p.path("key").asText(),actor);
                default -> Mono.error(new IllegalArgumentException("管理操作不支持"));
            };
            return response.cast(Object.class).map(Result::ok);
        });
    }
    private Mono<Map<String,String>> uploadMedia(tools.jackson.databind.JsonNode p,String actor) {
        byte[] bytes=java.util.Base64.getDecoder().decode(p.path("content").asText());
        String extension;
        if (bytes.length<4 || bytes.length>5*1024*1024) return Mono.error(new IllegalArgumentException("图片大小不符合要求"));
        if (bytes[0]==(byte)0x89 && bytes[1]==0x50 && bytes[2]==0x4e && bytes[3]==0x47) extension="png";
        else if (bytes[0]==(byte)0xff && bytes[1]==(byte)0xd8 && bytes[2]==(byte)0xff) extension="jpg";
        else return Mono.error(new IllegalArgumentException("仅支持PNG或JPEG图片"));
        return top.upload(p.path("seller").asText(),java.util.UUID.randomUUID()+"."+extension,bytes)
                        .flatMap(id -> store.database().sql("INSERT INTO xy_media VALUES (:id,:seller)").bind("id",id).bind("seller",p.path("seller").asText())
                                .fetch().rowsUpdated().then(store.audit(actor,"MEDIA_UPLOAD",p.path("seller").asText())).thenReturn(Map.of("mediaId",id)));
    }
    private Map<String,Object> publicListing(Listing l) {
        var output=new LinkedHashMap<String,Object>();
        output.put("key",l.key());output.put("seller",l.seller());output.put("localItemId",l.localItemId());
        output.put("revision",l.revision());output.put("status",l.status());output.put("nativeEnabled",l.nativeEnabled());output.put("payload",l.payload());
        output.put("remoteItemId",l.remoteItemId());
        Map<?,?> snapshot=l.remoteSnapshot().get("data") instanceof Map<?,?> data ? data : l.remoteSnapshot();
        Map<String,Object> content=new LinkedHashMap<>();
        for(String key:List.of("title","desc","reserve_price","quantity","images","item_sku_list","pv_list"))
            if(snapshot.containsKey(key)) content.put(key,snapshot.get(key));
        output.put("remoteContent",content);
        output.put("linked",l.remoteItemId()!=null && !l.remoteItemId().isBlank());return output;
    }
    private Map<String,Object> publicOrder(Order o) {
        var output=new LinkedHashMap<String,Object>();output.put("key",o.key());output.put("seller",o.seller());
        output.put("localOrderNo",o.localOrderNo());output.put("channel",o.channel());output.put("amountCents",o.amountCents());
        output.put("creationState",o.creationState());output.put("platformState",o.platformState());output.put("refundState",o.refundState());
        output.put("refundCents",o.refundCents());output.put("fulfillmentState",o.fulfillmentState());output.put("shipmentState",o.shipmentState());
        return output;
    }
    private Mono<Map<String,Object>> status() {
        List<String> blockers=new ArrayList<>();
        if (!properties.isTowerDeployment()) blockers.add("聚石塔部署未确认");
        if (!properties.isDataFlowApproved()) blockers.add("数据通道未批准");
        if (!properties.isMiniappEnabled()) blockers.add("小程序接入未启用");
        return store.database().sql("SELECT status,COUNT(*) AS total FROM xy_job GROUP BY status")
                .map((r,m)->Map.entry(r.get("status",String.class),r.get("total",Long.class))).all()
                .collectMap(Map.Entry::getKey,Map.Entry::getValue).map(counts -> Map.of("configured",true,
                        "salesEnabled",properties.isSalesEnabled(),"processingEnabled",properties.isProcessingEnabled(),
                        "miniappEnabled",properties.isMiniappEnabled(),"blockers",blockers,"jobCounts",counts,
                        "operations",java.util.Arrays.stream(TopOperation.values()).map(op -> Map.of("operation",op.name(),"method",op.method,"requestKey",op.requestKey==null ? "" : op.requestKey)).toList()));
    }
    @Transactional
    public Mono<Boolean> saveCapability(Capability c, String actor,String evidence) {
        TopOperation.valueOf(c.operation());
        if (!List.of("commerce","buyer","seller").contains(c.application()) || c.seller()==null || !c.seller().matches("[a-zA-Z0-9-]{1,80}") || c.expiresAt()<=store.now()
                || c.requestKey()!=null && !c.requestKey().matches("[a-z_0-9]{1,120}"))
            return Mono.error(new IllegalArgumentException("权限配置不正确"));
        if(c.verified() && (evidence.length()<12 || evidence.length()>1000)) return Mono.error(new IllegalArgumentException("请记录接口实际调用及权限核验依据"));
        Mono<Void> verification=!c.verified() ? Mono.empty() : store.database()
                .sql("INSERT INTO xy_verification VALUES (:key,:actor,:evidence,:now) ON DUPLICATE KEY UPDATE actor=VALUES(actor),evidence=VALUES(evidence),verified_at=VALUES(verified_at)")
                .bind("key",c.application()+":"+c.seller()+":"+c.operation()).bind("actor",actor).bind("evidence",evidence).bind("now",store.now()).fetch().rowsUpdated().then();
        return store.saveCapability(c).then(verification).then(store.audit(actor,"CAPABILITY_SAVE",c.application()+":"+c.seller()+":"+c.operation())).thenReturn(true)
                .as(transaction::transactional).retryWhen(reactor.util.retry.Retry.backoff(4,java.time.Duration.ofMillis(30))
                        .filter(org.springframework.dao.TransientDataAccessException.class::isInstance));
    }
    private Mono<Boolean> authorizeSeller(String key, String app, String code, String actor) {
        if (!key.matches("[a-zA-Z0-9-]{1,80}") || !List.of("commerce","seller").contains(app) || code.isBlank())
            return Mono.error(new IllegalArgumentException("商家授权参数不正确"));
        return top.authorize(app,code).flatMap(raw -> {
            var token=raw.isTextual() ? json.readTree(raw.asText()) : raw;
            String value=token.path("access_token").asText();long ttl=token.path("expires_in").asLong();
            String uid=token.path("taobao_user_id").asText(token.path("user_id").asText());
            if (value.isBlank() || ttl<=0 || uid.isBlank()) return Mono.error(new IllegalStateException("SELLER_AUTH_SCHEMA"));
            return store.saveSeller(new Seller(key,app,cipher.encrypt(value),store.now()+ttl,true))
                    .then(store.database().sql("DELETE FROM xy_seller_identity WHERE seller_key=:key").bind("key",key).fetch().rowsUpdated())
                    .then(store.database().sql("INSERT INTO xy_seller_identity VALUES (:key,:hash)")
                            .bind("key",key).bind("hash",XianyuIdentityService.hash(uid)).fetch().rowsUpdated())
                    .then(store.audit(actor,"SELLER_AUTHORIZE",key)).thenReturn(true);
        });
    }
    private Mono<Boolean> orderCommand(String key, String command, String actor) {
        return store.order(key).flatMap(o -> {
            Mono<Void> action=switch(command) {
                case "QUERY" -> trades.reconcile(key);
                case "REFUND_QUERY" -> store.enqueue("REFUND_QUERY:"+key+":"+store.now(),o.seller(),"REFUND_QUERY",Map.of("orderKey",key));
                case "DELIVERY_RETRY" -> store.enqueue("FULFILL:"+key+":"+store.now(),o.seller(),"FULFILL",Map.of("orderKey",key));
                case "SHIP_RETRY" -> store.enqueue("SHIP:"+key+":"+store.now(),o.seller(),"SHIP",Map.of("orderKey",key));
                case "CLOSE" -> store.enqueue("ORDER_CLOSE:"+key+":"+store.now(),o.seller(),"ORDER_CLOSE",Map.of("orderKey",key));
                case "PREPARE" -> store.enqueue("PREPARE:"+key+":"+store.now(),o.seller(),"PREPARE",Map.of("orderKey",key));
                default -> Mono.error(new IllegalArgumentException("订单操作不支持"));
            };
            return action.then(store.audit(actor,command,key)).thenReturn(true);
        });
    }
    private Mono<Boolean> retry(String key, String actor) {
        return store.database().sql("UPDATE xy_job SET status='READY',attempts=0,next_at=:now WHERE job_key=:key AND status='FAILED'")
                .bind("key",key).bind("now",store.now()).fetch().rowsUpdated().flatMap(n -> n==1
                        ? store.audit(actor,"JOB_RETRY",key).thenReturn(true)
                        : Mono.error(new IllegalArgumentException("任务需先核查远端结果")));
    }
}

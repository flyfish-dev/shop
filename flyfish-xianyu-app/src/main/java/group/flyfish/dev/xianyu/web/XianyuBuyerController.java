package group.flyfish.dev.xianyu.web;

import group.flyfish.dev.common.bean.Result;
import group.flyfish.dev.xianyu.bridge.ApprovedBridgeClient;
import group.flyfish.dev.xianyu.security.XianyuIdentityService;
import group.flyfish.dev.xianyu.store.XianyuModels.Identity;
import group.flyfish.dev.xianyu.trade.TradeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class XianyuBuyerController {
    private final XianyuIdentityService identities;
    private final ApprovedBridgeClient bridge;
    private final TradeService trades;
    private final group.flyfish.dev.xianyu.store.XianyuStore store;
    private final group.flyfish.dev.xianyu.top.TopGateway top;
    private final group.flyfish.dev.xianyu.XianyuProperties properties;
    private final group.flyfish.dev.xianyu.trade.ChannelCatalogService catalog;
    private final tools.jackson.databind.json.JsonMapper json;

    @PostMapping("/xianyu/native/claim")
    public Mono<Result<Map<String,String>>> nativeClaim(@RequestHeader("Authorization") String authorization,@RequestBody Map<String,String> input) {
        String remote=input.get("orderId"),seller=properties.getNativeSeller();
        if (remote==null || !remote.matches("[0-9]+") || seller==null || seller.isBlank()) return Mono.error(new IllegalArgumentException("领取入口暂未开放"));
        return identities.authenticate(authorization).flatMap(i -> top.call(seller,group.flyfish.dev.xianyu.top.TopOperation.ORDER_QUERY,Map.of("biz_order_id",remote))
                .flatMap(result -> {
                    JsonNode data=result.has("module") ? result.path("module") : result.path("data");
                    String buyer=data.path("encryption_buyer_id").asText();
                    if (buyer.isBlank()) return Mono.error(new IllegalArgumentException("订单身份尚未验证"));
                    return identities.subjectForPlatformId(buyer).filter(subject -> subject.equals(i.subject()))
                            .switchIfEmpty(Mono.error(new IllegalArgumentException("订单不存在或不属于当前账号")))
                            .then(trades.importNative(seller,remote))
                            .then(trades.claimNative(XianyuIdentityService.hash(seller+":"+remote),i));
                })).map(o -> Result.ok(Map.of("orderNo",o.localOrderNo())));
    }

    @PostMapping("/xianyu/account/{action}")
    public Mono<Result<JsonNode>> link(@PathVariable String action,@RequestHeader("Authorization") String authorization,@RequestBody Map<String,Object> request) {
        if (!java.util.Set.of("link","link-confirm").contains(action)) return Mono.error(new IllegalArgumentException("关联操作不支持"));
        return identities.authenticate(authorization).flatMap(i -> store.database()
                .sql("SELECT COUNT(*) AS total FROM xy_order WHERE buyer_subject=:subject AND local_order_no IS NOT NULL")
                .bind("subject",i.subject()).map((r,m)->r.get("total",Long.class)).one().flatMap(count -> {
                    if(count>0) return Mono.error(new IllegalArgumentException("已有渠道订单，请通过工单申请账号关联"));
                    Map<String,Object> payload=new java.util.LinkedHashMap<>(request);payload.put("subject",i.subject());
                    return bridge.auth(action,payload).flatMap(response -> response.has("token")
                            ? identities.savePortalToken(i.subject(),response.path("token").asText()).thenReturn(response) : Mono.just(response));
                })).map(Result::ok);
    }
    @PostMapping("/xianyu/auth/authorize")
    public Mono<Result<Map<String,String>>> authorize(@RequestBody Map<String,String> request) {
        return identities.authorize(request.get("code")).map(ticket -> Result.ok(Map.of("ticket",ticket)));
    }
    @PostMapping("/xianyu/auth/exchange")
    public Mono<Result<Map<String,Object>>> exchange(@RequestBody Map<String,String> request) {
        return identities.exchange(request.get("ticket")).map(Result::ok);
    }
    @PostMapping("/portal/users/logout")
    public Mono<Result<Boolean>> logout(@RequestHeader("Authorization") String authorization) {
        return identities.authenticate(authorization).then(identities.logout(authorization)).thenReturn(Result.ok(true));
    }
    @GetMapping("/portal/users/current")
    public Mono<Result<JsonNode>> current(@RequestHeader("Authorization") String authorization) {
        return identities.authenticate(authorization).flatMap(i -> bridge.auth("current",Map.of("token",identities.portalToken(i)))).map(Result::ok);
    }
    @GetMapping("/shops/payment-methods")
    public Result<java.util.List<String>> paymentMethods() { return Result.ok(java.util.List.of("xianyu")); }
    @PostMapping({"/shops/orders","/shops/payments"})
    public Mono<Result<Map<String,Object>>> order(@RequestHeader("Authorization") String authorization,
                                                 @RequestHeader("Idempotency-Key") String idempotency,
                                                 @RequestBody Map<String,Object> request) {
        return identities.authenticate(authorization).flatMap(i -> trades.prepare(i,request,idempotency)).map(Result::ok);
    }
    @GetMapping("/xianyu/orders/{no}/payment")
    public Mono<Result<Map<String,Object>>> payment(@PathVariable String no, @RequestHeader("Authorization") String authorization) {
        return identities.authenticate(authorization).flatMap(i -> trades.owned(no,i)).map(o -> Result.ok(trades.payment(o)));
    }
    @PostMapping("/xianyu/orders/{key}/claim")
    public Mono<Result<Map<String,String>>> claim(@PathVariable String key,@RequestHeader("Authorization") String authorization) {
        return identities.authenticate(authorization).flatMap(i -> trades.claimNative(key,i))
                .map(o -> Result.ok(Map.of("orderNo",o.localOrderNo())));
    }
    @GetMapping({"/shops/current","/shops/pricing","/shops/item-groups","/shops/items","/shops/items/{id}",
            "/shops/items/{id}/contracts","/shops/items/{id}/purchase-availability","/shops/items/{id}/license-renewal",
            "/shops/orders","/shops/orders/mine","/shops/orders/{no}","/portal/tickets","/portal/tickets/{ticketNo}"})
    public Mono<JsonNode> get(@RequestHeader(value="Authorization",required=false) String authorization,
                             org.springframework.web.server.ServerWebExchange exchange) {
        String path=exchange.getRequest().getPath().value();
        boolean publicRead=path.matches("/shops/(current|pricing|item-groups|items|items/[0-9]+(?:/contracts)?)");
        Mono<String> token=publicRead ? Mono.just("") : identities.authenticate(authorization).map(identities::portalToken);
        Map<String,Object> query=new java.util.LinkedHashMap<>(); exchange.getRequest().getQueryParams().forEach((k,v)->query.put(k,v.getFirst()));
        if (path.equals("/shops/items")) return catalog.catalog(query);
        if (path.matches("/shops/items/[0-9]+")) return token.flatMap(t -> catalog.detail(path.substring(path.lastIndexOf('/')+1),query,t));
        Mono<Void> selected=path.matches("/shops/items/[0-9]+/.*")
                ? catalog.requireSelection(path.split("/")[3],query.get("skuId")) : Mono.empty();
        return selected.then(token).flatMap(t -> bridge.shop("request",Map.of("path",path,"method","GET","query",query,"token",t)))
                .flatMap(result -> {
                    if (path.equals("/shops/orders") || path.equals("/shops/orders/mine")) return identities.authenticate(authorization)
                            .flatMap(i->store.database().sql("SELECT local_order_no FROM xy_order WHERE buyer_subject=:subject AND local_order_no IS NOT NULL")
                                    .bind("subject",i.subject()).map((r,m)->r.get("local_order_no",String.class)).all().collectList())
                            .map(ids->{
                                var copy=(tools.jackson.databind.node.ObjectNode)result.deepCopy();
                                var orders=json.createArrayNode();
                                for(JsonNode order:result.path("result")) if(ids.contains(order.path("orderNo").asText())) orders.add(order);
                                copy.set("result",orders);return (JsonNode)copy;
                            });
                    if (!path.startsWith("/shops/orders/") || path.endsWith("/mine")) return Mono.just(result);
                    return identities.authenticate(authorization).flatMap(i -> trades.owned(path.substring(path.lastIndexOf('/')+1),i))
                            .flatMap(o -> trades.reconcile(o.key()).onErrorResume(e -> Mono.empty()).thenReturn(result));
                });
    }
    @PostMapping({"/shops/coupons/apply","/shops/items/{id}/contracts/signatures","/shops/orders/{no}/delivery/extract","/portal/tickets","/portal/tickets/{ticketNo}/messages"})
    public Mono<JsonNode> post(@RequestHeader("Authorization") String authorization, @RequestBody Map<String,Object> body,
                              org.springframework.web.server.ServerWebExchange exchange) {
        Map<String,Object> query=new java.util.LinkedHashMap<>(); exchange.getRequest().getQueryParams().forEach((k,v)->query.put(k,v.getFirst()));
        String path=exchange.getRequest().getPath().value();
        Mono<Void> selected=path.matches("/shops/items/[0-9]+/.*")
                ? catalog.requireSelection(path.split("/")[3],query.get("skuId")) : path.equals("/shops/coupons/apply")
                ? catalog.requireSelection(String.valueOf(body.get("itemId")),body.get("skuId")) : Mono.empty();
        return selected.then(identities.authenticate(authorization)).flatMap(i -> bridge.shop("request",
                Map.of("path",exchange.getRequest().getPath().value(),"method","POST","query",query,"body",body,"token",identities.portalToken(i))));
    }
}

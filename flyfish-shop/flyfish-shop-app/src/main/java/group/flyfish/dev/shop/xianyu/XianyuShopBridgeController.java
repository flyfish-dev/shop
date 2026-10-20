package group.flyfish.dev.shop.xianyu;

import group.flyfish.dev.auth.api.client.AuthUserGateway;
import group.flyfish.dev.auth.api.user.UserAuthorizationUtils;
import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.shop.domain.dto.ShopOrderDto;
import group.flyfish.dev.shop.service.ShopOrderService;
import group.flyfish.dev.shop.repository.ShopOrderRepository;
import group.flyfish.dev.common.bridge.BridgeRequest;
import group.flyfish.dev.xianyu.api.SalesChannel;
import group.flyfish.dev.common.bridge.XianyuBridgeGuard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/internal/xianyu/shop")
public class XianyuShopBridgeController {
    private final XianyuBridgeGuard guard;
    private final ShopOrderService orders;
    private final ShopOrderRepository repository;
    private final AuthUserGateway users;
    private final DatabaseClient db;
    private final JsonMapper json;
    private final WebClient self;
    private final DeliveryTaskLedger ledger;
    public XianyuShopBridgeController(XianyuBridgeGuard guard, ShopOrderService orders, ShopOrderRepository repository,
                                     AuthUserGateway users, DatabaseClient db, JsonMapper json, DeliveryTaskLedger ledger,
                                     WebClient.Builder builder, @Value("${server.port:10082}") int port) {
        this.guard=guard; this.orders=orders; this.repository=repository; this.users=users; this.db=db; this.json=json;this.ledger=ledger;
        this.self=builder.baseUrl("http://127.0.0.1:" + port).build();
    }
    @PostMapping("/{operation}")
    public Mono<Object> execute(@PathVariable String operation, @RequestBody BridgeRequest request) {
        return guard.accept(request, "/internal/xianyu/shop/" + operation).flatMap(body -> {
            JsonNode input = json.readTree(body);
            String no = input.path("orderNo").asText();
            return switch (operation) {
                case "prepare" -> users.authenticate(List.of(input.path("token").asText()))
                        .switchIfEmpty(Mono.error(new BusinessException("USER_REQUIRED", "请先登录")))
                        .flatMap(user -> {
                            UserAuthorizationUtils.requireLogin(user);
                            ShopOrderDto dto = json.treeToValue(input.path("order"), ShopOrderDto.class);
                            dto.setPaymentProvider("xianyu"); dto.setPaymentCurrency("CNY");
                            dto.setChannelOrderKey(input.path("channelKey").asText());
                            SalesChannel channel = SalesChannel.valueOf(input.path("channel").asText());
                            if (channel == SalesChannel.WEBSITE) throw new BusinessException("CHANNEL_INVALID", "订单渠道不正确");
                            return orders.createOrder(dto, user).contextWrite(c -> {
                                var next=c.put(XianyuBridgeGuard.CHANNEL_CONTEXT,channel);
                                if (channel==SalesChannel.XIANYU_NATIVE) {
                                    BigDecimal amount=new BigDecimal(input.path("amount").asText());
                                    if (amount.signum()<=0 || amount.scale()>2) throw new BusinessException("CHANNEL_AMOUNT_INVALID","渠道金额不正确");
                                    next=next.put("flyfish.xianyu.nativeAmount",amount);
                                }
                                return next;
                            });
                        }).cast(Object.class);
                case "eligible" -> orders.xianyuNativeEligible(input.path("itemId").asLong(),input.path("skuId").asText(null)).cast(Object.class);
                case "paid" -> orders.confirmXianyuPayment(no, new BigDecimal(input.path("amount").asText()),
                        input.path("channelKey").asText()).cast(Object.class);
                case "fulfill" -> orders.fulfillXianyuOrder(no).cast(Object.class);
                case "delivery-tasks" -> repository.findByChannelOrderKey(input.path("channelKey").asText())
                        .filter(o->no.equals(o.getOrderNo())).flatMap(o->ledger.tasks(no).collectList()).cast(Object.class);
                case "resolve-delivery" -> repository.findByChannelOrderKey(input.path("channelKey").asText())
                        .filter(o->no.equals(o.getOrderNo())).flatMap(o->ledger.resolve(no,input.path("taskKey").asText(),input.path("actor").asText(),
                                input.path("resolution").asText(),input.path("evidence").asText())).cast(Object.class);
                case "refund" -> repository.projectXianyuRefund(no,input.path("channelKey").asText(),
                        refundState(input.path("state").asText()),new BigDecimal(input.path("amount").asText()),input.path("revision").asLong()).cast(Object.class);
                case "close" -> db.sql("UPDATE shop_order SET status='CLOSED',delivery_status='SKIPPED' WHERE order_no=:no AND payment_provider='xianyu' AND channel_order_key=:key AND paid_time IS NULL")
                        .bind("no", no).bind("key", input.path("channelKey").asText()).fetch().rowsUpdated().cast(Object.class);
                case "request" -> forward(input).cast(Object.class);
                default -> Mono.error(new BusinessException("XIANYU_OPERATION_INVALID", "渠道操作不支持"));
            };
        });
    }
    private String refundState(String state) {
        if (!List.of("NONE","PENDING","PARTIAL","REFUNDED","CLOSED","REJECTED","PROCESSING").contains(state))
            throw new BusinessException("XIANYU_REFUND_INVALID", "退款状态不正确");
        return state;
    }
    private Mono<JsonNode> forward(JsonNode input) {
        String path = input.path("path").asText();
        String method = input.path("method").asText("GET");
        boolean allowed = "GET".equals(method) && path.matches("/shops/(current|pricing|item-groups|items|items/[0-9]+(?:/(?:contracts|license-renewal|purchase-availability))?|orders|orders/mine|orders/[a-zA-Z0-9-]+)")
                || "POST".equals(method) && path.matches("/shops/(?:coupons/apply|items/[0-9]+/contracts/signatures|orders/[a-zA-Z0-9-]+/delivery/extract)");
        allowed=allowed || "GET".equals(method) && path.matches("/portal/tickets(?:/(?!managements)[a-zA-Z0-9-]+)?")
                || "POST".equals(method) && path.matches("/portal/tickets(?:/(?!managements)[a-zA-Z0-9-]+/messages)?");
        if (!allowed) return Mono.error(new BusinessException("XIANYU_PATH_DENIED", "渠道请求不支持"));
        var request = self.method(org.springframework.http.HttpMethod.valueOf(method)).uri(builder -> {
            builder.path(path);
            input.path("query").properties().forEach(e -> builder.queryParam(e.getKey(), e.getValue().asText()));
            return builder.build();
        }).headers(headers -> headers.setBearerAuth(input.path("token").asText()));
        return ("POST".equals(method) ? request.bodyValue(input.path("body")) : request)
                .retrieve().bodyToMono(JsonNode.class);
    }
}

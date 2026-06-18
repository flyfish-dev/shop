package group.flyfish.dev.shop.service.impl;

import group.flyfish.dev.shop.domain.po.ShopItem;
import group.flyfish.dev.shop.domain.po.ShopOrder;
import group.flyfish.dev.shop.service.DeliveryResult;
import group.flyfish.dev.shop.service.ShopDeliveryHandler;
import group.flyfish.dev.shop.service.ShopDeliveryService;
import group.flyfish.dev.auth.api.user.PortalUserVo;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 商品交付分发器。
 */
public class CompositeShopDeliveryService implements ShopDeliveryService {

    private final List<ShopDeliveryHandler> handlers;

    public CompositeShopDeliveryService(List<ShopDeliveryHandler> handlers) {
        this.handlers = handlers == null ? List.of() : List.copyOf(handlers);
    }

    @Override
    public Mono<DeliveryResult> deliver(ShopOrder order, ShopItem item, PortalUserVo buyer) {
        List<ShopDeliveryHandler> matchedHandlers = handlers.stream()
                .filter(handler -> handler.supports(item))
                .toList();
        if (matchedHandlers.isEmpty()) {
            return Mono.just(DeliveryResult.failed("该商品类型未实现自动交付"));
        }
        return Flux.fromIterable(matchedHandlers)
                .concatMap(handler -> handler.deliver(order, item, buyer)
                        .onErrorResume(e -> Mono.just(DeliveryResult.failed(e.getMessage()))))
                .collectList()
                .map(this::mergeResults);
    }

    private DeliveryResult mergeResults(List<DeliveryResult> results) {
        List<String> failures = results.stream()
                .filter(result -> !result.isSuccess())
                .map(DeliveryResult::getMessage)
                .filter(message -> message != null && !message.isBlank())
                .toList();
        if (!failures.isEmpty()) {
            return DeliveryResult.failed("自动交付失败：" + String.join("；", failures));
        }
        String message = results.stream()
                .map(DeliveryResult::getMessage)
                .filter(value -> value != null && !value.isBlank())
                .reduce((left, right) -> left + "；" + right)
                .orElse("自动交付完成");
        return DeliveryResult.ok(message);
    }
}

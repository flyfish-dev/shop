package group.flyfish.dev.shop.service.impl;

import group.flyfish.dev.shop.domain.po.ShopItem;
import group.flyfish.dev.shop.domain.po.ShopOrder;
import group.flyfish.dev.shop.service.DeliveryResult;
import group.flyfish.dev.shop.service.ShopDeliveryHandler;
import group.flyfish.dev.shop.service.ShopDeliveryService;
import group.flyfish.dev.user.domain.vo.PortalUserVo;
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
        return handlers.stream()
                .filter(handler -> handler.supports(item))
                .findFirst()
                .map(handler -> handler.deliver(order, item, buyer))
                .orElseGet(() -> Mono.just(DeliveryResult.failed("该商品类型未实现自动交付")));
    }
}

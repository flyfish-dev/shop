package group.flyfish.dev.shop.service;

import group.flyfish.dev.shop.domain.po.ShopItem;
import group.flyfish.dev.shop.domain.po.ShopOrder;
import group.flyfish.dev.auth.api.user.PortalUserVo;
import reactor.core.publisher.Mono;

public interface ShopDeliveryService {

    Mono<DeliveryResult> deliver(ShopOrder order, ShopItem item, PortalUserVo buyer);
}

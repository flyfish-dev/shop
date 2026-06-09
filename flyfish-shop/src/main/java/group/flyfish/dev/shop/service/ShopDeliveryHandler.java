package group.flyfish.dev.shop.service;

import group.flyfish.dev.shop.domain.po.ShopItem;
import group.flyfish.dev.shop.domain.po.ShopOrder;
import group.flyfish.dev.user.domain.vo.PortalUserVo;
import reactor.core.publisher.Mono;

/**
 * 商品交付策略。
 * <p>每个实现只处理自己支持的商品类型，组合交付器负责选择策略，避免订单服务了解每种交付细节。</p>
 */
public interface ShopDeliveryHandler {

    boolean supports(ShopItem item);

    Mono<DeliveryResult> deliver(ShopOrder order, ShopItem item, PortalUserVo buyer);
}

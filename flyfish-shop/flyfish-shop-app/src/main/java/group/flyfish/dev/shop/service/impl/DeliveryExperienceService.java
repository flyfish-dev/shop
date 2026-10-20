package group.flyfish.dev.shop.service.impl;

import group.flyfish.dev.auth.api.user.PortalUserVo;
import group.flyfish.dev.common.exception.ServiceException;
import group.flyfish.dev.common.json.JacksonUtils;
import group.flyfish.dev.shop.converter.ShopItemParamValue;
import group.flyfish.dev.shop.converter.impl.DeliveryExperienceParamValue;
import group.flyfish.dev.shop.domain.po.ShopItem;
import group.flyfish.dev.shop.domain.po.ShopOrder;
import group.flyfish.dev.shop.domain.po.ShopOrderDelivery;
import group.flyfish.dev.shop.repository.ShopOrderDeliveryRepository;
import group.flyfish.dev.shop.service.DeliveryResult;
import group.flyfish.dev.shop.service.ShopDeliveryHandler;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 将商品配置的运行体验入口固化为订单交付快照。
 */
@Service
@RequiredArgsConstructor
public class DeliveryExperienceService implements ShopDeliveryHandler {

    @Override
    public boolean recoverySafe() { return true; }

    private static final String ACTOR = "auto-delivery";

    private final ShopOrderDeliveryRepository deliveryRepository;

    @Override
    public boolean supports(ShopItem item) {
        return item != null && ShopItemParamValue.deliveryExperienceEnabled(item.getParams());
    }

    @Override
    public Stage stage() {
        return Stage.AFTER_SUCCESS;
    }

    @Override
    public Mono<DeliveryResult> deliver(ShopOrder order, ShopItem item, PortalUserVo buyer) {
        DeliveryExperienceParamValue param = ShopItemParamValue.deliveryExperience(item.getParams());
        if (!param.hasLinks()) {
            return Mono.just(DeliveryResult.failed("商品未配置有效的交付体验入口"));
        }
        return upsertDelivery(order, item, param)
                .thenReturn(DeliveryResult.ok("运行体验指引已生成，可在我的订单中查看"));
    }

    private Mono<ShopOrderDelivery> upsertDelivery(ShopOrder order, ShopItem item,
                                                    DeliveryExperienceParamValue param) {
        return deliveryRepository.findByOrderNoAndDeliveryType(order.getOrderNo(),
                        ShopOrderDelivery.DeliveryType.EXPERIENCE.name())
                .defaultIfEmpty(new ShopOrderDelivery())
                .map(delivery -> {
                    applyDelivery(delivery, order, item, param);
                    return delivery;
                })
                .flatMap(deliveryRepository::save)
                .onErrorMap(e -> new ServiceException("交付体验快照保存失败", e));
    }

    private void applyDelivery(ShopOrderDelivery delivery, ShopOrder order, ShopItem item,
                               DeliveryExperienceParamValue param) {
        if (delivery.getId() == null) {
            delivery.setCreateBy(ACTOR);
        }
        delivery.setUpdateBy(ACTOR);
        delivery.setOrderNo(order.getOrderNo());
        delivery.setItemId(item.getId());
        delivery.setBuyerId(order.getBuyerId());
        delivery.setDeliveryType(ShopOrderDelivery.DeliveryType.EXPERIENCE.name());
        delivery.setTitle(StringUtils.left(param.getTitle(), 128));
        delivery.setContent(null);
        delivery.setAttachments(null);
        delivery.setLicenseNo(null);
        delivery.setMetadata(JacksonUtils.toJson(param));
    }
}

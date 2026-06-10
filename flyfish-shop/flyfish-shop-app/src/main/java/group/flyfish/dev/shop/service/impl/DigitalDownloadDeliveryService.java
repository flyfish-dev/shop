package group.flyfish.dev.shop.service.impl;

import group.flyfish.dev.common.exception.ServiceException;
import group.flyfish.dev.common.json.JacksonUtils;
import group.flyfish.dev.common.upload.domain.vo.FileAttachmentVo;
import group.flyfish.dev.shop.converter.ShopItemParamValue;
import group.flyfish.dev.shop.converter.impl.DigitalDeliveryParamValue;
import group.flyfish.dev.shop.domain.po.ShopItem;
import group.flyfish.dev.shop.domain.po.ShopOrder;
import group.flyfish.dev.shop.domain.po.ShopOrderDelivery;
import group.flyfish.dev.shop.repository.ShopOrderDeliveryRepository;
import group.flyfish.dev.shop.service.DeliveryResult;
import group.flyfish.dev.shop.service.ShopDeliveryHandler;
import group.flyfish.dev.auth.api.user.PortalUserVo;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DigitalDownloadDeliveryService implements ShopDeliveryHandler {

    private static final String ACTOR = "auto-delivery";

    private final ShopOrderDeliveryRepository deliveryRepository;

    @Override
    public boolean supports(ShopItem item) {
        return item != null && item.getType() == ShopItem.Type.DIGITAL_DOWNLOAD;
    }

    @Override
    public Mono<DeliveryResult> deliver(ShopOrder order, ShopItem item, PortalUserVo buyer) {
        DigitalDeliveryParamValue param = ShopItemParamValue.digitalDelivery(item.getParams());
        if (!param.hasDeliveryContent()) {
            return Mono.just(DeliveryResult.failed("数字商品未配置提货内容"));
        }
        return upsertDelivery(order, item, param)
                .thenReturn(DeliveryResult.ok("数字商品已交付，可在我的订单中提取"));
    }

    private Mono<ShopOrderDelivery> upsertDelivery(ShopOrder order, ShopItem item,
                                                   DigitalDeliveryParamValue param) {
        return deliveryRepository.findByOrderNo(order.getOrderNo())
                .defaultIfEmpty(new ShopOrderDelivery())
                .map(delivery -> {
                    applyDelivery(delivery, order, item, param);
                    return delivery;
                })
                .flatMap(deliveryRepository::save)
                .onErrorMap(e -> new ServiceException("数字商品交付快照保存失败", e));
    }

    private void applyDelivery(ShopOrderDelivery delivery, ShopOrder order, ShopItem item,
                               DigitalDeliveryParamValue param) {
        if (delivery.getId() == null) {
            delivery.setCreateBy(ACTOR);
        }
        delivery.setUpdateBy(ACTOR);
        delivery.setOrderNo(order.getOrderNo());
        delivery.setItemId(item.getId());
        delivery.setBuyerId(order.getBuyerId());
        delivery.setDeliveryType(ShopOrderDelivery.DeliveryType.DIGITAL.name());
        delivery.setTitle(StringUtils.left(param.getTitle(), 128));
        delivery.setContent(param.getContent());
        delivery.setAttachments(attachmentsJson(param.getAttachments()));
    }

    private String attachmentsJson(List<FileAttachmentVo> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return null;
        }
        return JacksonUtils.toJson(attachments);
    }
}

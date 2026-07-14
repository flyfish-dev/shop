package group.flyfish.dev.shop.domain.dto;

import group.flyfish.dev.common.upload.domain.vo.FileAttachmentVo;
import group.flyfish.dev.shop.domain.po.ShopOrder;
import group.flyfish.dev.shop.domain.po.ShopOrderDelivery;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 管理员处理订单交付结果。
 */
@Data
public class ShopOrderDeliveryDto {

    @NotNull(message = "交付状态不能为空")
    private ShopOrder.DeliveryStatus deliveryStatus;

    private String deliveryMessage;

    /**
     * 补充交付资源类型。默认写入数字内容快照，适合后续给已交付订单补专属下载包。
     */
    private ShopOrderDelivery.DeliveryType deliveryType = ShopOrderDelivery.DeliveryType.DIGITAL;

    /**
     * 补充交付资源标题。
     */
    private String deliveryTitle;

    /**
     * 补充交付正文。
     */
    private String deliveryContent;

    /**
     * 补充交付附件。
     */
    private List<FileAttachmentVo> deliveryAttachments;
}

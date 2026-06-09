package group.flyfish.dev.shop.domain.vo;

import group.flyfish.dev.common.json.JacksonUtils;
import group.flyfish.dev.common.upload.domain.vo.FileAttachmentVo;
import group.flyfish.dev.shop.domain.po.ShopOrderDelivery;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import tools.jackson.core.type.TypeReference;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ShopOrderDeliveryExtractVo {

    private String orderNo;

    private String deliveryType;

    private String title;

    private String content;

    private List<FileAttachmentVo> attachments;

    private String licenseNo;

    private LocalDateTime extractedTime;

    public ShopOrderDeliveryExtractVo(ShopOrderDelivery delivery) {
        this.orderNo = delivery.getOrderNo();
        this.deliveryType = delivery.getDeliveryType();
        this.title = delivery.getTitle();
        this.content = delivery.getContent();
        this.attachments = parseAttachments(delivery.getAttachments());
        this.licenseNo = delivery.getLicenseNo();
        this.extractedTime = delivery.getExtractedTime();
    }

    private List<FileAttachmentVo> parseAttachments(String value) {
        if (StringUtils.isBlank(value)) {
            return List.of();
        }
        try {
            return JacksonUtils.readValue(value, new TypeReference<>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }
}

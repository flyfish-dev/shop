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

    private static final String LICENSE_SECURITY_MESSAGE =
            "授权文件已生成。为降低泄露风险，请通过下方文件下载并妥善保存，不在页面直接展示授权正文。";

    private String orderNo;

    private String deliveryType;

    private String title;

    private String content;

    private List<FileAttachmentVo> attachments;

    private List<ShopOrderDeliveryFileVo> files;

    private Boolean sensitive;

    private String securityMessage;

    private String licenseNo;

    private LocalDateTime extractedTime;

    public ShopOrderDeliveryExtractVo(ShopOrderDelivery delivery) {
        this(delivery, List.of());
    }

    public ShopOrderDeliveryExtractVo(ShopOrderDelivery delivery, List<ShopOrderDeliveryFileVo> files) {
        this.orderNo = delivery.getOrderNo();
        this.deliveryType = delivery.getDeliveryType();
        this.title = delivery.getTitle();
        this.sensitive = isLicenseDelivery(delivery);
        this.securityMessage = Boolean.TRUE.equals(this.sensitive) ? LICENSE_SECURITY_MESSAGE : null;
        this.content = Boolean.TRUE.equals(this.sensitive) ? LICENSE_SECURITY_MESSAGE : delivery.getContent();
        this.attachments = parseAttachments(delivery.getAttachments());
        this.files = files == null ? List.of() : files;
        this.licenseNo = delivery.getLicenseNo();
        this.extractedTime = delivery.getExtractedTime();
    }

    private boolean isLicenseDelivery(ShopOrderDelivery delivery) {
        return delivery != null
                && StringUtils.equalsIgnoreCase(ShopOrderDelivery.DeliveryType.LICENSE.name(), delivery.getDeliveryType());
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

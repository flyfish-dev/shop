package group.flyfish.dev.shop.domain.vo;

import group.flyfish.dev.common.json.JacksonUtils;
import group.flyfish.dev.common.upload.domain.vo.FileAttachmentVo;
import group.flyfish.dev.shop.domain.po.ShopOrderDelivery;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import tools.jackson.core.type.TypeReference;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

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

    private List<ShopOrderDeliveryExtractVo> deliveries;

    public ShopOrderDeliveryExtractVo() {
    }

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

    public static ShopOrderDeliveryExtractVo combine(String orderNo, List<ShopOrderDeliveryExtractVo> items) {
        List<ShopOrderDeliveryExtractVo> deliveries = items == null ? List.of() : items.stream()
                .filter(Objects::nonNull)
                .toList();
        if (deliveries.isEmpty()) {
            return null;
        }
        ShopOrderDeliveryExtractVo first = deliveries.get(0);
        ShopOrderDeliveryExtractVo vo = new ShopOrderDeliveryExtractVo();
        vo.orderNo = StringUtils.defaultIfBlank(orderNo, first.getOrderNo());
        vo.deliveryType = deliveries.size() == 1 ? first.getDeliveryType() : "MIXED";
        vo.title = deliveries.size() == 1 ? first.getTitle() : "交付内容";
        vo.attachments = deliveries.stream()
                .flatMap(delivery -> delivery.getAttachments() == null
                        ? java.util.stream.Stream.<FileAttachmentVo>empty()
                        : delivery.getAttachments().stream())
                .filter(Objects::nonNull)
                .toList();
        vo.files = deliveries.stream()
                .flatMap(delivery -> delivery.getFiles() == null
                        ? java.util.stream.Stream.<ShopOrderDeliveryFileVo>empty()
                        : delivery.getFiles().stream())
                .filter(Objects::nonNull)
                .toList();
        vo.sensitive = deliveries.stream().anyMatch(delivery -> Boolean.TRUE.equals(delivery.getSensitive()));
        vo.securityMessage = Boolean.TRUE.equals(vo.sensitive) ? LICENSE_SECURITY_MESSAGE : null;
        vo.content = combineContent(deliveries, vo.sensitive);
        vo.licenseNo = deliveries.stream()
                .map(ShopOrderDeliveryExtractVo::getLicenseNo)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .reduce((left, right) -> left + "、" + right)
                .orElse(null);
        vo.extractedTime = deliveries.stream()
                .map(ShopOrderDeliveryExtractVo::getExtractedTime)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
        vo.deliveries = deliveries;
        return vo;
    }

    private static String combineContent(List<ShopOrderDeliveryExtractVo> deliveries, boolean hasSensitiveDelivery) {
        String content = deliveries.stream()
                .filter(delivery -> !Boolean.TRUE.equals(delivery.getSensitive()))
                .map(ShopOrderDeliveryExtractVo::getContent)
                .filter(StringUtils::isNotBlank)
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse(null);
        if (StringUtils.isNotBlank(content)) {
            return content;
        }
        return hasSensitiveDelivery ? LICENSE_SECURITY_MESSAGE : null;
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

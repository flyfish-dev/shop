package group.flyfish.dev.shop.converter.impl;

import group.flyfish.dev.common.exception.ServiceException;
import group.flyfish.dev.shop.converter.ShopItemParamValue;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 支付成功后的使用体验指引配置。
 *
 * <p>该配置属于商品交付参数。订单完成自动交付时会写入独立快照，确保商品后续修改不会改变
 * 历史订单中已经承诺给客户的入口和说明。</p>
 */
@Data
public class DeliveryExperienceParamValue implements ShopItemParamValue {

    public static final String PARAM_KEY = "deliveryExperience";

    private static final int MAX_LINKS = 8;
    private static final int MAX_TITLE_LENGTH = 128;
    private static final int MAX_DESCRIPTION_LENGTH = 512;
    private static final int MAX_URL_LENGTH = 512;
    private static final List<String> LINK_KINDS = List.of(
            "REPOSITORY", "DEMO", "DOCUMENTATION", "SUPPORT", "DOWNLOAD", "LINK"
    );

    /** 是否启用交付后的体验指引。 */
    private boolean enabled;

    /** 中文标题。 */
    private String title;

    /** 英文标题。 */
    private String titleEn;

    /** 中文说明。 */
    private String description;

    /** 英文说明。 */
    private String descriptionEn;

    /** 可访问的体验入口。 */
    private List<Link> links = new ArrayList<>();

    /**
     * 标准化并校验配置。所有入口必须使用 HTTPS，避免订单页暴露不安全协议或脚本链接。
     */
    public void normalize() {
        title = normalizeText(title, "开始运行与验证", MAX_TITLE_LENGTH, "体验指引标题");
        titleEn = normalizeText(titleEn, "Run and Validate", MAX_TITLE_LENGTH, "英文体验指引标题");
        description = normalizeOptionalText(description, MAX_DESCRIPTION_LENGTH, "体验指引说明");
        descriptionEn = normalizeOptionalText(descriptionEn, MAX_DESCRIPTION_LENGTH, "英文体验指引说明");
        if (!enabled) {
            links = List.of();
            return;
        }
        if (links == null || links.isEmpty()) {
            throw new ServiceException("启用交付体验后至少需要配置一个入口");
        }
        if (links.size() > MAX_LINKS) {
            throw new ServiceException("交付体验入口不能超过 " + MAX_LINKS + " 个");
        }
        List<Link> normalized = new ArrayList<>();
        boolean primaryAssigned = false;
        for (Link source : links) {
            Link link = normalizeLink(source);
            if (link.isPrimary() && !primaryAssigned) {
                primaryAssigned = true;
            } else {
                link.setPrimary(false);
            }
            normalized.add(link);
        }
        if (!primaryAssigned && !normalized.isEmpty()) {
            normalized.get(0).setPrimary(true);
        }
        links = List.copyOf(normalized);
    }

    public boolean hasLinks() {
        return enabled && links != null && !links.isEmpty();
    }

    private Link normalizeLink(Link source) {
        if (source == null) {
            throw new ServiceException("交付体验入口不能为空");
        }
        Link link = new Link();
        link.setTitle(normalizeText(source.getTitle(), null, MAX_TITLE_LENGTH, "体验入口名称"));
        link.setTitleEn(normalizeOptionalText(source.getTitleEn(), MAX_TITLE_LENGTH, "英文体验入口名称"));
        link.setDescription(normalizeOptionalText(
                source.getDescription(), MAX_DESCRIPTION_LENGTH, "体验入口说明"));
        link.setDescriptionEn(normalizeOptionalText(
                source.getDescriptionEn(), MAX_DESCRIPTION_LENGTH, "英文体验入口说明"));
        link.setUrl(normalizeUrl(source.getUrl()));
        String kind = StringUtils.upperCase(StringUtils.defaultIfBlank(source.getKind(), "LINK"), Locale.ROOT);
        link.setKind(LINK_KINDS.contains(kind) ? kind : "LINK");
        link.setPrimary(source.isPrimary());
        return link;
    }

    private String normalizeUrl(String value) {
        String url = normalizeText(value, null, MAX_URL_LENGTH, "体验入口地址");
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || StringUtils.isBlank(uri.getHost())
                    || StringUtils.isNotBlank(uri.getUserInfo())) {
                throw new IllegalArgumentException();
            }
            return uri.normalize().toASCIIString();
        } catch (Exception e) {
            throw new ServiceException("体验入口必须是完整的 HTTPS 地址");
        }
    }

    private String normalizeText(String value, String fallback, int maxLength, String fieldName) {
        String normalized = StringUtils.defaultIfBlank(StringUtils.trimToNull(value), fallback);
        if (StringUtils.isBlank(normalized)) {
            throw new ServiceException(fieldName + "不能为空");
        }
        if (normalized.length() > maxLength) {
            throw new ServiceException(fieldName + "不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    private String normalizeOptionalText(String value, int maxLength, String fieldName) {
        String normalized = StringUtils.trimToNull(value);
        if (normalized != null && normalized.length() > maxLength) {
            throw new ServiceException(fieldName + "不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    /** 交付体验入口。 */
    @Data
    public static class Link {

        /** 中文入口名称。 */
        private String title;

        /** 英文入口名称。 */
        private String titleEn;

        /** 中文入口说明。 */
        private String description;

        /** 英文入口说明。 */
        private String descriptionEn;

        /** HTTPS 入口地址。 */
        private String url;

        /** 入口类型，例如 REPOSITORY、DEMO 或 DOCUMENTATION。 */
        private String kind;

        /** 是否作为首要操作展示。 */
        private boolean primary;
    }
}

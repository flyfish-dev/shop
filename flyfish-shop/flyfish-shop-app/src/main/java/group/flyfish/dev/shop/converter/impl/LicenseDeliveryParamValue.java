package group.flyfish.dev.shop.converter.impl;

import group.flyfish.dev.shop.converter.ShopItemParamValue;
import group.flyfish.dev.shop.domain.po.ShopDeliveryAction;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 授权许可交付参数。
 * <p>授权密钥由系统根证书签发，商品参数只描述授权语义，不保存密钥材料。</p>
 */
@Data
public class LicenseDeliveryParamValue implements ShopItemParamValue {

    public static final String LICENSE_KIND_RUNTIME = "runtime-license";

    public static final List<String> DEFAULT_OFFICE_FEATURES = List.of(
            "doc", "docx", "ppt", "pptx", "xls", "xlsx", "xlsb", "virtual-excel");

    /**
     * 授权类型。开源版仅保留运行授权业务参数，真实签发语义由私有扩展实现。
     */
    private String licenseKind;

    /**
     * 授权名称，例如 Office 预览套件企业授权。
     */
    private String licenseName;

    /**
     * 授权范围，例如 product:office-viewer 或 repo:flyfish-dev/viewer。
     */
    private String scope;

    /**
     * 授权产品。
     */
    private String product;

    /**
     * 套餐等级：personal、commercial、enterprise。
     */
    private String edition;

    /**
     * 持有人展示名称。
     */
    private String holder;

    /**
     * 授权部署来源，例如 https://demo.flyfish.group 或 https://*.example.com。
     */
    private List<String> allowedOrigins;

    /**
     * 授权功能列表。
     */
    private List<String> features;

    /**
     * 最大部署数。个人版和商业版只能为 1；企业版允许多域名/通配域名。
     */
    private Integer maxDeployments;

    /**
     * 是否允许商业使用。
     */
    private Boolean commercialUse;

    /**
     * 商品自动交付动作，混合商品用。
     */
    private List<ShopDeliveryAction> deliveryActions;

    /**
     * 有效天数。为空或小于等于 0 表示长期有效。
     */
    private Integer validDays;

    /**
     * 展示给用户的授权说明。
     */
    private String remark;

    public void normalize(String fallbackName) {
        licenseKind = normalizeLicenseKind(licenseKind);
        licenseName = StringUtils.defaultIfBlank(StringUtils.trimToNull(licenseName),
                StringUtils.defaultIfBlank(fallbackName, "飞鱼小铺授权许可"));
        scope = StringUtils.defaultIfBlank(StringUtils.trimToNull(scope), "product:" + licenseName);
        product = StringUtils.defaultIfBlank(StringUtils.trimToNull(product), "license-product");
        edition = normalizeEdition(edition);
        holder = StringUtils.defaultIfBlank(StringUtils.trimToNull(holder), licenseName);
        allowedOrigins = normalizeOrigins(allowedOrigins);
        features = normalizeFeatures(features);
        maxDeployments = normalizeMaxDeployments(maxDeployments, edition, allowedOrigins);
        commercialUse = commercialUse == null ? !"personal".equals(edition) : commercialUse;
        deliveryActions = normalizeActions(deliveryActions);
        remark = StringUtils.trimToNull(remark);
        if (validDays != null && validDays <= 0) {
            validDays = null;
        }
    }

    public boolean hasAuthorizedOrigin() {
        return allowedOrigins != null && !allowedOrigins.isEmpty();
    }

    public boolean isEnterprise() {
        return "enterprise".equals(edition);
    }

    private String normalizeLicenseKind(String value) {
        return LICENSE_KIND_RUNTIME;
    }

    private String normalizeEdition(String value) {
        String normalized = StringUtils.lowerCase(StringUtils.trimToNull(value), Locale.ROOT);
        if ("personal".equals(normalized) || "commercial".equals(normalized)
                || "enterprise".equals(normalized) || "demo".equals(normalized)) {
            return normalized;
        }
        return "commercial";
    }

    private List<String> normalizeOrigins(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String origin = StringUtils.lowerCase(StringUtils.trimToNull(value), Locale.ROOT);
            if (origin == null) {
                continue;
            }
            while (origin.endsWith("/")) {
                origin = origin.substring(0, origin.length() - 1);
            }
            if (StringUtils.isNotBlank(origin)) {
                normalized.add(origin);
            }
        }
        return new ArrayList<>(normalized);
    }

    private List<String> normalizeFeatures(List<String> values) {
        List<String> source = values == null || values.isEmpty() ? DEFAULT_OFFICE_FEATURES : values;
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : source) {
            String feature = StringUtils.lowerCase(StringUtils.trimToNull(value), Locale.ROOT);
            if (feature != null) {
                normalized.add(feature);
            }
        }
        return new ArrayList<>(normalized);
    }

    private Integer normalizeMaxDeployments(Integer value, String edition, List<String> origins) {
        int requested = value == null || value <= 0 ? Math.max(1, origins == null ? 1 : origins.size()) : value;
        if (!"enterprise".equals(edition)) {
            return 1;
        }
        return Math.max(1, requested);
    }

    private List<ShopDeliveryAction> normalizeActions(List<ShopDeliveryAction> values) {
        if (values == null || values.isEmpty()) {
            return List.of(ShopDeliveryAction.LICENSE);
        }
        Set<ShopDeliveryAction> normalized = new LinkedHashSet<>();
        for (ShopDeliveryAction action : values) {
            if (action != null) {
                normalized.add(action);
            }
        }
        if (normalized.isEmpty()) {
            normalized.add(ShopDeliveryAction.LICENSE);
        }
        return new ArrayList<>(normalized);
    }
}

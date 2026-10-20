package group.flyfish.dev.shop.converter.impl;

import group.flyfish.dev.shop.converter.ShopItemParamValue;
import group.flyfish.dev.shop.domain.po.ShopDeliveryAction;
import group.flyfish.dev.shop.license.LicenseOriginPolicy;
import group.flyfish.dev.shop.license.ProductLicenseProfile;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
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
    public static final String LICENSE_KIND_BRAND_REMOVAL = "brand-removal";
    public static final String PRODUCT_OFFICE_PREVIEW = ProductLicenseProfile.OFFICE_PREVIEW.getCode();
    public static final String PRODUCT_DWG_VIEWER = ProductLicenseProfile.DWG_VIEWER.getCode();
    public static final String PRODUCT_MODEL_VIEWER = ProductLicenseProfile.MODEL_VIEWER.getCode();
    public static final String PRODUCT_FILE_VIEWER = ProductLicenseProfile.FILE_VIEWER.getCode();
    public static final String FEATURE_DWG = "dwg";

    public static final List<String> DEFAULT_OFFICE_FEATURES = ProductLicenseProfile.OFFICE_PREVIEW.getFeatures();
    public static final List<String> DEFAULT_DWG_FEATURES = ProductLicenseProfile.DWG_VIEWER.getFeatures();
    public static final List<String> DEFAULT_MODEL_FEATURES = ProductLicenseProfile.MODEL_VIEWER.getFeatures();
    public static final List<String> DEFAULT_VIEWER_BRAND_FEATURES = ProductLicenseProfile.FILE_VIEWER.getFeatures();

    /**
     * 授权类型：runtime-license 为可直接部署的运行授权；brand-removal 为基于 Apache 2.0 边界的品牌标识移除声明。
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
     * 授权产品。Office Preview 为 office-preview；DWG Viewer 为 dwg-viewer；
     * Flyfish Viewer 声明授权为 file-viewer。
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
     * 部署域名模式：specific 为最多五个具体 origin；saas 为单个 HTTPS 泛域名。
     */
    private String deploymentMode;

    /**
     * 授权功能列表。
     */
    private List<String> features;

    /**
     * 最大部署数。商业版具体域名模式最多为 5，SaaS 模式为 1；企业版由合同约定。
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

    /**
     * 下单时需要买家补充的信息配置。
     */
    private ShopOrderFormParamValue orderForm;

    /**
     * 商业版或企业版首购时需要确认的年度维护协议。
     *
     * <p>该配置只负责合同确认，不参与首购价格计算。首年授权和维护权益已经包含在商品价格中。</p>
     */
    private MaintenanceAgreement maintenanceAgreement;

    /**
     * 独立授权续费 SKU 配置。
     *
     * <p>续费 SKU 不保存另一套授权参数，签发时从买家已有许可证派生，避免授权能力漂移。</p>
     */
    private LicenseRenewal licenseRenewal;

    /** 交付完成后的运行与验证入口。 */
    private DeliveryExperienceParamValue deliveryExperience;

    public void normalize(String fallbackName) {
        licenseKind = normalizeLicenseKind(licenseKind);
        product = normalizeProduct(product);
        licenseName = StringUtils.defaultIfBlank(StringUtils.trimToNull(licenseName),
                StringUtils.defaultIfBlank(fallbackName, isBrandRemoval()
                        ? "Flyfish Viewer 去品牌标识授权声明" : "飞鱼小铺授权许可"));
        scope = StringUtils.defaultIfBlank(StringUtils.trimToNull(scope),
                isBrandRemoval() ? "product:file-viewer:remove-branding" : "product:" + licenseName);
        edition = normalizeEdition(edition);
        holder = StringUtils.defaultIfBlank(StringUtils.trimToNull(holder),
                isBrandRemoval() ? "Flyfish Viewer" : licenseName);
        allowedOrigins = isBrandRemoval() ? List.of() : normalizeOrigins(allowedOrigins);
        boolean originProvidedAtCheckout = orderForm != null
                && orderForm.overridesTarget(ShopOrderFormParamValue.TARGET_LICENSE_ALLOWED_ORIGINS);
        if (!isBrandRemoval() && !originProvidedAtCheckout) {
            LicenseOriginPolicy.Result originPolicy = LicenseOriginPolicy.normalize(edition, deploymentMode, allowedOrigins);
            deploymentMode = originPolicy.deploymentMode();
            allowedOrigins = originPolicy.allowedOrigins();
        }
        features = normalizeFeatures(features);
        maxDeployments = normalizeMaxDeployments(maxDeployments, edition, allowedOrigins);
        commercialUse = commercialUse == null ? !"personal".equals(edition) : commercialUse;
        deliveryActions = normalizeActions(deliveryActions);
        remark = StringUtils.trimToNull(remark);
        if (validDays != null && validDays <= 0) {
            validDays = null;
        }
        maintenanceAgreement = normalizeMaintenanceAgreement(maintenanceAgreement);
        licenseRenewal = normalizeLicenseRenewal(licenseRenewal);
        if (deliveryExperience != null) {
            deliveryExperience.normalize();
        }
    }

    /**
     * 仅规范化购买阶段使用的协议和续费策略。
     *
     * <p>购买策略不依赖部署域名。完整签发时仍由 {@link #normalize(String)} 严格校验 origin，
     * 两个阶段保持各自职责，避免续费 SKU 因未重复配置来源许可证参数而无法购买。</p>
     */
    public void normalizePurchaseConfiguration() {
        licenseKind = normalizeLicenseKind(licenseKind);
        edition = normalizeEdition(edition);
        maintenanceAgreement = normalizeMaintenanceAgreement(maintenanceAgreement);
        licenseRenewal = normalizeLicenseRenewal(licenseRenewal);
    }

    public boolean hasAuthorizedOrigin() {
        return allowedOrigins != null && !allowedOrigins.isEmpty();
    }

    public boolean requiresAuthorizedOrigin() {
        return !isBrandRemoval();
    }

    public boolean isEnterprise() {
        return "enterprise".equals(edition);
    }

    public boolean isOfficePreview() {
        return PRODUCT_OFFICE_PREVIEW.equals(product);
    }

    public boolean isFileViewer() {
        return PRODUCT_FILE_VIEWER.equals(product);
    }

    public boolean isDwgViewer() {
        return PRODUCT_DWG_VIEWER.equals(product);
    }

    public boolean isModelViewer() {
        return PRODUCT_MODEL_VIEWER.equals(product);
    }

    public boolean isBrandRemoval() {
        return LICENSE_KIND_BRAND_REMOVAL.equals(licenseKind);
    }

    private String normalizeLicenseKind(String value) {
        String normalized = StringUtils.lowerCase(StringUtils.trimToNull(value), Locale.ROOT);
        if (LICENSE_KIND_BRAND_REMOVAL.equals(normalized)) {
            return LICENSE_KIND_BRAND_REMOVAL;
        }
        return LICENSE_KIND_RUNTIME;
    }

    private String normalizeProduct(String value) {
        String normalized = StringUtils.lowerCase(StringUtils.trimToNull(value), Locale.ROOT);
        if (StringUtils.isNotBlank(normalized)) {
            return normalized;
        }
        return isBrandRemoval() ? PRODUCT_FILE_VIEWER : PRODUCT_OFFICE_PREVIEW;
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
        List<String> source = values == null || values.isEmpty()
                ? defaultFeatures()
                : values;
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : source) {
            String feature = StringUtils.lowerCase(StringUtils.trimToNull(value), Locale.ROOT);
            if (feature != null) {
                normalized.add(feature);
            }
        }
        if (isBrandRemoval()) {
            normalized.removeIf(feature -> !DEFAULT_VIEWER_BRAND_FEATURES.contains(feature));
            if (normalized.isEmpty()) {
                normalized.addAll(DEFAULT_VIEWER_BRAND_FEATURES);
            }
        } else if (isDwgViewer()) {
            normalized.removeIf(feature -> !DEFAULT_DWG_FEATURES.contains(feature));
            if (normalized.isEmpty()) {
                normalized.addAll(DEFAULT_DWG_FEATURES);
            }
        } else if (isModelViewer()) {
            normalized.removeIf(feature -> !DEFAULT_MODEL_FEATURES.contains(feature));
            if (normalized.isEmpty()) {
                normalized.addAll(DEFAULT_MODEL_FEATURES);
            }
        }
        return new ArrayList<>(normalized);
    }

    private List<String> defaultFeatures() {
        if (isBrandRemoval()) {
            return DEFAULT_VIEWER_BRAND_FEATURES;
        }
        if (isDwgViewer()) {
            return DEFAULT_DWG_FEATURES;
        }
        return isModelViewer() ? DEFAULT_MODEL_FEATURES : DEFAULT_OFFICE_FEATURES;
    }

    private Integer normalizeMaxDeployments(Integer value, String edition, List<String> origins) {
        int requested = value == null || value <= 0 ? Math.max(1, origins == null ? 1 : origins.size()) : value;
        if ("personal".equals(edition) || "demo".equals(edition)) {
            return 1;
        }
        if ("commercial".equals(edition)) {
            if (LicenseOriginPolicy.MODE_SAAS.equals(deploymentMode)) {
                return 1;
            }
            return Math.min(LicenseOriginPolicy.COMMERCIAL_MAX_SPECIFIC_ORIGINS,
                    Math.max(requested, origins == null ? 1 : origins.size()));
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

    private MaintenanceAgreement normalizeMaintenanceAgreement(MaintenanceAgreement value) {
        if (!Set.of("commercial", "enterprise").contains(edition)
                || isBrandRemoval()
                || value == null
                || !Boolean.TRUE.equals(value.getEnabled())) {
            return null;
        }
        MaintenanceAgreement normalized = new MaintenanceAgreement();
        normalized.setEnabled(true);
        normalized.setRequired(value.getRequired() == null || value.getRequired());
        normalized.setContractIds(value.getContractIds() == null
                ? List.of()
                : value.getContractIds().stream().filter(id -> id != null && id > 0).distinct().toList());
        return normalized;
    }

    private LicenseRenewal normalizeLicenseRenewal(LicenseRenewal value) {
        if (isBrandRemoval() || value == null || !Boolean.TRUE.equals(value.getEnabled())) {
            return null;
        }
        LicenseRenewal normalized = new LicenseRenewal();
        normalized.setEnabled(true);
        BigDecimal annualPrice = value.getAnnualPrice();
        normalized.setAnnualPrice(annualPrice == null || annualPrice.signum() <= 0
                ? new BigDecimal("199.00")
                : annualPrice.setScale(2, java.math.RoundingMode.HALF_UP));
        normalized.setMaxYears(value.getMaxYears() == null || value.getMaxYears() <= 0
                ? 10
                : Math.min(value.getMaxYears(), 20));
        boolean editionsUnspecified = value.getEligibleEditions() == null
                || value.getEligibleEditions().isEmpty();
        List<String> editions = editionsUnspecified
                ? List.of("commercial", "enterprise")
                : value.getEligibleEditions().stream()
                        .map(candidate -> StringUtils.lowerCase(StringUtils.trimToNull(candidate), Locale.ROOT))
                        .filter(candidate -> Set.of("commercial", "enterprise").contains(candidate))
                        .distinct()
                        .toList();
        normalized.setEligibleEditions(editions);
        normalized.setContractIds(value.getContractIds() == null
                ? List.of()
                : value.getContractIds().stream().filter(id -> id != null && id > 0).distinct().toList());
        return normalized;
    }

    @Data
    public static class MaintenanceAgreement {

        /**
         * 是否启用维护协议确认。
         */
        private Boolean enabled;

        /**
         * 是否必须在首购前确认。默认必须确认。
         */
        private Boolean required;

        /**
         * 勾选确认后必须签署的合同 ID。
         */
        private List<Long> contractIds;
    }

    @Data
    public static class LicenseRenewal {

        /**
         * 是否将当前 SKU 作为授权续费 SKU。
         */
        private Boolean enabled;

        /**
         * 单年续费价格，默认人民币 199 元。
         */
        private BigDecimal annualPrice;

        /**
         * 单笔订单最多续费年数。
         */
        private Integer maxYears;

        /**
         * 允许续费的原授权版本。
         */
        private List<String> eligibleEditions;

        /**
         * 续费前必须签署的合同 ID。
         */
        private List<Long> contractIds;
    }
}

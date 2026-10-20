package group.flyfish.dev.shop.license;

import group.flyfish.dev.auth.api.user.PortalUserVo;
import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.common.json.JacksonUtils;
import group.flyfish.dev.shop.converter.ShopItemParamValue;
import group.flyfish.dev.shop.converter.impl.LicenseDeliveryParamValue;
import group.flyfish.dev.shop.domain.po.ShopDeliveryAction;
import group.flyfish.dev.shop.domain.po.ShopItem;
import group.flyfish.dev.shop.domain.po.ShopItemSku;
import group.flyfish.dev.shop.domain.po.ShopLicenseKeyPair;
import group.flyfish.dev.shop.domain.po.ShopOrder;
import group.flyfish.dev.shop.domain.vo.ShopLicenseRenewalEligibilityVo;
import group.flyfish.dev.shop.repository.ShopItemRepository;
import group.flyfish.dev.shop.repository.ShopItemSkuRepository;
import group.flyfish.dev.shop.repository.ShopLicenseKeyPairRepository;
import group.flyfish.dev.shop.repository.ShopOrderRepository;
import group.flyfish.dev.shop.support.ShopAuthorizationUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.core.type.TypeReference;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 授权续费资格、计价和许可证派生服务。
 *
 * <p>续费 SKU 只维护单年价格、最大年数和合同，不重复保存授权功能。实际签发参数从买家
 * 已持有的商业版或企业版许可证证书中派生，确保续费前后的授权能力完全一致。</p>
 */
@Service
@RequiredArgsConstructor
public class LicenseRenewalService {

    public static final String ORDER_PROPERTY_KEY = "licenseRenewal";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Comparator<EligibleLicense> LATEST_EXPIRY_FIRST = Comparator
            .comparing(EligibleLicense::suggestedStartDate, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(candidate -> candidate.license().getId(), Comparator.nullsLast(Comparator.reverseOrder()));

    private final ShopItemRepository itemRepository;
    private final ShopItemSkuRepository skuRepository;
    private final ShopLicenseKeyPairRepository licenseRepository;
    private final ShopOrderRepository orderRepository;

    /**
     * 查询当前用户对指定续费 SKU 的资格和默认值。
     */
    public Mono<ShopLicenseRenewalEligibilityVo> getEligibility(Long itemId, Long skuId, PortalUserVo buyer) {
        ShopAuthorizationUtils.requireLogin(buyer);
        return resolveEffectiveItem(itemId, skuId)
                .flatMap(item -> {
                    LicenseDeliveryParamValue.LicenseRenewal config = renewalConfig(item);
                    if (config == null) {
                        return Mono.just(notRenewalSku());
                    }
                    return findEligibleLicenses(buyer.getId(), item.getId(), config)
                            .collectSortedList(LATEST_EXPIRY_FIRST)
                            .map(licenses -> toEligibility(config, licenses));
                });
    }

    /**
     * 准备订单续费数据。金额、年数上限和资格全部由服务端重新校验。
     */
    public Mono<Selection> prepare(ShopItem item, PortalUserVo buyer, String sourceLicenseNo,
                                   LocalDate requestedStartDate, Integer requestedYears) {
        LicenseDeliveryParamValue.LicenseRenewal config = renewalConfig(item);
        if (config == null) {
            return Mono.just(Selection.standard());
        }
        if (config.getContractIds() == null || config.getContractIds().isEmpty()) {
            return Mono.error(new BusinessException("LICENSE_RENEWAL_CONTRACT_MISSING",
                    "授权续费协议尚未配置"));
        }
        int years = normalizeYears(requestedYears, config.getMaxYears());
        return findEligibleLicenses(buyer.getId(), item.getId(), config)
                .collectSortedList(LATEST_EXPIRY_FIRST)
                .flatMap(licenses -> {
                    if (licenses.isEmpty()) {
                        return Mono.error(new BusinessException("LICENSE_RENEWAL_NOT_ELIGIBLE",
                                "购买授权续费前，需要先持有该商品的商业版或企业版授权"));
                    }
                    EligibleLicense source = selectSource(licenses, sourceLicenseNo);
                    LocalDate startDate = requestedStartDate == null
                            ? source.suggestedStartDate()
                            : requestedStartDate;
                    if (startDate == null) {
                        return Mono.error(new BusinessException("LICENSE_RENEWAL_START_DATE_REQUIRED",
                                "请选择续费生效日期"));
                    }
                    LocalDate endDate = startDate.plusYears(years);
                    BigDecimal amount = config.getAnnualPrice()
                            .multiply(BigDecimal.valueOf(years))
                            .setScale(2, RoundingMode.HALF_UP);
                    return Mono.just(new Selection(true, source.license().getLicenseNo(),
                            source.license().getOrderNo(), startDate, endDate, years,
                            config.getAnnualPrice(), amount, config.getContractIds()));
                });
    }

    /**
     * 从续费订单恢复签发参数和明确的授权时间窗。
     */
    public Mono<DeliveryPlan> resolveDelivery(ShopOrder order, ShopItem item) {
        LicenseDeliveryParamValue.LicenseRenewal config = renewalConfig(item);
        if (config == null) {
            return Mono.just(DeliveryPlan.standard(
                    ShopItemParamValue.licenseDelivery(item.getParams(), item.getName())));
        }
        Map<String, Object> renewal = renewalOrderProperties(order);
        String sourceLicenseNo = text(renewal.get("sourceLicenseNo"));
        LocalDate startDate = localDate(renewal.get("startDate"));
        LocalDate endDate = localDate(renewal.get("endDate"));
        if (sourceLicenseNo == null || startDate == null || endDate == null || !endDate.isAfter(startDate)) {
            return Mono.error(new BusinessException("LICENSE_RENEWAL_ORDER_INVALID", "续费订单缺少有效的授权时间信息"));
        }
        return licenseRepository.findByBuyerIdAndItemIdAndLicenseNo(
                        order.getBuyerId(), order.getItemId(), sourceLicenseNo)
                .switchIfEmpty(Mono.error(new BusinessException("LICENSE_RENEWAL_SOURCE_NOT_FOUND",
                        "续费来源许可证不存在或不属于当前用户")))
                .map(source -> {
                    LicenseDeliveryParamValue param = licenseParam(source.getCertificate(), item.getName());
                    validateEligibleEdition(param.getEdition(), config.getEligibleEditions());
                    ProductLicensePeriod period = new ProductLicensePeriod(
                            startDate.atStartOfDay(BUSINESS_ZONE).toInstant(),
                            endDate.atStartOfDay(BUSINESS_ZONE).toInstant(),
                            sourceLicenseNo);
                    return new DeliveryPlan(true, param, period);
                });
    }

    public boolean isRenewalSku(ShopItem item) {
        return renewalConfig(item) != null;
    }

    public List<Long> renewalContractIds(ShopItem item) {
        LicenseDeliveryParamValue.LicenseRenewal config = renewalConfig(item);
        return config == null ? List.of() : config.getContractIds();
    }

    public Mono<List<Long>> renewalContractIds(Long itemId, Long skuId) {
        return resolveEffectiveItem(itemId, skuId).map(this::renewalContractIds);
    }

    public Map<String, Object> orderProperties(Selection selection) {
        if (selection == null || !selection.renewal()) {
            return Map.of();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("sourceLicenseNo", selection.sourceLicenseNo());
        values.put("sourceOrderNo", selection.sourceOrderNo());
        values.put("startDate", selection.startDate());
        values.put("endDate", selection.endDate());
        values.put("years", selection.years());
        values.put("annualPrice", selection.annualPrice());
        return values;
    }

    private Mono<ShopItem> resolveEffectiveItem(Long itemId, Long skuId) {
        return itemRepository.findById(itemId)
                .switchIfEmpty(Mono.error(new BusinessException("ITEM_NOT_FOUND", "商品不存在")))
                .flatMap(item -> {
                    if (skuId == null) {
                        return Mono.just(item);
                    }
                    return skuRepository.findById(skuId)
                            .filter(sku -> itemId.equals(sku.getItemId()))
                            .switchIfEmpty(Mono.error(new BusinessException("SKU_NOT_FOUND", "SKU不存在")))
                            .map(sku -> effectiveItem(item, sku));
                });
    }

    private ShopItem effectiveItem(ShopItem item, ShopItemSku sku) {
        ShopItem effective = new ShopItem();
        effective.setId(item.getId());
        effective.setName(item.getName() + " - " + sku.getName());
        effective.setType(sku.getType());
        effective.setParams(sku.getParams());
        effective.setEnabled(Boolean.TRUE.equals(item.getEnabled()) && Boolean.TRUE.equals(sku.getEnabled()));
        return effective;
    }

    private Flux<EligibleLicense> findEligibleLicenses(Long buyerId, Long itemId,
                                                       LicenseDeliveryParamValue.LicenseRenewal config) {
        return licenseRepository.findAllByBuyerIdAndItemId(buyerId, itemId)
                .concatMap(license -> orderRepository.findByOrderNo(license.getOrderNo())
                        .flatMap(order -> eligibleLicense(license, order, config))
                        .onErrorResume(ignored -> Mono.empty()));
    }

    private Mono<EligibleLicense> eligibleLicense(ShopLicenseKeyPair license, ShopOrder order,
                                                  LicenseDeliveryParamValue.LicenseRenewal config) {
        LicenseDeliveryParamValue param = licenseParam(license.getCertificate(), order.getItemName());
        if (!config.getEligibleEditions().contains(param.getEdition())
                || param.isBrandRemoval()
                || (!"commercial".equals(param.getEdition()) && !"enterprise".equals(param.getEdition()))) {
            return Mono.empty();
        }
        LocalDate expiresOn = expiryDate(license.getCertificate());
        boolean permanent = expiresOn == null;
        LocalDate suggestedStartDate = expiresOn;
        if (suggestedStartDate == null) {
            if (order.getPaidTime() != null) {
                suggestedStartDate = order.getPaidTime().toLocalDate().plusYears(1);
            } else if (order.getCreateTime() != null) {
                suggestedStartDate = order.getCreateTime().toLocalDate().plusYears(1);
            }
        }
        return Mono.just(new EligibleLicense(license, param, permanent, expiresOn, suggestedStartDate));
    }

    private EligibleLicense selectSource(List<EligibleLicense> licenses, String sourceLicenseNo) {
        if (StringUtils.isBlank(sourceLicenseNo)) {
            return licenses.getFirst();
        }
        return licenses.stream()
                .filter(candidate -> StringUtils.equals(candidate.license().getLicenseNo(), sourceLicenseNo))
                .findFirst()
                .orElseThrow(() -> new BusinessException("LICENSE_RENEWAL_SOURCE_NOT_FOUND",
                        "所选许可证不存在或不可续费"));
    }

    private ShopLicenseRenewalEligibilityVo toEligibility(LicenseDeliveryParamValue.LicenseRenewal config,
                                                          List<EligibleLicense> licenses) {
        ShopLicenseRenewalEligibilityVo vo = new ShopLicenseRenewalEligibilityVo();
        vo.setRenewalSku(true);
        vo.setAnnualPrice(config.getAnnualPrice());
        vo.setMaxYears(config.getMaxYears());
        vo.setEligible(!licenses.isEmpty());
        if (licenses.isEmpty()) {
            vo.setReasonCode("LICENSE_RENEWAL_NOT_ELIGIBLE");
            vo.setMessage("购买授权续费前，需要先持有该商品的商业版或企业版授权");
            return vo;
        }
        EligibleLicense defaultLicense = licenses.getFirst();
        vo.setDefaultLicenseNo(defaultLicense.license().getLicenseNo());
        vo.setDefaultStartDate(defaultLicense.suggestedStartDate());
        vo.setLicenses(licenses.stream().map(this::toOption).toList());
        return vo;
    }

    private ShopLicenseRenewalEligibilityVo notRenewalSku() {
        ShopLicenseRenewalEligibilityVo vo = new ShopLicenseRenewalEligibilityVo();
        vo.setRenewalSku(false);
        vo.setEligible(false);
        vo.setReasonCode("NOT_LICENSE_RENEWAL_SKU");
        vo.setMessage("当前套餐不是授权续费");
        return vo;
    }

    private ShopLicenseRenewalEligibilityVo.LicenseOption toOption(EligibleLicense source) {
        ShopLicenseRenewalEligibilityVo.LicenseOption option = new ShopLicenseRenewalEligibilityVo.LicenseOption();
        option.setLicenseNo(source.license().getLicenseNo());
        option.setOrderNo(source.license().getOrderNo());
        option.setLicenseName(source.param().getLicenseName());
        option.setEdition(source.param().getEdition());
        option.setEditionName(editionName(source.param().getEdition()));
        option.setPermanent(source.permanent());
        option.setExpiresOn(source.expiresOn());
        option.setSuggestedStartDate(source.suggestedStartDate());
        return option;
    }

    private LicenseDeliveryParamValue.LicenseRenewal renewalConfig(ShopItem item) {
        if (item == null || item.getType() != ShopItem.Type.LICENSE) {
            return null;
        }
        return ShopItemParamValue.licenseDeliveryConfiguration(item.getParams()).getLicenseRenewal();
    }

    private LicenseDeliveryParamValue licenseParam(String certificate, String fallbackName) {
        Map<String, Object> payload = payload(certificate);
        LicenseDeliveryParamValue param = new LicenseDeliveryParamValue();
        param.setLicenseKind(text(payload.get("licenseKind")));
        param.setLicenseName(text(payload.get("licenseName")));
        param.setScope(text(payload.get("scope")));
        param.setProduct(text(payload.get("product")));
        param.setEdition(text(payload.get("edition")));
        param.setHolder(text(payload.get("holder")));
        param.setAllowedOrigins(strings(payload.get("allowedOrigins")));
        param.setDeploymentMode(text(payload.get("deploymentMode")));
        param.setFeatures(strings(payload.get("features")));
        param.setMaxDeployments(integer(payload.get("maxDeployments"), null));
        param.setCommercialUse(bool(payload.get("commercialUse")));
        param.setDeliveryActions(List.of(ShopDeliveryAction.LICENSE));
        param.setRemark(text(payload.get("remark")));
        param.normalize(fallbackName);
        return param;
    }

    private Map<String, Object> payload(String certificate) {
        if (StringUtils.isBlank(certificate)) {
            throw new BusinessException("LICENSE_CERTIFICATE_INVALID", "已有许可证证书为空");
        }
        try {
            return JacksonUtils.readValue(certificate, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new BusinessException("LICENSE_CERTIFICATE_INVALID", "已有许可证证书无法解析");
        }
    }

    private Map<String, Object> renewalOrderProperties(ShopOrder order) {
        if (order == null || StringUtils.isBlank(order.getProperties())) {
            return Map.of();
        }
        try {
            Map<String, Object> properties = JacksonUtils.readValue(order.getProperties(), new TypeReference<>() {
            });
            Object value = properties.get(ORDER_PROPERTY_KEY);
            if (value instanceof Map<?, ?> map) {
                return JacksonUtils.readValue(JacksonUtils.toJson(map), new TypeReference<>() {
                });
            }
            return Map.of();
        } catch (Exception e) {
            return Map.of();
        }
    }

    private LocalDate expiryDate(String certificate) {
        String expiresAt = text(payload(certificate).get("expiresAt"));
        if (expiresAt == null) {
            return null;
        }
        try {
            return Instant.parse(expiresAt).atZone(BUSINESS_ZONE).toLocalDate();
        } catch (Exception e) {
            return null;
        }
    }

    private int normalizeYears(Integer requestedYears, int maxYears) {
        int years = requestedYears == null ? 1 : requestedYears;
        if (years <= 0 || years > maxYears) {
            throw new BusinessException("LICENSE_RENEWAL_YEARS_INVALID",
                    "单次续费年数必须在 1 到 " + maxYears + " 年之间");
        }
        return years;
    }

    private void validateEligibleEdition(String edition, List<String> eligibleEditions) {
        if (!eligibleEditions.contains(edition)) {
            throw new BusinessException("LICENSE_RENEWAL_EDITION_INVALID", "来源许可证版本不支持续费");
        }
    }

    private String editionName(String edition) {
        return switch (StringUtils.lowerCase(StringUtils.defaultString(edition), Locale.ROOT)) {
            case "enterprise" -> "企业授权";
            case "commercial" -> "商业授权";
            default -> edition;
        };
    }

    private String text(Object value) {
        return value == null ? null : StringUtils.trimToNull(String.valueOf(value));
    }

    private Integer integer(Object value, Integer fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.valueOf(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
    }

    private Boolean bool(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value == null ? null : Boolean.valueOf(String.valueOf(value));
    }

    private List<String> strings(Object value) {
        if (!(value instanceof Collection<?> values)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object candidate : values) {
            String normalized = text(candidate);
            if (normalized != null && !result.contains(normalized)) {
                result.add(normalized);
            }
        }
        return result;
    }

    private LocalDate localDate(Object value) {
        try {
            return value == null ? null : LocalDate.parse(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private record EligibleLicense(ShopLicenseKeyPair license, LicenseDeliveryParamValue param,
                                   boolean permanent, LocalDate expiresOn, LocalDate suggestedStartDate) {
    }

    public record Selection(boolean renewal, String sourceLicenseNo, String sourceOrderNo,
                            LocalDate startDate, LocalDate endDate, int years,
                            BigDecimal annualPrice, BigDecimal amount, List<Long> contractIds) {

        private static Selection standard() {
            return new Selection(false, null, null, null, null, 0,
                    BigDecimal.ZERO, BigDecimal.ZERO, List.of());
        }
    }

    public record DeliveryPlan(boolean renewal, LicenseDeliveryParamValue param, ProductLicensePeriod period) {

        private static DeliveryPlan standard(LicenseDeliveryParamValue param) {
            return new DeliveryPlan(false, param, null);
        }
    }
}

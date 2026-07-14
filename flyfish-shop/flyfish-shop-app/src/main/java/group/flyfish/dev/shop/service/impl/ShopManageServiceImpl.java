package group.flyfish.dev.shop.service.impl;

import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.shop.convert.ShopConvert;
import group.flyfish.dev.shop.converter.ShopItemDeliveryPlan;
import group.flyfish.dev.shop.converter.ShopItemParamValue;
import group.flyfish.dev.shop.converter.impl.DigitalDeliveryParamValue;
import group.flyfish.dev.shop.converter.impl.GitRepositoryAccessParamValue;
import group.flyfish.dev.shop.converter.impl.LicenseDeliveryParamValue;
import group.flyfish.dev.shop.domain.dto.*;
import group.flyfish.dev.shop.domain.po.ShopDeliveryAction;
import group.flyfish.dev.shop.domain.po.Shop;
import group.flyfish.dev.shop.domain.po.ShopItem;
import group.flyfish.dev.shop.domain.po.ShopItemGroup;
import group.flyfish.dev.shop.domain.po.ShopItemSku;
import group.flyfish.dev.shop.repository.ShopItemGroupRepository;
import group.flyfish.dev.shop.repository.ShopItemRepository;
import group.flyfish.dev.shop.repository.ShopItemSkuContractRepository;
import group.flyfish.dev.shop.repository.ShopItemSkuRepository;
import group.flyfish.dev.shop.repository.ShopRepository;
import group.flyfish.dev.shop.service.ShopContractService;
import group.flyfish.dev.shop.service.ShopManageService;
import group.flyfish.dev.shop.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.math.RoundingMode;

/**
 * 店铺管理服务实现类
 *
 * @author wangyu
 */
@Service
@RequiredArgsConstructor
public class ShopManageServiceImpl implements ShopManageService {

    private final ShopRepository shopRepository;
    private final ShopItemRepository shopItemRepository;
    private final ShopItemSkuRepository shopItemSkuRepository;
    private final ShopItemSkuContractRepository shopItemSkuContractRepository;
    private final ShopItemGroupRepository shopItemGroupRepository;
    private final ShopService shopService;
    private final ShopContractService shopContractService;
    private final ShopConvert shopConvert;

    @Override
    @Transactional
    public Mono<Shop> createShop(ShopCreateDto dto) {
        return Mono.just(dto)
                .map(shopConvert::convert)
                .flatMap(shopRepository::save);
    }

    @Override
    @Transactional
    public Mono<Shop> updateShop(Long id, ShopUpdateDto dto) {
        return shopRepository.findById(id)
                .switchIfEmpty(Mono.error(new BusinessException("SHOP_NOT_FOUND", "店铺不存在")))
                .doOnNext(shop -> shopConvert.update(shop, dto))
                .flatMap(shopRepository::save);
    }

    @Override
    @Transactional
    public Mono<Void> deleteShop(Long id) {
        return shopRepository.findById(id)
                .switchIfEmpty(Mono.error(new BusinessException("SHOP_NOT_FOUND", "店铺不存在")))
                .flatMap(shop -> shopRepository.deleteById(shop.getId()));
    }

    @Override
    @Transactional
    public Mono<Void> createItemGroup(ShopItemGroupCreateDto dto) {
        return requireSavedShop()
                .map(shop -> {
                    ShopItemGroup group = shopConvert.convert(dto);
                    group.setShopId(shop.getId());
                    if (group.getSort() == null) {
                        group.setSort(0);
                    }
                    if (group.getEnabled() == null) {
                        group.setEnabled(true);
                    }
                    return group;
                })
                .flatMap(shopItemGroupRepository::save)
                .then();
    }

    @Override
    @Transactional
    public Mono<Void> updateItemGroup(Long id, ShopItemGroupUpdateDto dto) {
        return shopItemGroupRepository.findById(id)
                .switchIfEmpty(Mono.error(new BusinessException("GROUP_NOT_FOUND", "商品分组不存在")))
                .doOnNext(group -> shopConvert.update(group, dto))
                .flatMap(shopItemGroupRepository::save)
                .then();
    }

    @Override
    @Transactional
    public Mono<Void> deleteItemGroup(Long id) {
        return shopItemGroupRepository.findById(id)
                .switchIfEmpty(Mono.error(new BusinessException("GROUP_NOT_FOUND", "商品分组不存在")))
                .flatMap(group -> shopItemGroupRepository.deleteById(group.getId()));
    }

    @Override
    @Transactional
    public Mono<Void> createItem(ShopItemCreateDto dto) {
        return validateItemCreate(dto)
                .map(shop -> {
                    ShopItem item = shopConvert.convert(dto);
                    item.setShopId(shop.getId());
                    item.setBuyCount(0);
                    normalizeDeliveryMode(item, dto.getDeliveryMode() != null);
                    normalizeItemParams(item, dto.getDeliveryActions());
                    normalizeSkuMode(item, dto.getSkus(), true);
                    if (item.getSort() == null) {
                        item.setSort(0);
                    }
                    if (item.getEnabled() == null) {
                        item.setEnabled(false);
                    }
                    if (item.getPinned() == null) {
                        item.setPinned(false);
                    }
                    if (item.getRecommended() == null) {
                        item.setRecommended(false);
                    }
                    normalizeItemPresentation(item);
                    applyUsdPriceMode(item, dto.getUsdPriceAutomatic());
                    normalizeUsdPrice(item);
                    normalizeItemCoupon(item);
                    return item;
                })
                .flatMap(shopItemRepository::save)
                .flatMap(item -> syncItemContractsAndSkus(item, dto.getContractIds(), dto.getSkus()))
                .then();
    }

    private Mono<Shop> validateItemCreate(ShopItemCreateDto dto) {
        return Mono.zip(
                requireSavedShop(),
                shopItemGroupRepository.findById(dto.getGroupId())
                        .switchIfEmpty(Mono.error(new BusinessException("GROUP_NOT_FOUND", "商品分组不存在")))
        ).map(tuple -> tuple.getT1());
    }

    private Mono<Shop> requireSavedShop() {
        return shopService.getCurrentShop()
                .filter(shop -> shop.getId() != null)
                .switchIfEmpty(Mono.error(new BusinessException("SHOP_NOT_FOUND", "请先创建店铺")));
    }

    @Override
    @Transactional
    public Mono<Void> updateItem(Long id, ShopItemUpdateDto dto) {
        return shopItemRepository.findById(id)
                .switchIfEmpty(Mono.error(new BusinessException("ITEM_NOT_FOUND", "商品不存在")))
                .flatMap(item -> {
                    if (dto.getGroupId() != null) {
                        return shopItemGroupRepository.findById(dto.getGroupId())
                                .switchIfEmpty(Mono.error(new BusinessException("GROUP_NOT_FOUND", "商品分组不存在")))
                                .thenReturn(item);
                    }
                    return Mono.just(item);
                })
                .doOnNext(item -> {
                    shopConvert.update(item, dto);
                    applyExplicitImageUpdate(item, dto);
                    normalizeDeliveryMode(item, dto.getDeliveryMode() != null);
                    normalizeItemParams(item, dto.getDeliveryActions());
                    normalizeSkuMode(item, dto.getSkus(), dto.getSkuMode() != null || dto.getSkus() != null);
                    normalizeItemPresentation(item);
                    applyUsdPriceMode(item, dto.getUsdPriceAutomatic());
                    normalizeUsdPrice(item);
                    normalizeItemCoupon(item);
                })
                .flatMap(shopItemRepository::save)
                .flatMap(item -> syncItemContractsAndSkus(item, dto.getContractIds(), dto.getSkus()))
                .then();
    }

    private void applyExplicitImageUpdate(ShopItem item, ShopItemUpdateDto dto) {
        if (dto.getImages() == null) {
            if (dto.getCover() != null) {
                item.setCover(dto.getCover());
            }
            return;
        }
        List<String> images = dto.getImages().stream()
                .filter(org.apache.commons.lang3.StringUtils::isNotBlank)
                .map(String::trim)
                .toList();
        item.setImages(images.isEmpty() ? null : String.join(",", images));
        item.setCover(images.isEmpty() ? null : images.get(0));
    }

    private void normalizeItemParams(ShopItem item, List<ShopDeliveryAction> requestedActions) {
        if (item.getType() == null) {
            return;
        }
        if (item.getType().usesGitRepositoryAccessParams()) {
            GitRepositoryAccessParamValue param = ShopItemParamValue.gitRepositoryAccess(item.getParams());
            param.setDeliveryActions(resolveActions(item, requestedActions, param.getDeliveryActions()));
            if (param.deliversLicense()) {
                LicenseDeliveryParamValue licenseParam = param.getLicenseDelivery() == null
                        ? ShopItemParamValue.licenseDelivery(item.getParams(), item.getName())
                        : param.getLicenseDelivery();
                licenseParam.normalize(item.getName());
                validateLicenseDelivery(item, licenseParam);
                param.setLicenseDelivery(licenseParam);
            } else {
                param.setLicenseDelivery(null);
            }
            if (Boolean.TRUE.equals(item.getEnabled())
                    && param.getDeliveryActions().contains(ShopDeliveryAction.GIT_REPOSITORY_ACCESS)
                    && !param.hasRepository()) {
                throw new BusinessException("GIT_REPOSITORY_REQUIRED", "请输入有效的 Git 仓库地址");
            }
            item.setParams(param.toJSON());
            return;
        }
        if (item.getType() == ShopItem.Type.DIGITAL_DOWNLOAD) {
            DigitalDeliveryParamValue param = ShopItemParamValue.digitalDelivery(item.getParams());
            if (Boolean.TRUE.equals(item.getEnabled())
                    && item.getDeliveryMode() == ShopItem.DeliveryMode.AUTOMATIC
                    && !param.hasDeliveryContent()) {
                throw new BusinessException("DIGITAL_DELIVERY_REQUIRED", "请配置数字商品提货内容");
            }
            item.setParams(param.toJSON());
            return;
        }
        if (item.getType() == ShopItem.Type.LICENSE) {
            LicenseDeliveryParamValue param = ShopItemParamValue.licenseDelivery(item.getParams(), item.getName());
            param.setDeliveryActions(resolveActions(item, requestedActions, param.getDeliveryActions()));
            validateLicenseDelivery(item, param);
            item.setParams(param.toJSON());
        }
    }

    private List<ShopDeliveryAction> resolveActions(ShopItem item, List<ShopDeliveryAction> requestedActions,
                                                    List<ShopDeliveryAction> existingActions) {
        if (requestedActions != null) {
            return ShopItemDeliveryPlan.normalize(requestedActions, item.getType());
        }
        return ShopItemDeliveryPlan.normalize(existingActions, item.getType());
    }

    private void validateLicenseDelivery(ShopItem item, LicenseDeliveryParamValue param) {
        param.normalize(item.getName());
        if (Boolean.TRUE.equals(item.getEnabled())
                && item.getDeliveryMode() == ShopItem.DeliveryMode.AUTOMATIC
                && param.requiresAuthorizedOrigin()
                && !param.hasAuthorizedOrigin()) {
            throw new BusinessException("LICENSE_ORIGIN_REQUIRED", "请配置授权部署域名");
        }
        boolean hasWildcard = param.getAllowedOrigins().stream().anyMatch(origin -> origin.contains("*"));
        if (Boolean.TRUE.equals(item.getEnabled()) && param.requiresAuthorizedOrigin() && !param.isEnterprise()
                && (param.getAllowedOrigins().size() > 1 || hasWildcard || param.getMaxDeployments() > 1)) {
            throw new BusinessException("LICENSE_TIER_INVALID", "个人版和商业版仅允许单域名单部署，企业版才允许多域名或通配域名");
        }
        if (Boolean.TRUE.equals(item.getEnabled())
                && "personal".equals(param.getEdition())
                && Boolean.TRUE.equals(param.getCommercialUse())) {
            throw new BusinessException("LICENSE_PERSONAL_COMMERCIAL_FORBIDDEN", "个人授权禁止商业使用");
        }
    }

    private void normalizeItemPresentation(ShopItem item) {
        item.setHighlightStyle(StringUtils.trimToNull(item.getHighlightStyle()));
        item.setHighlightIcon(StringUtils.trimToNull(item.getHighlightIcon()));
    }

    private void normalizeItemCoupon(ShopItem item) {
        if (!Boolean.TRUE.equals(item.getDefaultCouponEnabled())) {
            item.setDefaultCouponEnabled(false);
            item.setDefaultCouponCode(null);
            return;
        }
        String code = StringUtils.upperCase(StringUtils.trimToNull(item.getDefaultCouponCode()));
        if (code == null) {
            throw new BusinessException("DEFAULT_COUPON_REQUIRED", "请填写默认优惠券编码");
        }
        item.setDefaultCouponCode(code);
    }

    private void normalizeSkuMode(ShopItem item, List<ShopItemSkuDto> skus, boolean requireSkuList) {
        if (item.getSkuMode() == null) {
            item.setSkuMode(ShopItem.SkuMode.SINGLE);
        }
        if (requireSkuList && item.getSkuMode() == ShopItem.SkuMode.MULTI && (skus == null || skus.isEmpty())) {
            throw new BusinessException("SKU_REQUIRED", "多SKU商品至少需要配置一个SKU");
        }
    }

    private Mono<ShopItem> syncItemContractsAndSkus(ShopItem item, List<Long> contractIds,
                                                    List<ShopItemSkuDto> skuDtos) {
        if (item.getSkuMode() != ShopItem.SkuMode.MULTI) {
            return shopItemSkuContractRepository.deleteByItemId(item.getId())
                    .then(shopItemSkuRepository.deleteByItemId(item.getId()))
                    .then(contractIds == null
                            ? Mono.just(item)
                            : shopContractService.updateItemContracts(item.getId(), contractIds).thenReturn(item));
        }
        if (skuDtos == null) {
            return Mono.just(item);
        }
        return shopContractService.updateItemContracts(item.getId(), List.of())
                .then(syncSkus(item, skuDtos))
                .thenReturn(item);
    }

    private Mono<Void> syncSkus(ShopItem item, List<ShopItemSkuDto> skuDtos) {
        List<ShopItemSkuDto> normalizedDtos = normalizeSkuDtos(item, skuDtos);
        return shopItemSkuRepository.findAllByItemIdOrderBySort(item.getId()).collectList()
                .flatMap(existing -> {
                    Map<Long, ShopItemSku> existingById = existing.stream()
                            .filter(sku -> sku.getId() != null)
                            .collect(java.util.stream.Collectors.toMap(ShopItemSku::getId, sku -> sku));
                    Set<Long> incomingIds = normalizedDtos.stream()
                            .map(ShopItemSkuDto::getId)
                            .filter(Objects::nonNull)
                            .collect(java.util.stream.Collectors.toSet());
                    Mono<Void> deletes = Flux.fromIterable(existing)
                            .filter(sku -> sku.getId() != null && !incomingIds.contains(sku.getId()))
                            .concatMap(sku -> shopItemSkuContractRepository.deleteBySkuId(sku.getId())
                                    .then(shopItemSkuRepository.delete(sku)))
                            .then();
                    Mono<Void> saves = Flux.fromIterable(normalizedDtos)
                            .concatMap(dto -> {
                                ShopItemSku sku = dto.getId() == null ? new ShopItemSku() : existingById.get(dto.getId());
                                if (sku == null) {
                                    sku = new ShopItemSku();
                                }
                                applySku(item, sku, dto);
                                return shopItemSkuRepository.save(sku)
                                        .flatMap(saved -> shopContractService.updateSkuContracts(item.getId(),
                                                saved.getId(), dto.getContractIds()).thenReturn(saved));
                            })
                            .then();
                    return deletes.then(saves);
                });
    }

    private List<ShopItemSkuDto> normalizeSkuDtos(ShopItem item, List<ShopItemSkuDto> source) {
        if (source == null || source.isEmpty()) {
            throw new BusinessException("SKU_REQUIRED", "多SKU商品至少需要配置一个SKU");
        }
        List<ShopItemSkuDto> normalized = new ArrayList<>();
        Set<String> codes = new LinkedHashSet<>();
        int index = 1;
        for (ShopItemSkuDto dto : source) {
            if (dto == null) {
                continue;
            }
            if (StringUtils.isBlank(dto.getName())) {
                throw new BusinessException("SKU_NAME_REQUIRED", "SKU名称不能为空");
            }
            if (dto.getPrice() == null || dto.getPrice().signum() <= 0) {
                throw new BusinessException("SKU_PRICE_REQUIRED", "SKU价格必须大于0");
            }
            String code = normalizeSkuCode(dto.getCode(), dto.getName(), index, codes);
            dto.setCode(code);
            dto.setSort(dto.getSort() == null ? index - 1 : dto.getSort());
            dto.setEnabled(dto.getEnabled() == null || dto.getEnabled());
            dto.setDefaultSelected(Boolean.TRUE.equals(dto.getDefaultSelected()));
            normalizeSkuCoupon(dto);
            dto.setUsdPrice(normalizeUsdPrice(dto.getUsdPrice()));
            ensureDonationUsdMinimum(dto.getType() == null ? item.getType() : dto.getType(), dto.getUsdPrice());
            normalized.add(dto);
            index++;
        }
        if (normalized.isEmpty()) {
            throw new BusinessException("SKU_REQUIRED", "多SKU商品至少需要配置一个SKU");
        }
        if (normalized.stream().noneMatch(dto -> Boolean.TRUE.equals(dto.getEnabled()))) {
            throw new BusinessException("SKU_ENABLED_REQUIRED", "多SKU商品至少需要上架一个SKU");
        }
        int defaultIndex = -1;
        for (int i = 0; i < normalized.size(); i++) {
            ShopItemSkuDto dto = normalized.get(i);
            if (Boolean.TRUE.equals(dto.getEnabled()) && Boolean.TRUE.equals(dto.getDefaultSelected())) {
                defaultIndex = i;
                break;
            }
        }
        if (defaultIndex < 0) {
            for (int i = 0; i < normalized.size(); i++) {
                if (Boolean.TRUE.equals(normalized.get(i).getEnabled())) {
                    defaultIndex = i;
                    break;
                }
            }
        }
        for (int i = 0; i < normalized.size(); i++) {
            normalized.get(i).setDefaultSelected(i == defaultIndex);
        }
        return normalized;
    }

    private String normalizeSkuCode(String rawCode, String name, int index, Set<String> used) {
        String base = StringUtils.upperCase(StringUtils.trimToNull(rawCode));
        if (base == null) {
            base = StringUtils.upperCase(StringUtils.trimToEmpty(name), Locale.ROOT)
                    .replaceAll("[^A-Z0-9]+", "-")
                    .replaceAll("(^-|-$)", "");
        }
        if (StringUtils.isBlank(base)) {
            base = "SKU-" + index;
        }
        String code = base;
        int suffix = 2;
        while (!used.add(code)) {
            code = base + "-" + suffix++;
        }
        return code;
    }

    private void normalizeSkuCoupon(ShopItemSkuDto dto) {
        if (dto.getDefaultCouponEnabled() == null) {
            dto.setDefaultCouponCode(null);
            return;
        }
        if (!Boolean.TRUE.equals(dto.getDefaultCouponEnabled())) {
            dto.setDefaultCouponCode(null);
            return;
        }
        String code = StringUtils.upperCase(StringUtils.trimToNull(dto.getDefaultCouponCode()));
        dto.setDefaultCouponCode(code);
    }

    private void applySku(ShopItem item, ShopItemSku sku, ShopItemSkuDto dto) {
        sku.setItemId(item.getId());
        sku.setCode(dto.getCode());
        sku.setName(StringUtils.trim(dto.getName()));
        sku.setDescription(StringUtils.trimToNull(dto.getDescription()));
        sku.setPrice(dto.getPrice());
        sku.setUsdPrice(dto.getUsdPrice());
        sku.setType(dto.getType() == null ? item.getType() : dto.getType());
        sku.setDeliveryMode(dto.getDeliveryMode());
        sku.setParams(dto.getParams());
        sku.setI18n(shopConvert.fromItemI18n(dto.getI18n()));
        sku.setTags(shopConvert.fromListString(dto.getTags()));
        sku.setSort(dto.getSort() == null ? 0 : dto.getSort());
        sku.setEnabled(dto.getEnabled() == null || dto.getEnabled());
        sku.setDefaultSelected(Boolean.TRUE.equals(dto.getDefaultSelected()));
        sku.setBuyCount(sku.getBuyCount() == null ? 0 : sku.getBuyCount());
        sku.setDefaultCouponEnabled(dto.getDefaultCouponEnabled());
        sku.setDefaultCouponCode(dto.getDefaultCouponCode());
        normalizeSkuDelivery(item, sku, dto.getDeliveryActions());
    }

    private void normalizeSkuDelivery(ShopItem item, ShopItemSku sku, List<ShopDeliveryAction> requestedActions) {
        ShopItem effective = new ShopItem();
        effective.setId(item.getId());
        effective.setName(StringUtils.defaultIfBlank(sku.getName(), item.getName()));
        effective.setPrice(sku.getPrice());
        effective.setType(sku.getType() == null ? item.getType() : sku.getType());
        effective.setDeliveryMode(sku.getDeliveryMode());
        effective.setParams(sku.getParams());
        effective.setEnabled(sku.getEnabled());
        normalizeDeliveryMode(effective, sku.getDeliveryMode() != null);
        normalizeItemParams(effective, requestedActions);
        sku.setType(effective.getType());
        sku.setDeliveryMode(effective.getDeliveryMode());
        sku.setParams(effective.getParams());
    }

    private void normalizeUsdPrice(ShopItem item) {
        item.setUsdPrice(normalizeUsdPrice(item.getUsdPrice()));
        ensureDonationUsdMinimum(item.getType(), item.getUsdPrice());
    }

    private void applyUsdPriceMode(ShopItem item, Boolean automatic) {
        if (Boolean.TRUE.equals(automatic)) {
            item.setUsdPrice(null);
            return;
        }
        if (Boolean.FALSE.equals(automatic) && item.getUsdPrice() == null) {
            throw new BusinessException("USD_PRICE_REQUIRED", "请填写美元价格");
        }
    }

    private java.math.BigDecimal normalizeUsdPrice(java.math.BigDecimal usdPrice) {
        if (usdPrice == null) {
            return null;
        }
        if (usdPrice.signum() <= 0) {
            throw new BusinessException("USD_PRICE_INVALID", "美元价格必须大于0");
        }
        return usdPrice.setScale(2, RoundingMode.HALF_UP);
    }

    private void ensureDonationUsdMinimum(ShopItem.Type type, java.math.BigDecimal usdPrice) {
        if ((type == ShopItem.Type.DONATION || type == ShopItem.Type.GIT_REPOSITORY_DONATION_ACCESS)
                && usdPrice != null && usdPrice.compareTo(java.math.BigDecimal.ONE) < 0) {
            throw new BusinessException("USD_DONATION_PRICE_TOO_LOW", "美元打赏起始金额不能低于 $1");
        }
    }

    private void normalizeDeliveryMode(ShopItem item, boolean explicitMode) {
        if (item.getType() == null) {
            item.setType(ShopItem.Type.SERVICE_PACKAGE);
        }
        if (item.getType().requiresAutomaticDelivery()) {
            item.setDeliveryMode(item.getType().getDefaultDeliveryMode());
            return;
        }
        ShopItem.DeliveryMode deliveryMode = item.getDeliveryMode();
        if (deliveryMode == null) {
            item.setDeliveryMode(item.getType().getDefaultDeliveryMode());
            return;
        }
        if (!item.getType().supportsDeliveryMode(deliveryMode)) {
            if (explicitMode) {
                throw new BusinessException("DELIVERY_MODE_INVALID", "该商品类型不支持当前交付方式");
            }
            item.setDeliveryMode(item.getType().getDefaultDeliveryMode());
        }
    }

    @Override
    @Transactional
    public Mono<Void> deleteItem(Long id) {
        return shopItemRepository.findById(id)
                .switchIfEmpty(Mono.error(new BusinessException("ITEM_NOT_FOUND", "商品不存在")))
                .flatMap(item -> shopItemRepository.deleteById(item.getId()));
    }
}

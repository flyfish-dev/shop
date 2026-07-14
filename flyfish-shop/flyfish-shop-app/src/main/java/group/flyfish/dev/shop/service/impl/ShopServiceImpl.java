package group.flyfish.dev.shop.service.impl;

import group.flyfish.dev.shop.convert.ShopConvert;
import group.flyfish.dev.shop.converter.ShopItemDeliveryPlan;
import group.flyfish.dev.shop.domain.bo.DefaultShop;
import group.flyfish.dev.shop.domain.po.Shop;
import group.flyfish.dev.shop.domain.po.ShopItem;
import group.flyfish.dev.shop.domain.po.ShopItemSku;
import group.flyfish.dev.shop.domain.qo.ShopItemGroupListQo;
import group.flyfish.dev.shop.domain.qo.ShopItemListQo;
import group.flyfish.dev.shop.domain.vo.ShopCouponApplyVo;
import group.flyfish.dev.shop.domain.vo.ShopItemDetailVo;
import group.flyfish.dev.shop.domain.vo.ShopItemGroupListVo;
import group.flyfish.dev.shop.domain.vo.ShopItemListVo;
import group.flyfish.dev.shop.domain.vo.ShopItemSkuVo;
import group.flyfish.dev.shop.repository.ShopItemGroupRepository;
import group.flyfish.dev.shop.repository.ShopItemRepository;
import group.flyfish.dev.shop.repository.ShopItemSkuRepository;
import group.flyfish.dev.shop.repository.ShopRepository;
import group.flyfish.dev.shop.pricing.ShopPricingService;
import group.flyfish.dev.shop.service.ShopContractService;
import group.flyfish.dev.shop.service.ShopCouponService;
import group.flyfish.dev.shop.service.ShopItemViewStatService;
import group.flyfish.dev.shop.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 店铺服务
 *
 * @author wangyu
 */
@Service
@RequiredArgsConstructor
public class ShopServiceImpl implements ShopService {

    private final ShopRepository shopRepository;
    private final ShopItemGroupRepository shopItemGroupRepository;
    private final ShopItemRepository shopItemRepository;
    private final ShopItemSkuRepository shopItemSkuRepository;
    private final ShopConvert shopConvert;
    private final ShopContractService shopContractService;
    private final ShopCouponService shopCouponService;
    private final ShopItemViewStatService shopItemViewStatService;
    private final ShopPricingService shopPricingService;

    /**
     * 获取当前店铺，如果没有店铺，创建飞鱼小铺
     *
     * @return 结果
     */
    @Override
    public Mono<Shop> getCurrentShop() {
        return shopRepository.findAll().next().defaultIfEmpty(DefaultShop.defaultShop());
    }

    /**
     * 获取商品分组列表
     *
     * @param shopId 店铺id
     * @return 结果
     */
    @Override
    public Flux<ShopItemGroupListVo> getItemGroupList(ShopItemGroupListQo qo) {
        return Mono.justOrEmpty(qo)
                .switchIfEmpty(Mono.defer(() -> getCurrentShop().map(shop -> {
                    ShopItemGroupListQo alternative = new ShopItemGroupListQo();
                    alternative.setShopId(shop.getId());
                    return alternative;
                })))
                .flatMapMany(shopItemGroupRepository::findAll)
                .map(shopConvert::convert);
    }

    /**
     * 获取商品列表
     *
     * @param qo 查询
     * @return 结果
     */
    @Override
    public Mono<Page<ShopItemListVo>> getItemList(ShopItemListQo qo) {
        return shopItemRepository.findAll(qo, qo.getPageable())
                .flatMap(page -> Flux.fromIterable(page.getContent())
                        .concatMap(item -> enrichListItem(item, shopConvert.toItemList(item)))
                        .collectList()
                        .map(list -> new PageImpl<>(list, page.getPageable(), page.getTotalElements())));
    }

    /**
     * 查询商品详情
     *
     * @param id 商品id
     * @return 结果
     */
    @Override
    public Mono<ShopItemDetailVo> getItemDetail(Long id) {
        return shopItemRepository.findById(id)
                .flatMap(item -> {
                    ShopItemDetailVo vo = shopConvert.toItemDetail(item);
                    applyUsdPricing(vo, item);
                    return Mono.zip(
                                    shopContractService.getItemContractIds(id),
                                    shopContractService.hasActiveContracts(id),
                                    defaultCouponPreview(vo.getDefaultCouponEnabled(), vo.getDefaultCouponCode(), vo.getPrice())
                                            .map(Optional::of)
                                            .defaultIfEmpty(Optional.empty()),
                                    shopItemViewStatService.recordAndGetViewCount(id),
                                    skuVos(item, true).collectList())
                            .map(tuple -> {
                                vo.setContractIds(tuple.getT1());
                                List<ShopItemSkuVo> skus = tuple.getT5();
                                applySkuDetail(vo, item, skus);
                                vo.setContractRequired(isMultiSku(item)
                                        ? skus.stream().anyMatch(sku -> Boolean.TRUE.equals(sku.getContractRequired()))
                                        : tuple.getT2());
                                tuple.getT3().ifPresent(vo::setDefaultCouponPreview);
                                vo.setViewCount(tuple.getT4());
                                return vo;
                            });
                });
    }

    private Mono<ShopItemListVo> enrichListItem(ShopItem item, ShopItemListVo vo) {
        applyUsdPricing(vo, item);
        return Mono.zip(
                        shopContractService.hasActiveContracts(Long.valueOf(vo.getId())),
                        defaultCouponPreview(vo.getDefaultCouponEnabled(), vo.getDefaultCouponCode(), money(vo.getPrice()))
                                .map(Optional::of)
                                .defaultIfEmpty(Optional.empty()),
                        skuVos(item, false).collectList())
                .map(tuple -> {
                    List<ShopItemSkuVo> skus = tuple.getT3();
                    applySkuList(vo, item, skus);
                    vo.setContractRequired(isMultiSku(item)
                            ? skus.stream().anyMatch(sku -> Boolean.TRUE.equals(sku.getContractRequired()))
                            : tuple.getT1());
                    tuple.getT2().ifPresent(vo::setDefaultCouponPreview);
                    return vo;
                });
    }

    private void applySkuList(ShopItemListVo vo, ShopItem item, List<ShopItemSkuVo> skus) {
        vo.setSkuMode(resolveSkuMode(item).name());
        vo.setMultiSku(isMultiSku(item));
        vo.setSkuCount(skus.size());
        if (!isMultiSku(item) || skus.isEmpty()) {
            return;
        }
        ShopItemSkuVo defaultSku = defaultSku(skus);
        vo.setDefaultSku(defaultSku);
        BigDecimal min = skus.stream().map(ShopItemSkuVo::getPrice).min(Comparator.naturalOrder()).orElse(item.getPrice());
        BigDecimal max = skus.stream().map(ShopItemSkuVo::getPrice).max(Comparator.naturalOrder()).orElse(item.getPrice());
        BigDecimal minUsd = skus.stream().map(ShopItemSkuVo::getEffectiveUsdPrice)
                .filter(java.util.Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(shopPricingService.effectiveUsdPrice(item));
        BigDecimal maxUsd = skus.stream().map(ShopItemSkuVo::getEffectiveUsdPrice)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(minUsd);
        vo.setMinPrice(min == null ? null : min.toPlainString());
        vo.setMaxPrice(max == null ? null : max.toPlainString());
        vo.setPrice(min == null ? vo.getPrice() : min.toPlainString());
        vo.setMinEffectiveUsdPrice(moneyText(minUsd));
        vo.setMaxEffectiveUsdPrice(moneyText(maxUsd));
        vo.setEffectiveUsdPrice(moneyText(minUsd));
        if (defaultSku != null && defaultSku.getDefaultCouponPreview() != null) {
            vo.setDefaultCouponPreview(defaultSku.getDefaultCouponPreview());
        }
    }

    private void applySkuDetail(ShopItemDetailVo vo, ShopItem item, List<ShopItemSkuVo> skus) {
        vo.setSkuMode(resolveSkuMode(item).name());
        vo.setMultiSku(isMultiSku(item));
        vo.setSkus(skus);
        if (!isMultiSku(item) || skus.isEmpty()) {
            return;
        }
        ShopItemSkuVo defaultSku = defaultSku(skus);
        vo.setDefaultSku(defaultSku);
        if (defaultSku != null && defaultSku.getDefaultCouponPreview() != null) {
            vo.setDefaultCouponPreview(defaultSku.getDefaultCouponPreview());
        }
    }

    private Flux<ShopItemSkuVo> skuVos(ShopItem item, boolean includeDisabled) {
        if (!isMultiSku(item)) {
            return Flux.empty();
        }
        return (includeDisabled
                ? shopItemSkuRepository.findAllByItemIdOrderBySort(item.getId())
                : shopItemSkuRepository.findEnabledByItemIdOrderBySort(item.getId()))
                .concatMap(sku -> toSkuVo(item, sku));
    }

    private Mono<ShopItemSkuVo> toSkuVo(ShopItem item, ShopItemSku sku) {
        ShopItemSkuVo vo = new ShopItemSkuVo();
        vo.setId(sku.getId());
        vo.setItemId(sku.getItemId());
        vo.setCode(sku.getCode());
        vo.setName(sku.getName());
        vo.setDescription(sku.getDescription());
        vo.setPrice(sku.getPrice());
        vo.setUsdPrice(sku.getUsdPrice());
        vo.setEffectiveUsdPrice(shopPricingService.effectiveUsdPrice(effectiveItem(item, sku)));
        vo.setCnyPerUsd(shopPricingService.cnyPerUsd());
        vo.setType(sku.getType() == null ? null : sku.getType().name());
        vo.setTypeName(sku.getType() == null ? null : sku.getType().getTitle());
        ShopItem.DeliveryMode deliveryMode = resolveDeliveryMode(sku);
        vo.setDeliveryMode(deliveryMode.name());
        vo.setDeliveryModeName(deliveryMode.getTitle());
        vo.setDeliveryActions(ShopItemDeliveryPlan.actionNames(effectiveItem(item, sku)));
        vo.setParams(sku.getParams());
        vo.setI18n(shopConvert.toItemI18n(sku.getI18n()));
        vo.setTags(shopConvert.toListString(sku.getTags()));
        vo.setSort(sku.getSort());
        vo.setEnabled(sku.getEnabled());
        vo.setDefaultSelected(sku.getDefaultSelected());
        vo.setBuyCount(sku.getBuyCount());
        vo.setDefaultCouponEnabled(effectiveCouponEnabled(item, sku));
        vo.setDefaultCouponCode(effectiveCouponCode(item, sku));
        return Mono.zip(
                        shopContractService.getSkuContractIds(sku.getId()),
                        shopContractService.hasActiveContracts(item.getId(), sku.getId()),
                        defaultCouponPreview(vo.getDefaultCouponEnabled(), vo.getDefaultCouponCode(), sku.getPrice())
                                .map(Optional::of)
                                .defaultIfEmpty(Optional.empty()))
                .map(tuple -> {
                    vo.setContractIds(tuple.getT1());
                    vo.setContractRequired(tuple.getT2());
                    tuple.getT3().ifPresent(vo::setDefaultCouponPreview);
                    return vo;
                });
    }

    private ShopItem effectiveItem(ShopItem item, ShopItemSku sku) {
        ShopItem effective = new ShopItem();
        effective.setId(item.getId());
        effective.setShopId(item.getShopId());
        effective.setName(sku.getName());
        effective.setPrice(sku.getPrice());
        effective.setUsdPrice(sku.getUsdPrice());
        effective.setType(sku.getType());
        effective.setDeliveryMode(sku.getDeliveryMode());
        effective.setParams(sku.getParams());
        effective.setEnabled(sku.getEnabled());
        return effective;
    }

    private ShopItemSkuVo defaultSku(List<ShopItemSkuVo> skus) {
        return skus.stream()
                .filter(sku -> Boolean.TRUE.equals(sku.getDefaultSelected()))
                .findFirst()
                .orElseGet(() -> skus.isEmpty() ? null : skus.getFirst());
    }

    private ShopItem.SkuMode resolveSkuMode(ShopItem item) {
        return item.getSkuMode() == null ? ShopItem.SkuMode.SINGLE : item.getSkuMode();
    }

    private boolean isMultiSku(ShopItem item) {
        return resolveSkuMode(item) == ShopItem.SkuMode.MULTI;
    }

    private ShopItem.DeliveryMode resolveDeliveryMode(ShopItemSku sku) {
        if (sku.getType() != null) {
            return sku.getType().normalizeDeliveryMode(sku.getDeliveryMode());
        }
        if (sku.getDeliveryMode() != null) {
            return sku.getDeliveryMode();
        }
        return ShopItem.DeliveryMode.MANUAL;
    }

    private Boolean effectiveCouponEnabled(ShopItem item, ShopItemSku sku) {
        return sku.getDefaultCouponEnabled() == null ? item.getDefaultCouponEnabled() : sku.getDefaultCouponEnabled();
    }

    private String effectiveCouponCode(ShopItem item, ShopItemSku sku) {
        return sku.getDefaultCouponEnabled() == null ? item.getDefaultCouponCode() : sku.getDefaultCouponCode();
    }

    private Mono<ShopCouponApplyVo> defaultCouponPreview(Boolean enabled, String couponCode, BigDecimal price) {
        if (!Boolean.TRUE.equals(enabled) || StringUtils.isBlank(couponCode) || price == null) {
            return Mono.empty();
        }
        return shopCouponService.applyCoupon(couponCode, price)
                .map(ShopCouponApplyVo::new)
                .onErrorResume(e -> Mono.empty());
    }

    private BigDecimal money(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void applyUsdPricing(ShopItemListVo vo, ShopItem item) {
        vo.setEffectiveUsdPrice(moneyText(shopPricingService.effectiveUsdPrice(item)));
        vo.setCnyPerUsd(shopPricingService.cnyPerUsd().toPlainString());
    }

    private void applyUsdPricing(ShopItemDetailVo vo, ShopItem item) {
        vo.setEffectiveUsdPrice(shopPricingService.effectiveUsdPrice(item));
        vo.setCnyPerUsd(shopPricingService.cnyPerUsd());
    }

    private String moneyText(BigDecimal value) {
        return value == null ? null : value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}

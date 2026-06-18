package group.flyfish.dev.shop.service.impl;

import group.flyfish.dev.shop.convert.ShopConvert;
import group.flyfish.dev.shop.domain.bo.DefaultShop;
import group.flyfish.dev.shop.domain.po.Shop;
import group.flyfish.dev.shop.domain.qo.ShopItemGroupListQo;
import group.flyfish.dev.shop.domain.qo.ShopItemListQo;
import group.flyfish.dev.shop.domain.vo.ShopItemDetailVo;
import group.flyfish.dev.shop.domain.vo.ShopItemGroupListVo;
import group.flyfish.dev.shop.domain.vo.ShopItemListVo;
import group.flyfish.dev.shop.domain.vo.ShopCouponApplyVo;
import group.flyfish.dev.shop.repository.ShopItemGroupRepository;
import group.flyfish.dev.shop.repository.ShopItemRepository;
import group.flyfish.dev.shop.repository.ShopRepository;
import group.flyfish.dev.shop.service.ShopContractService;
import group.flyfish.dev.shop.service.ShopCouponService;
import group.flyfish.dev.shop.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
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
    private final ShopConvert shopConvert;
    private final ShopContractService shopContractService;
    private final ShopCouponService shopCouponService;

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
                        .concatMap(item -> enrichListItem(shopConvert.toItemList(item)))
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
                .map(shopConvert::toItemDetail)
                .flatMap(vo -> Mono.zip(
                                shopContractService.getItemContractIds(id),
                                shopContractService.hasActiveContracts(id),
                                defaultCouponPreview(vo.getDefaultCouponEnabled(), vo.getDefaultCouponCode(), vo.getPrice())
                                        .map(Optional::of)
                                        .defaultIfEmpty(Optional.empty()))
                        .map(tuple -> {
                            vo.setContractIds(tuple.getT1());
                            vo.setContractRequired(tuple.getT2());
                            tuple.getT3().ifPresent(vo::setDefaultCouponPreview);
                            return vo;
                        }));
    }

    private Mono<ShopItemListVo> enrichListItem(ShopItemListVo vo) {
        return Mono.zip(
                        shopContractService.hasActiveContracts(Long.valueOf(vo.getId())),
                        defaultCouponPreview(vo.getDefaultCouponEnabled(), vo.getDefaultCouponCode(), money(vo.getPrice()))
                                .map(Optional::of)
                                .defaultIfEmpty(Optional.empty()))
                .map(tuple -> {
                    vo.setContractRequired(tuple.getT1());
                    tuple.getT2().ifPresent(vo::setDefaultCouponPreview);
                    return vo;
                });
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
}

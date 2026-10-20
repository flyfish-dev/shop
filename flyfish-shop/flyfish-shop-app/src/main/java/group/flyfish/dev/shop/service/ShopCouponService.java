package group.flyfish.dev.shop.service;

import group.flyfish.dev.shop.domain.dto.ShopCouponCreateDto;
import group.flyfish.dev.shop.domain.dto.ShopCouponUpdateDto;
import group.flyfish.dev.shop.domain.vo.ShopCouponVo;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

public interface ShopCouponService {

    Flux<ShopCouponVo> listCoupons();

    Mono<ShopCouponVo> createCoupon(ShopCouponCreateDto dto);

    Mono<ShopCouponVo> updateCoupon(Long id, ShopCouponUpdateDto dto);

    Mono<Void> deleteCoupon(Long id);

    Mono<CouponDiscount> applyCoupon(String code, BigDecimal originalAmount);

    /**
     * 按商品上下文试算优惠券。限定商品或SKU的券必须通过该入口校验作用域。
     */
    Mono<CouponDiscount> applyCoupon(String code, BigDecimal originalAmount, Long itemId, Long skuId);

    Mono<Void> markUsed(String code);

    /**
     * 在零元订单事务中原子核销优惠券。
     *
     * @param code 已通过试算的优惠券编码
     * @return 核销完成信号；优惠券状态变化或库存耗尽时返回业务异常
     */
    Mono<Void> claimForZeroAmountOrder(String code);

    Mono<OfferLinkDiscount> applyOfferLink(String token, BigDecimal originalAmount, Long itemId, Long skuId,
                                           String currency, Long buyerId);

    Mono<Void> reserveOfferLink(Long offerLinkId, String orderNo, Long buyerId);

    Mono<Void> consumeOfferLink(Long offerLinkId, String orderNo, Long buyerId);

    Mono<Void> releaseOfferLink(String orderNo);

    Mono<Void> releaseClosedOfferLinkReservations();
}

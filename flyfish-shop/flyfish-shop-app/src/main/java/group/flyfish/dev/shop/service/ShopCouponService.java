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

    Mono<Void> markUsed(String code);
}

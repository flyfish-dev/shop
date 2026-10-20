package group.flyfish.dev.shop.service.impl;

import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.shop.domain.po.ShopCoupon;
import group.flyfish.dev.shop.repository.ShopCouponRepository;
import group.flyfish.dev.shop.service.CouponDiscount;
import group.flyfish.dev.shop.service.ShopOfferLinkService;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShopCouponServiceImplTest {

    @Test
    void discountCouponUsesMaxDiscountAmountAsBusinessCap() {
        ShopCouponRepository repository = mock(ShopCouponRepository.class);
        ShopCoupon coupon = usableCoupon("MAX80", ShopCoupon.Type.DISCOUNT, "7.00");
        coupon.setMaxDiscountAmount(new BigDecimal("80.00"));
        when(repository.findByCode("MAX80")).thenReturn(Mono.just(coupon));

        ShopCouponServiceImpl service = service(repository);

        StepVerifier.create(service.applyCoupon("max80", new BigDecimal("1000.00")))
                .assertNext(discount -> assertDiscount(discount, "MAX80", "1000.00", "80.00", "920.00"))
                .verifyComplete();
    }

    @Test
    void reductionCouponIgnoresMaxDiscountAmount() {
        ShopCouponRepository repository = mock(ShopCouponRepository.class);
        ShopCoupon coupon = usableCoupon("CUT50", ShopCoupon.Type.REDUCTION, "50.00");
        coupon.setMaxDiscountAmount(new BigDecimal("10.00"));
        when(repository.findByCode("CUT50")).thenReturn(Mono.just(coupon));

        ShopCouponServiceImpl service = service(repository);

        StepVerifier.create(service.applyCoupon("CUT50", new BigDecimal("200.00")))
                .assertNext(discount -> assertDiscount(discount, "CUT50", "200.00", "50.00", "150.00"))
                .verifyComplete();
    }

    @Test
    void reductionCouponCanCoverTheFullOrderAmount() {
        ShopCouponRepository repository = mock(ShopCouponRepository.class);
        ShopCoupon coupon = usableCoupon("FREE50", ShopCoupon.Type.REDUCTION, "50.00");
        when(repository.findByCode("FREE50")).thenReturn(Mono.just(coupon));

        ShopCouponServiceImpl service = service(repository);

        StepVerifier.create(service.applyCoupon("FREE50", new BigDecimal("50.00")))
                .assertNext(discount -> assertDiscount(discount, "FREE50", "50.00", "50.00", "0.00"))
                .verifyComplete();
    }

    @Test
    void scopedCouponOnlyAppliesToConfiguredItemAndSku() {
        ShopCouponRepository repository = mock(ShopCouponRepository.class);
        ShopCoupon coupon = usableCoupon("DWGLAUNCH400", ShopCoupon.Type.REDUCTION, "400.00");
        coupon.setThresholdAmount(new BigDecimal("1299.00"));
        coupon.setItemId(1013L);
        coupon.setSkuId(17L);
        when(repository.findByCode("DWGLAUNCH400")).thenReturn(Mono.just(coupon));

        ShopCouponServiceImpl service = service(repository);

        StepVerifier.create(service.applyCoupon("DWGLAUNCH400", new BigDecimal("1299.00"), 1013L, 17L))
                .assertNext(discount -> assertDiscount(
                        discount, "DWGLAUNCH400", "1299.00", "400.00", "899.00"))
                .verifyComplete();

        StepVerifier.create(service.applyCoupon("DWGLAUNCH400", new BigDecimal("1299.00"), 1014L, 17L))
                .expectErrorSatisfies(error -> assertEquals(
                        "COUPON_ITEM_MISMATCH", ((BusinessException) error).getCode()))
                .verify();

        StepVerifier.create(service.applyCoupon("DWGLAUNCH400", new BigDecimal("1299.00"), 1013L, 18L))
                .expectErrorSatisfies(error -> assertEquals(
                        "COUPON_SKU_MISMATCH", ((BusinessException) error).getCode()))
                .verify();
    }

    @Test
    void zeroAmountCouponClaimFailsWhenAtomicUpdateDoesNotMatch() {
        ShopCouponRepository repository = mock(ShopCouponRepository.class);
        when(repository.claimAvailableUse("FREE50")).thenReturn(Mono.just(0));

        ShopCouponServiceImpl service = service(repository);

        StepVerifier.create(service.claimForZeroAmountOrder("free50"))
                .expectErrorSatisfies(error -> {
                    BusinessException exception = (BusinessException) error;
                    assertEquals("COUPON_UNAVAILABLE", exception.getCode());
                })
                .verify();
    }

    @Test
    void offerLinkBackingCouponCannotBeAppliedAsARegularCoupon() {
        ShopCouponRepository repository = mock(ShopCouponRepository.class);
        ShopCoupon coupon = usableCoupon("FFLINKSECRET", ShopCoupon.Type.REDUCTION, "100.00");
        coupon.setSource(ShopCoupon.Source.OFFER_LINK);
        when(repository.findByCode("FFLINKSECRET")).thenReturn(Mono.just(coupon));

        StepVerifier.create(service(repository).applyCoupon("FFLINKSECRET", new BigDecimal("100.00")))
                .expectErrorSatisfies(error -> assertEquals(
                        "COUPON_NOT_FOUND", ((BusinessException) error).getCode()))
                .verify();
    }

    private ShopCoupon usableCoupon(String code, ShopCoupon.Type type, String discountValue) {
        ShopCoupon coupon = new ShopCoupon();
        coupon.setCode(code);
        coupon.setName(code);
        coupon.setType(type);
        coupon.setDiscountValue(new BigDecimal(discountValue));
        coupon.setThresholdAmount(BigDecimal.ZERO);
        coupon.setMaxDiscountAmount(BigDecimal.ZERO);
        coupon.setTotalCount(0);
        coupon.setUsedCount(0);
        coupon.setEnabled(true);
        coupon.setStartTime(LocalDateTime.now().minusDays(1));
        coupon.setEndTime(LocalDateTime.now().plusDays(1));
        return coupon;
    }

    private ShopCouponServiceImpl service(ShopCouponRepository repository) {
        return new ShopCouponServiceImpl(repository, mock(ShopOfferLinkService.class));
    }

    private void assertDiscount(CouponDiscount discount, String code, String original,
                                String discountAmount, String payable) {
        assertEquals(code, discount.code());
        assertEquals(0, new BigDecimal(original).compareTo(discount.originalAmount()));
        assertEquals(0, new BigDecimal(discountAmount).compareTo(discount.discountAmount()));
        assertEquals(0, new BigDecimal(payable).compareTo(discount.payableAmount()));
    }
}

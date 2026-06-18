package group.flyfish.dev.shop.service.impl;

import group.flyfish.dev.shop.domain.po.ShopCoupon;
import group.flyfish.dev.shop.repository.ShopCouponRepository;
import group.flyfish.dev.shop.service.CouponDiscount;
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

        ShopCouponServiceImpl service = new ShopCouponServiceImpl(repository);

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

        ShopCouponServiceImpl service = new ShopCouponServiceImpl(repository);

        StepVerifier.create(service.applyCoupon("CUT50", new BigDecimal("200.00")))
                .assertNext(discount -> assertDiscount(discount, "CUT50", "200.00", "50.00", "150.00"))
                .verifyComplete();
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

    private void assertDiscount(CouponDiscount discount, String code, String original,
                                String discountAmount, String payable) {
        assertEquals(code, discount.code());
        assertEquals(0, new BigDecimal(original).compareTo(discount.originalAmount()));
        assertEquals(0, new BigDecimal(discountAmount).compareTo(discount.discountAmount()));
        assertEquals(0, new BigDecimal(payable).compareTo(discount.payableAmount()));
    }
}

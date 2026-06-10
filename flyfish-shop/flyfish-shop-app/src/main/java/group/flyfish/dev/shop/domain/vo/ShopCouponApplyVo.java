package group.flyfish.dev.shop.domain.vo;

import group.flyfish.dev.shop.service.CouponDiscount;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 优惠券试算结果。
 */
@Data
public class ShopCouponApplyVo {

    private String couponCode;

    private BigDecimal originalAmount;

    private BigDecimal discountAmount;

    private BigDecimal payableAmount;

    public ShopCouponApplyVo(CouponDiscount discount) {
        this.couponCode = discount.code();
        this.originalAmount = discount.originalAmount();
        this.discountAmount = discount.discountAmount();
        this.payableAmount = discount.payableAmount();
    }
}

package group.flyfish.dev.shop.service;

import java.math.BigDecimal;

/**
 * 优惠券核算结果。
 *
 * @param code           优惠券编码；未使用优惠券时为空
 * @param originalAmount 原始订单金额
 * @param discountAmount 优惠金额
 * @param payableAmount  实付金额
 */
public record CouponDiscount(String code, BigDecimal originalAmount,
                             BigDecimal discountAmount, BigDecimal payableAmount) {
}

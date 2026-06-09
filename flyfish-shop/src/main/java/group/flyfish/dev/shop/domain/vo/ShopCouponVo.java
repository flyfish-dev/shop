package group.flyfish.dev.shop.domain.vo;

import group.flyfish.dev.shop.domain.po.ShopCoupon;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券展示数据。
 */
@Data
public class ShopCouponVo {

    private Long id;

    private String code;

    private String name;

    private ShopCoupon.Type type;

    private String typeName;

    private BigDecimal discountValue;

    private BigDecimal thresholdAmount;

    private Integer totalCount;

    private Integer usedCount;

    private Integer remainingCount;

    private Boolean enabled;

    private Boolean validNow;

    private String displayRule;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public ShopCouponVo(ShopCoupon coupon) {
        this.id = coupon.getId();
        this.code = coupon.getCode();
        this.name = coupon.getName();
        this.type = coupon.getType();
        this.typeName = resolveTypeName(coupon.getType());
        this.discountValue = coupon.getDiscountValue();
        this.thresholdAmount = coupon.getThresholdAmount();
        this.totalCount = coupon.getTotalCount();
        this.usedCount = coupon.getUsedCount();
        this.remainingCount = resolveRemainingCount(coupon.getTotalCount(), coupon.getUsedCount());
        this.enabled = coupon.getEnabled();
        this.validNow = isValidNow(coupon);
        this.displayRule = resolveDisplayRule(coupon);
        this.startTime = coupon.getStartTime();
        this.endTime = coupon.getEndTime();
        this.createTime = coupon.getCreateTime();
        this.updateTime = coupon.getUpdateTime();
    }

    private String resolveTypeName(ShopCoupon.Type type) {
        if (type == ShopCoupon.Type.DISCOUNT) {
            return "折扣";
        }
        if (type == ShopCoupon.Type.REDUCTION) {
            return "满减";
        }
        return "优惠";
    }

    private Integer resolveRemainingCount(Integer totalCount, Integer usedCount) {
        if (totalCount == null || totalCount <= 0) {
            return null;
        }
        return Math.max(0, totalCount - (usedCount == null ? 0 : usedCount));
    }

    private boolean isValidNow(ShopCoupon coupon) {
        LocalDateTime now = LocalDateTime.now();
        boolean started = coupon.getStartTime() == null || !now.isBefore(coupon.getStartTime());
        boolean notExpired = coupon.getEndTime() == null || !now.isAfter(coupon.getEndTime());
        boolean hasQuota = coupon.getTotalCount() == null || coupon.getTotalCount() <= 0
                || (coupon.getUsedCount() == null ? 0 : coupon.getUsedCount()) < coupon.getTotalCount();
        return Boolean.TRUE.equals(coupon.getEnabled()) && started && notExpired && hasQuota;
    }

    private String resolveDisplayRule(ShopCoupon coupon) {
        BigDecimal threshold = coupon.getThresholdAmount() == null ? BigDecimal.ZERO : coupon.getThresholdAmount();
        BigDecimal discount = coupon.getDiscountValue() == null ? BigDecimal.ZERO : coupon.getDiscountValue();
        String prefix = threshold.signum() > 0 ? "满" + threshold.stripTrailingZeros().toPlainString() : "无门槛";
        if (coupon.getType() == ShopCoupon.Type.DISCOUNT) {
            return prefix + "享" + discount.stripTrailingZeros().toPlainString() + "折";
        }
        return prefix + "减" + discount.stripTrailingZeros().toPlainString();
    }
}

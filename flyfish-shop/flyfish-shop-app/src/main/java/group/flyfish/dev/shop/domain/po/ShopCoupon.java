package group.flyfish.dev.shop.domain.po;

import group.flyfish.dev.annotations.data.Property;
import group.flyfish.dev.common.base.reactive.AuditDomain;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 店铺优惠券。
 */
@Getter
@Setter
@Table("shop_coupon")
public class ShopCoupon extends AuditDomain {

    @Property("优惠券编码")
    private String code;

    @Property("优惠券名称")
    private String name;

    @Property("优惠类型")
    private Type type;

    @Property("优惠值")
    @Column("discount_value")
    private BigDecimal discountValue;

    @Property("使用门槛")
    @Column("threshold_amount")
    private BigDecimal thresholdAmount;

    @Property("最高减免金额")
    @Column("max_discount_amount")
    private BigDecimal maxDiscountAmount;

    @Property("发放总量")
    @Column("total_count")
    private Integer totalCount;

    @Property("已使用数量")
    @Column("used_count")
    private Integer usedCount;

    @Property("启用状态")
    private Boolean enabled;

    @Property("开始时间")
    @Column("start_time")
    private LocalDateTime startTime;

    @Property("结束时间")
    @Column("end_time")
    private LocalDateTime endTime;

    public enum Type {

        DISCOUNT,
        REDUCTION
    }
}

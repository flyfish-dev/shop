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
 * 店铺订单
 *
 * @author wangyu
 */
@Getter
@Setter
@Table("shop_order")
public class ShopOrder extends AuditDomain {

    @Property("订单号")
    @Column("order_no")
    private String orderNo;

    @Property("商品id")
    @Column("item_id")
    private Long itemId;

    @Property("店铺id")
    @Column("shop_id")
    private Long shopId;

    @Property("购买用户id")
    @Column("buyer_id")
    private Long buyerId;

    @Property("购买数量")
    private Integer count;

    @Property("商品属性")
    private String properties;

    @Property("订单金额")
    private BigDecimal amount;

    @Property("原始金额")
    @Column("original_amount")
    private BigDecimal originalAmount;

    @Property("优惠金额")
    @Column("discount_amount")
    private BigDecimal discountAmount;

    @Property("优惠券编码")
    @Column("coupon_code")
    private String couponCode;

    @Property("外部订单号")
    @Column("outer_no")
    private String outerNo;

    @Property("支付提供方")
    @Column("payment_provider")
    private String paymentProvider;

    @Property("支付流水号")
    @Column("transaction_code")
    private String transactionCode;

    @Property("支付时间")
    @Column("paid_time")
    private LocalDateTime paidTime;

    @Property("过期时间")
    @Column("expire_time")
    private LocalDateTime expireTime;

    @Property("订单状态")
    private Status status;

    @Property("交付状态")
    @Column("delivery_status")
    private DeliveryStatus deliveryStatus;

    @Property("交付信息")
    @Column("delivery_message")
    private String deliveryMessage;

    /**
     * 订单状态
     */
    public enum Status {

        PENDING, PAYING, PAID, DELIVERED, FAILED, CLOSED
    }

    /**
     * 交付状态
     */
    public enum DeliveryStatus {

        WAITING, PROCESSING, SUCCESS, FAILED, SKIPPED
    }
}

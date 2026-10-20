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

    @Column("sales_channel")
    private group.flyfish.dev.xianyu.api.SalesChannel salesChannel;

    @Column("channel_order_key")
    private String channelOrderKey;

    @Column("refund_state")
    private String refundState;

    @Column("refund_amount")
    private BigDecimal refundAmount;

    @Property("订单号")
    @Column("order_no")
    private String orderNo;

    @Property("商品id")
    @Column("item_id")
    private Long itemId;

    @Property("SKU id")
    @Column("sku_id")
    private Long skuId;

    @Property("SKU编码快照")
    @Column("sku_code")
    private String skuCode;

    @Property("SKU名称快照")
    @Column("sku_name")
    private String skuName;

    @Property("商品名称快照")
    @Column("item_name")
    private String itemName;

    @Property("商品类型快照")
    @Column("item_type")
    private String itemType;

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

    @Property("商品快照")
    @Column("item_snapshot")
    private String itemSnapshot;

    @Property("SKU快照")
    @Column("sku_snapshot")
    private String skuSnapshot;

    @Property("订单金额")
    private BigDecimal amount;

    @Property("订单币种")
    private String currency;

    @Property("原始金额")
    @Column("original_amount")
    private BigDecimal originalAmount;

    @Property("优惠金额")
    @Column("discount_amount")
    private BigDecimal discountAmount;

    @Property("优惠券编码")
    @Column("coupon_code")
    private String couponCode;

    @Property("专享购买链接id")
    @Column("offer_link_id")
    private Long offerLinkId;

    @Property("订单结算来源")
    @Column("settlement_source")
    private SettlementSource settlementSource;

    @Property("合同确认编号")
    @Column("external_contract_reference")
    private String externalContractReference;

    @Property("付款确认编号")
    @Column("external_payment_reference")
    private String externalPaymentReference;

    @Property("外部结算确认备注")
    @Column("external_confirmation_note")
    private String externalConfirmationNote;

    @Property("外部结算确认人")
    @Column("external_confirmed_by")
    private String externalConfirmedBy;

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
     * 订单的资金结算来源，用于区分在线支付、优惠全额抵扣和管理员确认的外部付款。
     */
    public enum SettlementSource {

        ONLINE_PAYMENT, COUPON, EXTERNAL_CONFIRMED
    }

    /**
     * 交付状态
     */
    public enum DeliveryStatus {

        WAITING, PROCESSING, SUCCESS, FAILED, SKIPPED
    }
}

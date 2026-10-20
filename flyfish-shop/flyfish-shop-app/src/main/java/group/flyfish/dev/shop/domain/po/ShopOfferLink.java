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
 * 商品专享购买链接。
 *
 * <p>数据库只保存令牌摘要。完整令牌仅在创建响应中返回一次，避免管理列表或数据库读取接口
 * 直接暴露可使用的优惠凭据。</p>
 */
@Getter
@Setter
@Table("shop_offer_link")
public class ShopOfferLink extends AuditDomain {

    @Property("链接备注")
    private String name;

    @Property("令牌SHA-256摘要")
    @Column("token_hash")
    private String tokenHash;

    @Property("令牌末尾提示")
    @Column("token_hint")
    private String tokenHint;

    @Property("内部优惠券编码")
    @Column("coupon_code")
    private String couponCode;

    @Property("链接用途")
    private Purpose purpose;

    @Property("限定商品id")
    @Column("item_id")
    private Long itemId;

    @Property("商品名称快照")
    @Column("item_name")
    private String itemName;

    @Property("限定SKU id")
    @Column("sku_id")
    private Long skuId;

    @Property("SKU名称快照")
    @Column("sku_name")
    private String skuName;

    @Property("专享优惠类型")
    private Type type;

    @Property("优惠值")
    @Column("discount_value")
    private BigDecimal discountValue;

    @Property("最高减免金额")
    @Column("max_discount_amount")
    private BigDecimal maxDiscountAmount;

    @Property("限定领取用户id")
    @Column("recipient_user_id")
    private Long recipientUserId;

    @Property("领取用户名称快照")
    @Column("recipient_name")
    private String recipientName;

    @Property("合同确认编号")
    @Column("contract_reference")
    private String contractReference;

    @Property("付款确认编号")
    @Column("payment_reference")
    private String paymentReference;

    @Property("线下确认备注")
    @Column("confirmation_note")
    private String confirmationNote;

    @Property("已确认结算金额")
    @Column("settlement_amount")
    private BigDecimal settlementAmount;

    @Property("已确认结算币种")
    @Column("settlement_currency")
    private String settlementCurrency;

    @Property("链接状态")
    private Status status;

    @Property("失效时间")
    @Column("expires_at")
    private LocalDateTime expiresAt;

    @Property("预约订单号")
    @Column("reserved_order_no")
    private String reservedOrderNo;

    @Property("预约用户id")
    @Column("reserved_by")
    private Long reservedBy;

    @Property("预约时间")
    @Column("reserved_time")
    private LocalDateTime reservedTime;

    @Property("核销订单号")
    @Column("used_order_no")
    private String usedOrderNo;

    @Property("核销用户id")
    @Column("used_by")
    private Long usedBy;

    @Property("核销时间")
    @Column("used_time")
    private LocalDateTime usedTime;

    @Property("作废时间")
    @Column("revoked_time")
    private LocalDateTime revokedTime;

    public enum Type {

        DISCOUNT,
        REDUCTION,
        FREE
    }

    /**
     * 链接用途。促销链接进入优惠核销，已签约已付款链接直接进入订单交付。
     */
    public enum Purpose {

        PROMOTION,
        SIGNED_PAID_FULFILLMENT
    }

    public enum Status {

        ACTIVE,
        RESERVED,
        USED,
        REVOKED
    }
}

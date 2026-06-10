package group.flyfish.dev.shop.domain.po;

import group.flyfish.dev.annotations.data.Property;
import group.flyfish.dev.common.base.reactive.AuditDomain;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;

/**
 * 店铺交易流水
 *
 * @author wangyu
 */
@Getter
@Setter
@Table("shop_transaction")
public class ShopTransaction extends AuditDomain {

    @Property("交易流水号")
    private String code;

    @Property("关联订单号")
    @Column("order_no")
    private String orderNo;

    @Property("店铺id")
    @Column("shop_id")
    private Long shopId;

    @Property("交易内容")
    private String content;

    @Property("付款人信息")
    private String payer;

    @Property("收款人信息")
    private String receiver;

    @Property("交易金额")
    private BigDecimal amount;

    @Property("交易类型")
    private Type type;

    /**
     * 交易类型
     */
    public enum Type {

        PAYMENT, REFUND
    }
}

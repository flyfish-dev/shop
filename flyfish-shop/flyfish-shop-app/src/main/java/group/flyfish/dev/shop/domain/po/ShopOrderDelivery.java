package group.flyfish.dev.shop.domain.po;

import group.flyfish.dev.common.base.reactive.AuditDomain;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 订单交付提货快照。
 * <p>自动交付商品在支付成功后写入该表；用户点击提取时读取快照，不受商品后续修改影响。</p>
 */
@Getter
@Setter
@Table("shop_order_delivery")
public class ShopOrderDelivery extends AuditDomain {

    /**
     * 订单号。
     */
    @Column("order_no")
    private String orderNo;

    /**
     * 商品 ID。
     */
    @Column("item_id")
    private Long itemId;

    /**
     * 购买用户 ID。
     */
    @Column("buyer_id")
    private Long buyerId;

    /**
     * 交付类型，取值见 {@link DeliveryType}。
     */
    @Column("delivery_type")
    private String deliveryType;

    /**
     * 提货标题。
     */
    private String title;

    /**
     * 提货正文内容。
     */
    private String content;

    /**
     * 交付附件 JSON。
     */
    private String attachments;

    /**
     * 授权编号，授权许可类商品使用。
     */
    @Column("license_no")
    private String licenseNo;

    /**
     * 首次提取时间。
     */
    @Column("extracted_time")
    private LocalDateTime extractedTime;

    public enum DeliveryType {
        DIGITAL,
        LICENSE
    }
}

package group.flyfish.dev.shop.domain.po;

import group.flyfish.dev.annotations.data.Property;
import group.flyfish.dev.common.base.reactive.AuditDomain;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;

/**
 * 商品 SKU。
 */
@Getter
@Setter
@Table("shop_item_sku")
public class ShopItemSku extends AuditDomain {

    @Property("商品id")
    @Column("item_id")
    private Long itemId;

    @Property("SKU编码")
    private String code;

    @Property("SKU名称")
    private String name;

    @Property("SKU描述")
    private String description;

    @Property("SKU价格")
    private BigDecimal price;

    @Property("手工美元价格")
    @Column("usd_price")
    private BigDecimal usdPrice;

    @Property("SKU类型")
    private ShopItem.Type type;

    @Property("交付方式")
    @Column("delivery_mode")
    private ShopItem.DeliveryMode deliveryMode;

    @Property("SKU参数")
    private String params;

    @Property("SKU多语言内容")
    private String i18n;

    @Property("SKU标签")
    private String tags;

    @Property("排序")
    private Integer sort;

    @Property("启用状态")
    private Boolean enabled;

    @Property("默认选中")
    @Column("default_selected")
    private Boolean defaultSelected;

    @Property("购买人数")
    @Column("buy_count")
    private Integer buyCount;

    @Property("启用默认优惠券")
    @Column("default_coupon_enabled")
    private Boolean defaultCouponEnabled;

    @Property("默认优惠券编码")
    @Column("default_coupon_code")
    private String defaultCouponCode;
}

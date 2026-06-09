package group.flyfish.dev.shop.domain.po;

import group.flyfish.dev.annotations.data.Property;
import group.flyfish.dev.common.base.reactive.AuditDomain;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 店铺商品组
 *
 * @author wangyu
 */
@Getter
@Setter
@Table("shop_item_group")
public class ShopItemGroup extends AuditDomain {

    @Property("所属店铺id")
    @Column("shop_id")
    private Long shopId;

    @Property("商品组名")
    private String name;

    @Property("商品组封面")
    private String cover;

    @Property("商品组图标")
    private String icon;

    @Property("商品组描述")
    private String description;

    @Property("排序")
    private Integer sort;

    @Property("启用状态")
    private Boolean enabled;
}

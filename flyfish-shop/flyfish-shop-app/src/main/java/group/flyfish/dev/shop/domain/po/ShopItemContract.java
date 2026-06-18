package group.flyfish.dev.shop.domain.po;

import group.flyfish.dev.annotations.data.Property;
import group.flyfish.dev.common.base.reactive.AuditDomain;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 商品与合同的绑定关系。
 */
@Getter
@Setter
@Table("shop_item_contract")
public class ShopItemContract extends AuditDomain {

    @Property("商品id")
    @Column("item_id")
    private Long itemId;

    @Property("合同id")
    @Column("contract_id")
    private Long contractId;

    @Property("是否必签")
    private Boolean required;

    @Property("启用状态")
    private Boolean enabled;

    @Property("排序")
    private Integer sort;
}

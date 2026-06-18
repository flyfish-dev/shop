package group.flyfish.dev.shop.domain.po;

import group.flyfish.dev.annotations.data.Property;
import group.flyfish.dev.common.base.reactive.AuditDomain;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 商铺信息
 *
 * @author wangyu
 */
@Getter
@Setter
@Table("shop")
public class Shop extends AuditDomain {

    @Property("商铺名称")
    private String name;

    @Property("商铺描述")
    private String description;

    @Property("商铺头像")
    private String avatar;
}

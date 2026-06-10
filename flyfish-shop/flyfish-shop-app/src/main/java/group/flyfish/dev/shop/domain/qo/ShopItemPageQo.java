package group.flyfish.dev.shop.domain.qo;

import group.flyfish.dev.common.base.reactive.PageableQo;
import group.flyfish.dev.common.bean.page.qo.PagedQo;
import group.flyfish.dev.shop.domain.po.ShopItem;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品分页查询对象
 *
 * @author wangyu
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ShopItemPageQo extends PageableQo<ShopItem> {

    /**
     * 所属店铺ID
     */
    private Long shopId;

    /**
     * 所属分组ID
     */
    private Long groupId;

    /**
     * 商品名称(模糊查询)
     */
    private String name;

    /**
     * 商品状态
     */
    private Boolean enabled;
}

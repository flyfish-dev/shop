package group.flyfish.dev.shop.domain.vo;

import lombok.Data;

/**
 * 商品分组列表
 *
 * @author wangyu
 */
@Data
public class ShopItemGroupListVo {

    // 商品分组id
    private Long id;

    // 商品分组名称
    private String name;

    private String description;

    private Integer sort;

    private Boolean enabled;
}

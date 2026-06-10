package group.flyfish.dev.shop.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品列表实体
 *
 * @author wangyu
 */
@Data
public class ShopItemListVo {

    // 主键
    private String id;

    // 商品名
    private String name;

    // 商品封面
    private String cover;

    // 商品价格
    private String price;

    // 分组id
    private Long groupId;

    // 商品标签
    private List<String> tags;

    // 商品类型
    private String type;

    // 商品类型名称
    private String typeName;

    // 交付方式
    private String deliveryMode;

    // 交付方式名称
    private String deliveryModeName;

    // 发布时间
    private LocalDateTime createTime;

    // 购买数量
    private Integer buyCount;

    private Integer sort;

    // 上架状态
    private Boolean enabled;

    // 置顶状态
    private Boolean pinned;

    // 推荐状态
    private Boolean recommended;
}

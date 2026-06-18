package group.flyfish.dev.shop.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品详情
 *
 * @author wangyu
 */
@Data
public class ShopItemDetailVo {

    // 商品id
    private String id;

    // 商品名称
    private String name;

    // 商品类型
    private String type;

    // 商品类型名称
    private String typeName;

    // 交付方式
    private String deliveryMode;

    // 交付方式名称
    private String deliveryModeName;

    // 自动交付动作
    private List<String> deliveryActions;

    // 分组id
    private Long groupId;

    // 价格
    private BigDecimal price;

    // 商品标签
    private List<String> tags;

    // 商品图集，第一张永远是封面，后面是图集
    private List<String> images;

    // 商品描述
    private String description;

    // 上架状态
    private Boolean enabled;

    // 排序
    private Integer sort;

    // 置顶状态
    private Boolean pinned;

    // 推荐状态
    private Boolean recommended;

    // 商品参数
    private String params;

    // 购买人数
    private Integer buyCount;

    // 醒目样式
    private String highlightStyle;

    // 醒目图标
    private String highlightIcon;

    // 是否启用默认优惠券
    private Boolean defaultCouponEnabled;

    // 默认优惠券编码
    private String defaultCouponCode;

    // 默认优惠券试算结果，仅用于展示券后价
    private ShopCouponApplyVo defaultCouponPreview;

    // 是否需要购买前签署合同
    private Boolean contractRequired;

    // 绑定的合同id
    private List<Long> contractIds;
}

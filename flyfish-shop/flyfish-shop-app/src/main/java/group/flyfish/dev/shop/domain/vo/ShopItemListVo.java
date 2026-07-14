package group.flyfish.dev.shop.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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

    // 手工美元价格，为空时按汇率自动换算
    private String usdPrice;

    // 实际展示和结算使用的美元价格
    private String effectiveUsdPrice;

    // 人民币兑美元换算快照
    private String cnyPerUsd;

    // 分组id
    private Long groupId;

    // 商品标签
    private List<String> tags;

    // 商品多语言内容
    private Map<String, ShopItemI18nVo> i18n;

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

    // SKU模式
    private String skuMode;

    // 是否多SKU商品
    private Boolean multiSku;

    // SKU数量
    private Integer skuCount;

    // 最低SKU价格
    private String minPrice;

    // 最高SKU价格
    private String maxPrice;

    // 最低SKU美元价格
    private String minEffectiveUsdPrice;

    // 最高SKU美元价格
    private String maxEffectiveUsdPrice;

    // 默认SKU
    private ShopItemSkuVo defaultSku;
}

package group.flyfish.dev.shop.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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

    // 手工美元价格，为空时按汇率自动换算
    private BigDecimal usdPrice;

    // 实际展示和结算使用的美元价格
    private BigDecimal effectiveUsdPrice;

    // 人民币兑美元换算快照
    private BigDecimal cnyPerUsd;

    // 商品标签
    private List<String> tags;

    // 商品图集，第一张永远是封面，后面是图集
    private List<String> images;

    // 商品描述
    private String description;

    // 商品多语言内容
    private Map<String, ShopItemI18nVo> i18n;

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

    // 商品查看次数
    private Long viewCount;

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

    // SKU模式
    private String skuMode;

    // 是否多SKU商品
    private Boolean multiSku;

    // 默认SKU
    private ShopItemSkuVo defaultSku;

    // SKU列表
    private List<ShopItemSkuVo> skus;
}

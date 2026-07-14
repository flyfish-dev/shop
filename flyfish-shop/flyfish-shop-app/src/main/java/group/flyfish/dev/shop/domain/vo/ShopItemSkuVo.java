package group.flyfish.dev.shop.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 商品 SKU 视图。
 */
@Data
public class ShopItemSkuVo {

    private Long id;

    private Long itemId;

    private String code;

    private String name;

    private String description;

    private BigDecimal price;

    private BigDecimal usdPrice;

    private BigDecimal effectiveUsdPrice;

    private BigDecimal cnyPerUsd;

    private String type;

    private String typeName;

    private String deliveryMode;

    private String deliveryModeName;

    private List<String> deliveryActions;

    private String params;

    private Map<String, ShopItemI18nVo> i18n;

    private List<String> tags;

    private Integer sort;

    private Boolean enabled;

    private Boolean defaultSelected;

    private Integer buyCount;

    private Boolean defaultCouponEnabled;

    private String defaultCouponCode;

    private ShopCouponApplyVo defaultCouponPreview;

    private Boolean contractRequired;

    private List<Long> contractIds;
}

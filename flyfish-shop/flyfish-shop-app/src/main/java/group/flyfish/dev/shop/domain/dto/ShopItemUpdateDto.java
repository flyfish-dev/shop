package group.flyfish.dev.shop.domain.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import group.flyfish.dev.shop.domain.po.ShopDeliveryAction;
import group.flyfish.dev.shop.domain.po.ShopItem;

/**
 * 商品更新DTO
 */
@Data
public class ShopItemUpdateDto {

    private String name;

    private String cover;

    private List<String> images;

    @DecimalMin(value = "0.01", message = "商品价格必须大于0")
    private BigDecimal price;

    @DecimalMin(value = "0.01", message = "美元价格必须大于0")
    private BigDecimal usdPrice;

    private Boolean usdPriceAutomatic;

    private Long groupId;

    private ShopItem.Type type;

    private ShopItem.DeliveryMode deliveryMode;

    private List<ShopDeliveryAction> deliveryActions;

    private List<String> tags;

    private String params;

    private String description;

    private Map<String, ShopItemI18nDto> i18n;

    @Min(value = 0, message = "商品排序不能为负数")
    private Integer sort;

    private Boolean enabled;

    private Boolean pinned;

    private Boolean recommended;

    private String highlightStyle;

    private String highlightIcon;

    private Boolean defaultCouponEnabled;

    private String defaultCouponCode;

    private ShopItem.SkuMode skuMode;

    private List<ShopItemSkuDto> skus;

    private List<Long> contractIds;
}

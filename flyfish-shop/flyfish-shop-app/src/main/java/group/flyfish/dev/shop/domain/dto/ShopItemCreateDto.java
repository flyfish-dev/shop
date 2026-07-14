package group.flyfish.dev.shop.domain.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import group.flyfish.dev.shop.domain.po.ShopDeliveryAction;
import group.flyfish.dev.shop.domain.po.ShopItem;

/**
 * 商品创建DTO
 */
@Data
public class ShopItemCreateDto {

    @NotBlank(message = "商品名称不能为空")
    private String name;

    private String cover;

    private List<String> images;

    @NotNull(message = "商品价格不能为空")
    @DecimalMin(value = "0.01", message = "商品价格必须大于0")
    private BigDecimal price;

    @DecimalMin(value = "0.01", message = "美元价格必须大于0")
    private BigDecimal usdPrice;

    private Boolean usdPriceAutomatic = true;

    @NotNull(message = "分组ID不能为空")
    private Long groupId;

    @NotNull(message = "商品类型不能为空")
    private ShopItem.Type type;

    private ShopItem.DeliveryMode deliveryMode;

    private List<ShopDeliveryAction> deliveryActions;

    private List<String> tags;

    private String params;

    private String description;

    private Map<String, ShopItemI18nDto> i18n;

    @Min(value = 0, message = "商品排序不能为负数")
    private Integer sort = 0;

    private Boolean enabled = false;

    private Boolean pinned = false;

    private Boolean recommended = false;

    private String highlightStyle;

    private String highlightIcon;

    private Boolean defaultCouponEnabled = false;

    private String defaultCouponCode;

    private ShopItem.SkuMode skuMode = ShopItem.SkuMode.SINGLE;

    private List<ShopItemSkuDto> skus;

    private List<Long> contractIds;
}

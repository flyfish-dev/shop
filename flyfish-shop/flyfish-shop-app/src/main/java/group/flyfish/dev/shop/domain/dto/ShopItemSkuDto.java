package group.flyfish.dev.shop.domain.dto;

import group.flyfish.dev.shop.domain.po.ShopDeliveryAction;
import group.flyfish.dev.shop.domain.po.ShopItem;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 商品 SKU 保存 DTO。
 */
@Data
public class ShopItemSkuDto {

    private Long id;

    private String code;

    @NotBlank(message = "SKU名称不能为空")
    private String name;

    private String description;

    @NotNull(message = "SKU价格不能为空")
    @DecimalMin(value = "0.01", message = "SKU价格必须大于0")
    private BigDecimal price;

    @DecimalMin(value = "0.01", message = "SKU美元价格必须大于0")
    private BigDecimal usdPrice;

    private ShopItem.Type type;

    private ShopItem.DeliveryMode deliveryMode;

    private List<ShopDeliveryAction> deliveryActions;

    private String params;

    private Map<String, ShopItemI18nDto> i18n;

    private List<String> tags;

    @Min(value = 0, message = "SKU排序不能为负数")
    private Integer sort = 0;

    private Boolean enabled = true;

    private Boolean defaultSelected = false;

    private Boolean defaultCouponEnabled;

    private String defaultCouponCode;

    private List<Long> contractIds;
}

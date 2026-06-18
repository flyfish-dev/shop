package group.flyfish.dev.shop.domain.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
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

    @NotNull(message = "分组ID不能为空")
    private Long groupId;

    @NotNull(message = "商品类型不能为空")
    private ShopItem.Type type;

    private ShopItem.DeliveryMode deliveryMode;

    private List<ShopDeliveryAction> deliveryActions;

    private List<String> tags;

    private String params;

    private String description;

    @Min(value = 0, message = "商品排序不能为负数")
    private Integer sort = 0;

    private Boolean enabled = false;

    private Boolean pinned = false;

    private Boolean recommended = false;

    private String highlightStyle;

    private String highlightIcon;

    private Boolean defaultCouponEnabled = false;

    private String defaultCouponCode;

    private List<Long> contractIds;
}

package group.flyfish.dev.shop.domain.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
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

    private Long groupId;

    private ShopItem.Type type;

    private ShopItem.DeliveryMode deliveryMode;

    private List<String> tags;

    private String params;

    private String description;

    @Min(value = 0, message = "商品排序不能为负数")
    private Integer sort;

    private Boolean enabled;

    private Boolean pinned;

    private Boolean recommended;
}

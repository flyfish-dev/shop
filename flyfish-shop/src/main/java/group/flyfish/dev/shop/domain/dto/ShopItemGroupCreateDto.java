package group.flyfish.dev.shop.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 商品分组创建DTO
 */
@Data
public class ShopItemGroupCreateDto {

    @NotBlank(message = "分组名称不能为空")
    private String name;

    private String description;

    private String cover;

    private String icon;

    private Integer sort = 0;

    private Boolean enabled = true;
}

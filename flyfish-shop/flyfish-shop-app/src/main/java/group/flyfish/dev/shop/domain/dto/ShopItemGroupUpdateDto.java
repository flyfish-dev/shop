package group.flyfish.dev.shop.domain.dto;

import lombok.Data;

@Data
public class ShopItemGroupUpdateDto {
    private String name;
    private Integer sort;
    private String description;
    private Boolean enabled;
    private String cover;
    private String icon;
}

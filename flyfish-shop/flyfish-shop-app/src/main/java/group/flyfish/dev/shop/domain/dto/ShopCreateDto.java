package group.flyfish.dev.shop.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建店铺请求DTO
 *
 * @author wangyu
 */
@Data
public class ShopCreateDto {

    /**
     * 店铺名称
     */
    @NotBlank(message = "店铺名称不能为空")
    private String name;

    /**
     * 店铺头像
     */
    private String avatar;

    /**
     * 店铺描述
     */
    @NotBlank(message = "店铺描述不能为空")
    private String description;

}

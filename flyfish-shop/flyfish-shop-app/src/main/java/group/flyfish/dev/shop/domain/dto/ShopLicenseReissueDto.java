package group.flyfish.dev.shop.domain.dto;

import group.flyfish.dev.annotations.data.Property;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员补发永久商业许可证请求。
 */
@Data
public class ShopLicenseReissueDto {

    @NotBlank(message = "请填写补发原因")
    @Size(max = 512, message = "补发原因不能超过512个字符")
    @Property("补发原因")
    private String reason;
}

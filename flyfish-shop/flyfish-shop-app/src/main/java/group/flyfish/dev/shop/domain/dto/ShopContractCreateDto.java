package group.flyfish.dev.shop.domain.dto;

import group.flyfish.dev.shop.domain.po.ShopContract;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 合同创建参数。
 */
@Data
public class ShopContractCreateDto {

    @NotBlank(message = "合同名称不能为空")
    private String name;

    @NotNull(message = "合同类型不能为空")
    private ShopContract.Type type;

    private String description;

    private List<String> tags;

    private Map<String, ShopContractI18nDto> i18n;

    private Boolean enabled = true;

    @Min(value = 0, message = "排序不能为负数")
    private Integer sort = 0;
}

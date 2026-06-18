package group.flyfish.dev.shop.domain.dto;

import group.flyfish.dev.shop.domain.po.ShopContract;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.List;

/**
 * 合同更新参数。
 */
@Data
public class ShopContractUpdateDto {

    private String name;

    private ShopContract.Type type;

    private String description;

    private List<String> tags;

    private Boolean enabled;

    @Min(value = 0, message = "排序不能为负数")
    private Integer sort;
}

package group.flyfish.dev.shop.domain.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 合同文件更新参数。
 */
@Data
public class ShopContractFileUpdateDto {

    private String fileName;

    private Boolean enabled;

    @Min(value = 0, message = "排序不能为负数")
    private Integer sort;
}

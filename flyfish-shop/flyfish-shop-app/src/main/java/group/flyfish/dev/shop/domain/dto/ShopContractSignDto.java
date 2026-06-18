package group.flyfish.dev.shop.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 合同文件同意参数。
 */
@Data
public class ShopContractSignDto {

    private String signToken;

    @NotNull(message = "合同id不能为空")
    private Long contractId;

    @NotNull(message = "合同文件id不能为空")
    private Long fileId;

    private Integer readPercent = 100;
}

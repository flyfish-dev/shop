package group.flyfish.dev.git.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GitRepositorySyncDto {

    @NotBlank(message = "请选择代码平台")
    private String provider;

    @NotNull(message = "请选择 API Token")
    private Long accessTokenId;

    private String keyword;
}

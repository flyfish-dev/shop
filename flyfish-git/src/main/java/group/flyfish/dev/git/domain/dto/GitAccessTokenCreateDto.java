package group.flyfish.dev.git.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GitAccessTokenCreateDto {

    @NotBlank(message = "请选择代码平台")
    private String provider;

    @NotBlank(message = "请输入 Token 名称")
    @Size(max = 128, message = "Token 名称不能超过128个字符")
    private String name;

    @Size(max = 512, message = "中文描述不能超过512个字符")
    private String description;

    @NotBlank(message = "请输入 API Token")
    private String tokenValue;

    @Size(max = 128, message = "账号名称不能超过128个字符")
    private String username;

    private LocalDateTime expireTime;

    private Boolean enabled = true;

    private Integer sort = 0;
}

package group.flyfish.dev.git.domain.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GitAccessTokenUpdateDto {

    private String provider;

    @Size(max = 128, message = "Token 名称不能超过128个字符")
    private String name;

    @Size(max = 512, message = "中文描述不能超过512个字符")
    private String description;

    /**
     * 更新时允许为空，空值表示保留原 Token，避免前端回显敏感明文。
     */
    private String tokenValue;

    @Size(max = 128, message = "账号名称不能超过128个字符")
    private String username;

    private LocalDateTime expireTime;

    private Boolean enabled;

    private Integer sort;
}

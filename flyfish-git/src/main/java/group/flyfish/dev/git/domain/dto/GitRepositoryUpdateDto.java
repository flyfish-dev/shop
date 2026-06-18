package group.flyfish.dev.git.domain.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GitRepositoryUpdateDto {

    private String provider;

    private Long accessTokenId;

    @Size(max = 128, message = "仓库 Owner 不能超过128个字符")
    private String owner;

    @Size(max = 128, message = "仓库名称不能超过128个字符")
    private String repo;

    @Size(max = 128, message = "管理名称不能超过128个字符")
    private String name;

    @Size(max = 512, message = "中文描述不能超过512个字符")
    private String description;

    private String permission;

    @Size(max = 512, message = "仓库地址不能超过512个字符")
    private String url;

    private Boolean privateRepo;

    private LocalDateTime expireTime;

    private Boolean enabled;

    private Integer sort;
}

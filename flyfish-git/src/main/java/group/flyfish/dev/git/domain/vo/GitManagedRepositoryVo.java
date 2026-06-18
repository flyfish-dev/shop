package group.flyfish.dev.git.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class GitManagedRepositoryVo {

    private Long id;

    private String provider;

    private String providerName;

    private Long accessTokenId;

    private String accessTokenName;

    private String owner;

    private String repo;

    private String fullName;

    private String name;

    private String description;

    private String permission;

    private String permissionName;

    private String url;

    private Boolean privateRepo;

    private LocalDateTime expireTime;

    private Boolean expired;

    private Boolean enabled;

    private Integer sort;
}

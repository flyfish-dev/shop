package group.flyfish.dev.git.domain.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GitRepositoryOptionVo {

    private Long id;

    private String provider;

    private String providerName;

    private String owner;

    private String repo;

    private String fullName;

    private String name;

    private String description;

    private String url;

    private Boolean privateRepo;

    private String permission;

    private String tokenPermission;
}

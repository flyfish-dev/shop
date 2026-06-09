package group.flyfish.dev.git.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class GitAccessTokenVo {

    private Long id;

    private String provider;

    private String providerName;

    private String name;

    private String description;

    private String username;

    private String tokenMasked;

    private String tokenTail;

    private LocalDateTime expireTime;

    private Boolean expired;

    private Boolean enabled;

    private Integer sort;
}

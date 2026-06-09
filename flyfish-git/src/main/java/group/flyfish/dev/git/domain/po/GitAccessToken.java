package group.flyfish.dev.git.domain.po;

import group.flyfish.dev.annotations.data.Property;
import group.flyfish.dev.common.base.reactive.AuditDomain;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 代码托管平台 API Token。
 */
@Getter
@Setter
@Table("git_api_token")
public class GitAccessToken extends AuditDomain {

    @Property("平台")
    private String provider;

    @Property("名称")
    private String name;

    @Property("中文描述")
    private String description;

    @Property("令牌值")
    @Column("token_value")
    private String tokenValue;

    @Property("令牌归属账号")
    private String username;

    @Property("过期时间")
    @Column("expire_time")
    private LocalDateTime expireTime;

    @Property("启用状态")
    private Boolean enabled;

    @Property("排序")
    private Integer sort;
}

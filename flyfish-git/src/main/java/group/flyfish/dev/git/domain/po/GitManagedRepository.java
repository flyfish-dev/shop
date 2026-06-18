package group.flyfish.dev.git.domain.po;

import group.flyfish.dev.annotations.data.Property;
import group.flyfish.dev.common.base.reactive.AuditDomain;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 系统可交付的代码仓库。
 */
@Getter
@Setter
@Table("git_repository")
public class GitManagedRepository extends AuditDomain {

    @Property("平台")
    private String provider;

    @Property("API Token ID")
    @Column("access_token_id")
    private Long accessTokenId;

    @Property("仓库 Owner")
    private String owner;

    @Property("仓库名称")
    private String repo;

    @Property("仓库全名")
    @Column("full_name")
    private String fullName;

    @Property("管理名称")
    private String name;

    @Property("中文描述")
    private String description;

    @Property("交付权限")
    private String permission;

    @Property("仓库地址")
    private String url;

    @Property("私有仓库")
    @Column("private_repo")
    private Boolean privateRepo;

    @Property("过期时间")
    @Column("expire_time")
    private LocalDateTime expireTime;

    @Property("启用状态")
    private Boolean enabled;

    @Property("排序")
    private Integer sort;
}

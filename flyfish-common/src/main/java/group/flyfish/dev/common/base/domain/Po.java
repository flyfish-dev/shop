package group.flyfish.dev.common.base.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 所有实体类继承
 */
@Getter
@Setter
public abstract class Po implements Serializable {

    @Id
    private String id;

    @Column("create_by")
    private String createBy;

    @Column("create_time")
    private LocalDateTime createTime;

    @Column("update_by")
    private String updateBy;

    @Column("update_time")
    private LocalDateTime updateTime;

    @Column("is_delete")
    private Boolean delete;
}

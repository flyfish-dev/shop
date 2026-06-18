package group.flyfish.dev.common.base.reactive;

import lombok.Data;
import org.springframework.data.annotation.*;
import org.springframework.data.relational.core.mapping.Column;

import java.time.LocalDateTime;

@Data
public class AuditDomain implements Domain<Long> {

    @Id
    private Long id;

    @Column("create_by")
    @CreatedBy
    private String createBy;

    @Column(value = "create_time")
    @CreatedDate
    private LocalDateTime createTime;

    @Column("update_by")
    @LastModifiedBy
    private String updateBy;

    @Column(value = "update_time")
    @LastModifiedDate
    private LocalDateTime updateTime;

    @Column(value = "is_delete")
    private Boolean delete = false;
}

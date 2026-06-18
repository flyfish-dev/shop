package group.flyfish.dev.support.domain.po;

import group.flyfish.dev.common.base.reactive.AuditDomain;
import lombok.Data;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Table("support_ticket")
public class SupportTicket extends AuditDomain {

    @Column("ticket_no")
    private String ticketNo;

    @Column("creator_id")
    private Long creatorId;

    private String title;

    private String category;

    private String priority;

    private String status;

    private String contact;

    @Column("last_message")
    private String lastMessage;

    @Column("admin_unread_count")
    private Integer adminUnreadCount;

    @Column("user_unread_count")
    private Integer userUnreadCount;

    @Column("assignee_id")
    private Long assigneeId;

    @Column("resolved_by")
    private Long resolvedBy;

    @Column("resolved_time")
    private LocalDateTime resolvedTime;

    public enum Priority {
        LOW,
        NORMAL,
        HIGH,
        URGENT
    }

    public enum Status {
        OPEN,
        PROCESSING,
        WAITING_USER,
        RESOLVED,
        CLOSED
    }
}

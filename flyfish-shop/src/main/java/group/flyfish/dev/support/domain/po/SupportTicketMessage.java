package group.flyfish.dev.support.domain.po;

import group.flyfish.dev.common.base.reactive.AuditDomain;
import lombok.Data;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("support_ticket_message")
public class SupportTicketMessage extends AuditDomain {

    @Column("ticket_id")
    private Long ticketId;

    @Column("sender_id")
    private Long senderId;

    @Column("sender_role")
    private String senderRole;

    private String content;

    /**
     * 消息附件 JSON，保存上传后的附件元数据。
     */
    private String attachments;

    public enum SenderRole {
        USER,
        ADMIN
    }
}

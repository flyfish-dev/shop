package group.flyfish.dev.support.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import group.flyfish.dev.support.domain.po.SupportTicket;
import group.flyfish.dev.user.domain.po.PortalUser;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;

@Data
public class SupportTicketVo {

    private Long id;

    private String ticketNo;

    private Long creatorId;

    private String creatorName;

    private String creatorAvatar;

    private String creatorPhone;

    private String creatorEmail;

    private String title;

    private String category;

    private String priority;

    private String status;

    private String contact;

    private String lastMessage;

    private Integer adminUnreadCount;

    private Integer userUnreadCount;

    private Integer unreadCount;

    private Long assigneeId;

    private Long resolvedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime resolvedTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime updateTime;

    public SupportTicketVo(SupportTicket ticket, PortalUser creator) {
        this(ticket, creator, false);
    }

    public SupportTicketVo(SupportTicket ticket, PortalUser creator, boolean managerView) {
        this.id = ticket.getId();
        this.ticketNo = ticket.getTicketNo();
        this.creatorId = ticket.getCreatorId();
        this.creatorName = creatorName(creator, ticket.getCreatorId());
        this.creatorAvatar = creator == null ? null : creator.getAvatar();
        this.creatorPhone = creator == null ? null : creator.getPhone();
        this.creatorEmail = creator == null ? null : creator.getEmail();
        this.title = ticket.getTitle();
        this.category = ticket.getCategory();
        this.priority = ticket.getPriority();
        this.status = ticket.getStatus();
        this.contact = ticket.getContact();
        this.lastMessage = ticket.getLastMessage();
        this.adminUnreadCount = safe(ticket.getAdminUnreadCount());
        this.userUnreadCount = safe(ticket.getUserUnreadCount());
        this.unreadCount = managerView ? this.adminUnreadCount : this.userUnreadCount;
        this.assigneeId = ticket.getAssigneeId();
        this.resolvedBy = ticket.getResolvedBy();
        this.resolvedTime = ticket.getResolvedTime();
        this.createTime = ticket.getCreateTime();
        this.updateTime = ticket.getUpdateTime();
    }

    private int safe(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private static String creatorName(PortalUser creator, Long fallbackId) {
        if (creator == null) {
            return fallbackId == null ? "用户" : "用户 " + fallbackId;
        }
        return StringUtils.defaultIfBlank(creator.getUsername(), "用户 " + creator.getId());
    }
}

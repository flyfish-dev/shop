package group.flyfish.dev.customer.domain.vo;

import group.flyfish.dev.support.domain.vo.SupportTicketVo;
import lombok.Data;

import java.util.List;

@Data
public class CustomerServiceSummaryVo {

    /**
     * 当前登录人是否为客服管理员。
     */
    private boolean manager;

    /**
     * 当前登录人是否绑定过微信账号。
     */
    private boolean wechatBound;

    /**
     * 消息和工单合计未读数，用于全局铃铛角标。
     */
    private Long unreadCount;

    /**
     * 客服聊天未读数。
     */
    private Long customerMessageUnreadCount;

    /**
     * 工单未读数。
     */
    private Long ticketUnreadCount;

    /**
     * 未读会话提醒列表。
     */
    private List<CustomerConversationVo> conversations;

    /**
     * 未读工单提醒列表。
     */
    private List<SupportTicketVo> ticketReminders;

    public CustomerServiceSummaryVo(boolean manager, boolean wechatBound, Long unreadCount,
                                    List<CustomerConversationVo> conversations) {
        this(manager, wechatBound, unreadCount, unreadCount, 0L, conversations, List.of());
    }

    public CustomerServiceSummaryVo(boolean manager, boolean wechatBound, Long customerMessageUnreadCount,
                                    Long ticketUnreadCount, List<CustomerConversationVo> conversations,
                                    List<SupportTicketVo> ticketReminders) {
        this(manager, wechatBound, safe(customerMessageUnreadCount) + safe(ticketUnreadCount),
                customerMessageUnreadCount, ticketUnreadCount, conversations, ticketReminders);
    }

    private CustomerServiceSummaryVo(boolean manager, boolean wechatBound, Long unreadCount,
                                     Long customerMessageUnreadCount, Long ticketUnreadCount,
                                     List<CustomerConversationVo> conversations,
                                     List<SupportTicketVo> ticketReminders) {
        this.manager = manager;
        this.wechatBound = wechatBound;
        this.unreadCount = safe(unreadCount);
        this.customerMessageUnreadCount = safe(customerMessageUnreadCount);
        this.ticketUnreadCount = safe(ticketUnreadCount);
        this.conversations = conversations == null ? List.of() : conversations;
        this.ticketReminders = ticketReminders == null ? List.of() : ticketReminders;
    }

    private static long safe(Long value) {
        return value == null ? 0 : Math.max(0, value);
    }
}

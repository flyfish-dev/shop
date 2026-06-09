package group.flyfish.dev.customer.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import group.flyfish.dev.customer.domain.po.CustomerConversation;
import group.flyfish.dev.user.support.FunNicknameGenerator;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;

@Data
public class CustomerConversationVo {

    /**
     * 会话 ID。
     */
    private Long id;

    /**
     * 平台用户 ID。
     */
    private Long userId;

    /**
     * 微信公众号 openid 或站内会话稳定键。
     */
    private String wechatOpenid;

    /**
     * 会话展示名称。
     */
    private String displayName;

    /**
     * 会话展示头像。
     */
    private String avatar;

    /**
     * 会话状态。
     */
    private String status;

    /**
     * 最近一条消息摘要。
     */
    private String lastMessage;

    /**
     * 最近一条消息时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime lastMessageTime;

    /**
     * 最近一条用户侧消息时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime lastInboundTime;

    /**
     * 管理员侧未读消息数。
     */
    private Integer adminUnreadCount;

    /**
     * 用户侧未读消息数。
     */
    private Integer userUnreadCount;

    /**
     * 当前视角下需要展示的未读消息数。
     */
    private Integer unreadCount;

    public CustomerConversationVo(CustomerConversation conversation, boolean managerView) {
        this.id = conversation.getId();
        this.userId = conversation.getUserId();
        this.wechatOpenid = conversation.getWechatOpenid();
        this.displayName = displayName(conversation);
        this.avatar = conversation.getAvatar();
        this.status = conversation.getStatus();
        this.lastMessage = conversation.getLastMessage();
        this.lastMessageTime = conversation.getLastMessageTime();
        this.lastInboundTime = conversation.getLastInboundTime();
        this.adminUnreadCount = safe(conversation.getAdminUnreadCount());
        this.userUnreadCount = safe(conversation.getUserUnreadCount());
        this.unreadCount = managerView ? this.adminUnreadCount : this.userUnreadCount;
    }

    private int safe(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private String displayName(CustomerConversation conversation) {
        String value = StringUtils.trimToEmpty(conversation.getDisplayName());
        if (StringUtils.isNotBlank(value) && !FunNicknameGenerator.isGenericWechatName(value)) {
            return value;
        }
        String openid = StringUtils.trimToEmpty(conversation.getWechatOpenid());
        if (StringUtils.isNotBlank(openid) && !StringUtils.startsWith(openid, "web:user:")) {
            return FunNicknameGenerator.generate(openid);
        }
        if (conversation.getUserId() != null && conversation.getUserId() > 0) {
            return "客户 #" + conversation.getUserId();
        }
        return StringUtils.defaultIfBlank(value, "客户");
    }

}

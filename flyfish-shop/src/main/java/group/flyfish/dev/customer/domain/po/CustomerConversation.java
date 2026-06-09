package group.flyfish.dev.customer.domain.po;

import group.flyfish.dev.common.base.reactive.AuditDomain;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Getter
@Setter
@Table("customer_conversation")
public class CustomerConversation extends AuditDomain {

    /**
     * 平台用户 ID；微信未绑定用户时可能为空，绑定后用于归一化会话。
     */
    @Column("user_id")
    private Long userId;

    /**
     * 微信公众号 openid；浏览器纯站内会话使用 web:user:{userId} 作为稳定会话键。
     */
    @Column("wechat_openid")
    private String wechatOpenid;

    /**
     * 微信开放平台 unionId，用于后续跨应用识别同一微信用户。
     */
    @Column("wechat_union_id")
    private String wechatUnionId;

    /**
     * 会话展示名称，优先使用第三方昵称或用户维护的昵称。
     */
    @Column("display_name")
    private String displayName;

    /**
     * 会话展示头像，来自绑定平台头像或用户资料头像。
     */
    private String avatar;

    /**
     * 会话状态，取值见 {@link Status}。
     */
    private String status;

    /**
     * 最近一条消息摘要，用于管理员会话列表预览。
     */
    @Column("last_message")
    private String lastMessage;

    /**
     * 最近一条消息时间，用于会话列表倒序排序。
     */
    @Column("last_message_time")
    private LocalDateTime lastMessageTime;

    /**
     * 最近一条用户侧消息时间，便于识别客户最近主动咨询时间。
     */
    @Column("last_inbound_time")
    private LocalDateTime lastInboundTime;

    /**
     * 管理员侧未读消息数，用户或公众号发来的消息会累加。
     */
    @Column("admin_unread_count")
    private Integer adminUnreadCount;

    /**
     * 用户侧未读消息数，管理员回复后会累加。
     */
    @Column("user_unread_count")
    private Integer userUnreadCount;

    public enum Status {
        OPEN,
        CLOSED
    }
}

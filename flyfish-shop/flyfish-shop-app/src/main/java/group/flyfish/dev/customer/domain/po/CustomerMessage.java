package group.flyfish.dev.customer.domain.po;

import group.flyfish.dev.common.base.reactive.AuditDomain;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Setter
@Table("customer_message")
public class CustomerMessage extends AuditDomain {

    /**
     * 所属客服会话 ID。
     */
    @Column("conversation_id")
    private Long conversationId;

    /**
     * 平台用户 ID；微信未绑定用户的公众号消息可能为空。
     */
    @Column("user_id")
    private Long userId;

    /**
     * 微信公众号 openid 或站内会话稳定键。
     */
    @Column("wechat_openid")
    private String wechatOpenid;

    /**
     * 消息方向，取值见 {@link Direction}。
     */
    private String direction;

    /**
     * 消息来源渠道，取值见 {@link Channel}。
     */
    private String channel;

    /**
     * 发送方角色，取值见 {@link SenderRole}。
     */
    @Column("sender_role")
    private String senderRole;

    /**
     * 发送方平台用户 ID；管理员 Web 回复时记录管理员 ID。
     */
    @Column("sender_id")
    private Long senderId;

    /**
     * 消息类型，支持 text、markdown、image、file，用于前端选择不同展示方式。
     */
    @Column("message_type")
    private String messageType;

    /**
     * 消息正文内容。
     */
    private String content;

    /**
     * 消息附件 JSON，保存已上传文件的名称、URL、大小和类型。
     */
    private String attachments;

    /**
     * 原始消息报文，主要用于微信公众号回调追溯。
     */
    @Column("raw_payload")
    private String rawPayload;

    /**
     * 微信消息 ID，用于公众号消息幂等和排查。
     */
    @Column("wechat_msg_id")
    private String wechatMsgId;

    /**
     * 关联业务类型，例如订单、工单等。
     */
    @Column("related_type")
    private String relatedType;

    /**
     * 关联业务编号，例如订单号或工单号。
     */
    @Column("related_no")
    private String relatedNo;

    /**
     * 发送状态，取值见 {@link SendStatus}。
     */
    @Column("send_status")
    private String sendStatus;

    /**
     * 发送失败时的错误信息。
     */
    @Column("error_message")
    private String errorMessage;

    /**
     * 管理员是否已读该消息。
     */
    @Column("read_by_admin")
    private Boolean readByAdmin;

    /**
     * 用户是否已读该消息。
     */
    @Column("read_by_user")
    private Boolean readByUser;

    public enum Direction {
        INBOUND,
        OUTBOUND
    }

    public enum Channel {
        WECHAT,
        WEB
    }

    public enum SenderRole {
        USER,
        ADMIN,
        SYSTEM
    }

    public enum SendStatus {
        RECEIVED,
        SENDING,
        SENT,
        FAILED
    }
}

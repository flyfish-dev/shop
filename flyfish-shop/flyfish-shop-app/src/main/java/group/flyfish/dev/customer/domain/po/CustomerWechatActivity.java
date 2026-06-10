package group.flyfish.dev.customer.domain.po;

import group.flyfish.dev.common.base.reactive.AuditDomain;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 公众号用户动态。
 */
@Getter
@Setter
@Table("customer_wechat_activity")
public class CustomerWechatActivity extends AuditDomain {

    /**
     * 绑定的平台用户 ID；未绑定微信时为空。
     */
    @Column("user_id")
    private Long userId;

    /**
     * 微信公众号 openid，用于识别同一公众号用户。
     */
    @Column("wechat_openid")
    private String wechatOpenid;

    /**
     * 微信 unionId，用于后续跨应用归一化。
     */
    @Column("wechat_union_id")
    private String wechatUnionId;

    /**
     * 用户展示名快照。
     */
    @Column("display_name")
    private String displayName;

    /**
     * 用户头像快照。
     */
    private String avatar;

    /**
     * 动态类型，取值见 {@link ActivityType}。
     */
    @Column("activity_type")
    private String activityType;

    /**
     * 微信消息类型，例如 text、image、event、location。
     */
    @Column("message_type")
    private String messageType;

    /**
     * 微信事件类型，例如 subscribe、unsubscribe、SCAN。
     */
    @Column("event_type")
    private String eventType;

    /**
     * 事件 key，例如扫码场景值。
     */
    @Column("event_key")
    private String eventKey;

    /**
     * 动态标题，用于列表快速扫读。
     */
    private String title;

    /**
     * 动态正文或摘要。
     */
    private String content;

    /**
     * 微信消息 ID，用于排查与去重。
     */
    @Column("wechat_msg_id")
    private String wechatMsgId;

    /**
     * 原始微信明文 XML，便于问题追溯。
     */
    @Column("raw_payload")
    private String rawPayload;

    public enum ActivityType {
        SUBSCRIBE,
        UNSUBSCRIBE,
        SCAN,
        TEXT,
        IMAGE,
        LOCATION,
        EVENT,
        MESSAGE
    }
}

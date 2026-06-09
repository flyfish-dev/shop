package group.flyfish.dev.customer.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import group.flyfish.dev.customer.domain.po.CustomerWechatActivity;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CustomerWechatActivityVo {

    /**
     * 动态 ID。
     */
    private Long id;

    /**
     * 绑定的平台用户 ID。
     */
    private Long userId;

    /**
     * 微信公众号 openid。
     */
    private String wechatOpenid;

    /**
     * 用户展示名。
     */
    private String displayName;

    /**
     * 用户头像。
     */
    private String avatar;

    /**
     * 动态类型。
     */
    private String activityType;

    /**
     * 微信消息类型。
     */
    private String messageType;

    /**
     * 微信事件类型。
     */
    private String eventType;

    /**
     * 微信事件 key。
     */
    private String eventKey;

    /**
     * 动态标题。
     */
    private String title;

    /**
     * 动态正文。
     */
    private String content;

    /**
     * 微信消息 ID。
     */
    private String wechatMsgId;

    /**
     * 创建时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime createTime;

    public CustomerWechatActivityVo(CustomerWechatActivity activity) {
        this.id = activity.getId();
        this.userId = activity.getUserId();
        this.wechatOpenid = activity.getWechatOpenid();
        this.displayName = activity.getDisplayName();
        this.avatar = activity.getAvatar();
        this.activityType = activity.getActivityType();
        this.messageType = activity.getMessageType();
        this.eventType = activity.getEventType();
        this.eventKey = activity.getEventKey();
        this.title = activity.getTitle();
        this.content = activity.getContent();
        this.wechatMsgId = activity.getWechatMsgId();
        this.createTime = activity.getCreateTime();
    }
}

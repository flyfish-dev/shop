package group.flyfish.dev.customer.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import group.flyfish.dev.common.json.JacksonUtils;
import group.flyfish.dev.common.upload.domain.vo.FileAttachmentVo;
import group.flyfish.dev.customer.domain.po.CustomerMessage;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import tools.jackson.core.type.TypeReference;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class CustomerMessageVo {

    /**
     * 消息 ID。
     */
    private Long id;

    /**
     * 所属客服会话 ID。
     */
    private Long conversationId;

    /**
     * 消息所属平台用户 ID。
     */
    private Long userId;

    /**
     * 消息方向，INBOUND 表示客户发给平台，OUTBOUND 表示平台发给客户。
     */
    private String direction;

    /**
     * 消息渠道，例如 WECHAT、WEB。
     */
    private String channel;

    /**
     * 发送方角色，例如 USER、ADMIN、SYSTEM。
     */
    private String senderRole;

    /**
     * 发送方平台用户 ID。
     */
    private Long senderId;

    /**
     * 发送方展示名称，后端根据角色和用户资料统一补齐。
     */
    private String senderName;

    /**
     * 发送方头像地址。
     */
    private String senderAvatar;

    /**
     * 消息类型，当前主要为 text。
     */
    private String messageType;

    /**
     * 消息正文。
     */
    private String content;

    /**
     * 消息附件。
     */
    private List<FileAttachmentVo> attachments;

    /**
     * 关联业务类型。
     */
    private String relatedType;

    /**
     * 关联业务编号。
     */
    private String relatedNo;

    /**
     * 发送状态。
     */
    private String sendStatus;

    /**
     * 发送失败时的错误信息。
     */
    private String errorMessage;

    /**
     * 消息创建时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime createTime;

    public CustomerMessageVo(CustomerMessage message) {
        this.id = message.getId();
        this.conversationId = message.getConversationId();
        this.userId = message.getUserId();
        this.direction = message.getDirection();
        this.channel = message.getChannel();
        this.senderRole = message.getSenderRole();
        this.senderId = message.getSenderId();
        this.messageType = message.getMessageType();
        this.content = message.getContent();
        this.attachments = parseAttachments(message.getAttachments());
        this.relatedType = message.getRelatedType();
        this.relatedNo = message.getRelatedNo();
        this.sendStatus = message.getSendStatus();
        this.errorMessage = message.getErrorMessage();
        this.createTime = message.getCreateTime();
    }

    public void applySender(String senderName, String senderAvatar) {
        this.senderName = senderName;
        this.senderAvatar = senderAvatar;
    }

    private List<FileAttachmentVo> parseAttachments(String value) {
        if (StringUtils.isBlank(value)) {
            return List.of();
        }
        try {
            return JacksonUtils.readValue(value, new TypeReference<>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }
}

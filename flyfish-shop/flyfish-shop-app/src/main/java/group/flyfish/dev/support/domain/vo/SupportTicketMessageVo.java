package group.flyfish.dev.support.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import group.flyfish.dev.common.json.JacksonUtils;
import group.flyfish.dev.common.upload.domain.vo.FileAttachmentVo;
import group.flyfish.dev.support.domain.po.SupportTicketMessage;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import tools.jackson.core.type.TypeReference;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class SupportTicketMessageVo {

    private Long id;

    private Long senderId;

    private String senderRole;

    private String senderName;

    private String senderAvatar;

    private String content;

    private List<FileAttachmentVo> attachments;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime createTime;

    public SupportTicketMessageVo(SupportTicketMessage message, String senderName, String senderAvatar) {
        this.id = message.getId();
        this.senderId = message.getSenderId();
        this.senderRole = message.getSenderRole();
        this.senderName = senderName;
        this.senderAvatar = senderAvatar;
        this.content = message.getContent();
        this.attachments = parseAttachments(message.getAttachments());
        this.createTime = message.getCreateTime();
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

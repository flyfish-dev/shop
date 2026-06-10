package group.flyfish.dev.customer.domain.dto;

import group.flyfish.dev.common.upload.domain.vo.FileAttachmentVo;
import lombok.Data;

import java.util.List;

@Data
public class CustomerSocketCommand {

    /**
     * WebSocket 命令类型，例如 SYNC、OPEN、SEND、READ、CLOSE。
     */
    private String type;

    /**
     * 管理员正在操作的会话 ID。
     */
    private Long conversationId;

    /**
     * 管理员从订单或工单入口发起沟通时指定的客户用户 ID。
     */
    private Long userId;

    /**
     * SEND 命令携带的消息内容。
     */
    private String content;

    /**
     * SEND 命令携带的消息类型：text、markdown、image、file。
     */
    private String messageType;

    /**
     * SEND 命令携带的附件列表。
     */
    private List<FileAttachmentVo> attachments;

    /**
     * SEND 命令关联的业务类型。
     */
    private String relatedType;

    /**
     * SEND 命令关联的业务编号。
     */
    private String relatedNo;
}

package group.flyfish.dev.customer.domain.dto;

import group.flyfish.dev.common.upload.domain.vo.FileAttachmentVo;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CustomerMessageSendDto {

    /**
     * 待发送的文本消息内容；带附件时允许为空。
     */
    @Size(max = 4096, message = "消息内容不能超过4096个字符")
    private String content;

    /**
     * 消息类型：text、markdown、image、file。
     */
    private String messageType;

    /**
     * 已上传附件列表，前端先上传文件再随消息提交附件元数据。
     */
    private List<FileAttachmentVo> attachments;

    /**
     * 关联业务类型，例如 ORDER、TICKET。
     */
    private String relatedType;

    /**
     * 关联业务编号，例如订单号或工单号。
     */
    private String relatedNo;
}

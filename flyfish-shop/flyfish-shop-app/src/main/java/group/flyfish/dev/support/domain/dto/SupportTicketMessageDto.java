package group.flyfish.dev.support.domain.dto;

import group.flyfish.dev.common.upload.domain.vo.FileAttachmentVo;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class SupportTicketMessageDto {

    /**
     * 回复内容；上传附件时允许为空，由服务层统一校验至少有文字或附件。
     */
    @Size(max = 4096, message = "回复内容不能超过4096个字符")
    private String content;

    /**
     * 回复附件。
     */
    private List<FileAttachmentVo> attachments;
}

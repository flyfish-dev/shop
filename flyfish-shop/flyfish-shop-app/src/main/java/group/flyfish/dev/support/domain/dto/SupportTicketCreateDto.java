package group.flyfish.dev.support.domain.dto;

import group.flyfish.dev.common.upload.domain.vo.FileAttachmentVo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class SupportTicketCreateDto {

    @NotBlank(message = "请输入工单标题")
    @Size(max = 120, message = "工单标题不能超过120个字符")
    private String title;

    @Size(max = 40, message = "工单类型不能超过40个字符")
    private String category;

    private String priority;

    @Size(max = 512, message = "联系方式不能超过512个字符")
    private String contact;

    /**
     * 问题内容；上传附件时允许为空，由服务层统一校验至少有文字或附件。
     */
    @Size(max = 4096, message = "问题内容不能超过4096个字符")
    private String content;

    /**
     * 工单首条消息附件。
     */
    private List<FileAttachmentVo> attachments;
}

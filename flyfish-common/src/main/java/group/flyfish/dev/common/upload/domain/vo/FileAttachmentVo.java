package group.flyfish.dev.common.upload.domain.vo;

import group.flyfish.dev.common.upload.domain.FileMetadata;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
public class FileAttachmentVo {

    /**
     * 文件元数据 ID，便于后续追溯实际上传记录。
     */
    private Long id;

    /**
     * 原始文件名，用于聊天和工单页面展示。
     */
    private String name;

    /**
     * 文件访问地址，前端通过该地址展示图片或下载附件。
     */
    private String url;

    /**
     * 文件大小，单位字节。
     */
    private Long size;

    /**
     * MIME 类型，例如 image/png、application/pdf。
     */
    private String contentType;

    /**
     * 是否为图片类型，前端据此选择缩略图或普通附件样式。
     */
    private Boolean image;

    public static FileAttachmentVo from(FileMetadata metadata) {
        FileAttachmentVo vo = new FileAttachmentVo();
        vo.setId(metadata.getId());
        vo.setName(metadata.getOriginalFilename());
        vo.setUrl(metadata.getUrl());
        vo.setSize(metadata.getSize());
        vo.setContentType(metadata.getContentType());
        vo.setImage(StringUtils.startsWithIgnoreCase(metadata.getContentType(), "image/"));
        return vo;
    }
}

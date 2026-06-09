package group.flyfish.dev.common.upload.domain;

import group.flyfish.dev.common.base.reactive.AuditDomain;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 文件元数据
 *
 * @author wangyu
 */
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@Table("file_metadata")
public class FileMetadata extends AuditDomain {

    /**
     * 文件哈希值(sha256)
     */
    private String hash;

    /**
     * 原始文件名
     */
    private String originalFilename;

    /**
     * 存储路径
     */
    private String path;

    /**
     * 文件大小(字节)
     */
    private Long size;

    /**
     * 文件类型
     */
    private String contentType;

    /**
     * 访问URL
     */
    private String url;
}
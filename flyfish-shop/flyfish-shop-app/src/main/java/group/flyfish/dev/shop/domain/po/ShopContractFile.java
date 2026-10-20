package group.flyfish.dev.shop.domain.po;

import group.flyfish.dev.annotations.data.Property;
import group.flyfish.dev.common.base.reactive.AuditDomain;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 合同附件文件。
 */
@Getter
@Setter
@Table("shop_contract_file")
public class ShopContractFile extends AuditDomain {

    @Property("合同id")
    @Column("contract_id")
    private Long contractId;

    @Property("逻辑文档标识")
    @Column("document_key")
    private String documentKey;

    @Property("合同文件语言")
    private String locale;

    @Property("文件名称")
    @Column("file_name")
    private String fileName;

    @Property("文件地址")
    @Column("file_url")
    private String fileUrl;

    @Property("文件SHA-256")
    @Column("file_hash")
    private String fileHash;

    @Property("逻辑文档版本")
    @Column("file_version")
    private Integer fileVersion;

    @Property("文件类型")
    @Column("content_type")
    private String contentType;

    @Property("文件大小")
    @Column("file_size")
    private Long fileSize;

    @Property("排序")
    private Integer sort;

    @Property("启用状态")
    private Boolean enabled;
}

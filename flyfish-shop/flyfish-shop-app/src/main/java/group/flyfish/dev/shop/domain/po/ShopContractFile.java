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

    @Property("文件名称")
    @Column("file_name")
    private String fileName;

    @Property("文件地址")
    @Column("file_url")
    private String fileUrl;

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

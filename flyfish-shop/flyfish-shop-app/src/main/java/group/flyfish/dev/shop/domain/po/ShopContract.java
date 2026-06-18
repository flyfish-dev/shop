package group.flyfish.dev.shop.domain.po;

import group.flyfish.dev.annotations.data.Property;
import group.flyfish.dev.common.base.reactive.AuditDomain;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 小铺合同文档。
 */
@Getter
@Setter
@Table("shop_contract")
public class ShopContract extends AuditDomain {

    @Property("合同名称")
    private String name;

    @Property("合同类型")
    private Type type;

    @Property("合同描述")
    private String description;

    @Property("合同标签")
    private String tags;

    @Property("启用状态")
    private Boolean enabled;

    @Property("排序")
    private Integer sort;

    /**
     * 合同类型按软件和企业采购常见场景收敛，便于后台管理和后续归档检索。
     */
    @Getter
    public enum Type {

        PURCHASE_AGREEMENT("采购合同"),
        SOFTWARE_LICENSE("软件许可协议"),
        SERVICE_AGREEMENT("服务协议"),
        SUBSCRIPTION_AGREEMENT("订阅服务协议"),
        CONFIDENTIALITY_AGREEMENT("保密协议"),
        DATA_PROCESSING_AGREEMENT("数据处理协议"),
        ACCEPTANCE_CONFIRMATION("验收确认书"),
        CUSTOM("其他合同");

        private final String title;

        Type(String title) {
            this.title = title;
        }
    }
}

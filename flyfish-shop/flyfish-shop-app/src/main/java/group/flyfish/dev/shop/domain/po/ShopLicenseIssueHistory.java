package group.flyfish.dev.shop.domain.po;

import group.flyfish.dev.annotations.data.Property;
import group.flyfish.dev.common.base.reactive.AuditDomain;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 授权许可证签发历史。
 *
 * <p>主许可证表只保存当前可下载版本，本表完整保留初始签发和管理员补发版本，
 * 用于客户争议处理、安全审计和授权追溯。</p>
 */
@Getter
@Setter
@Table("shop_license_issue_history")
public class ShopLicenseIssueHistory extends AuditDomain {

    @Property("订单号")
    @Column("order_no")
    private String orderNo;

    @Property("授权编号")
    @Column("license_no")
    private String licenseNo;

    @Property("商品ID")
    @Column("item_id")
    private Long itemId;

    @Property("购买用户ID")
    @Column("buyer_id")
    private Long buyerId;

    @Property("签发类型")
    @Column("issue_type")
    private String issueType;

    @Property("补发原因")
    private String reason;

    @Property("是否永久有效")
    private Boolean permanent;

    @Property("签名算法")
    private String algorithm;

    @Property("授权审计Payload")
    private String certificate;

    @Property("授权文件")
    private String signature;

    public enum IssueType {
        ORIGINAL,
        PERMANENT_REISSUE,
        RENEWAL
    }
}

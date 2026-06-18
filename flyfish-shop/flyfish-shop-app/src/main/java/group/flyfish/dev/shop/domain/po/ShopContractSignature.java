package group.flyfish.dev.shop.domain.po;

import group.flyfish.dev.annotations.data.Property;
import group.flyfish.dev.common.base.reactive.AuditDomain;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 合同阅读与同意留痕。
 */
@Getter
@Setter
@Table("shop_contract_signature")
public class ShopContractSignature extends AuditDomain {

    @Property("签署令牌")
    @Column("sign_token")
    private String signToken;

    @Property("订单号")
    @Column("order_no")
    private String orderNo;

    @Property("商品id")
    @Column("item_id")
    private Long itemId;

    @Property("购买用户id")
    @Column("buyer_id")
    private Long buyerId;

    @Property("合同id")
    @Column("contract_id")
    private Long contractId;

    @Property("合同文件id")
    @Column("contract_file_id")
    private Long contractFileId;

    @Property("合同名称快照")
    @Column("contract_name")
    private String contractName;

    @Property("合同类型快照")
    @Column("contract_type")
    private String contractType;

    @Property("文件名称快照")
    @Column("file_name")
    private String fileName;

    @Property("文件地址快照")
    @Column("file_url")
    private String fileUrl;

    @Property("阅读比例")
    @Column("read_percent")
    private Integer readPercent;

    @Property("签署状态")
    private Status status;

    @Property("同意时间")
    @Column("agreed_time")
    private LocalDateTime agreedTime;

    @Property("客户端IP")
    @Column("client_ip")
    private String clientIp;

    @Property("客户端UA")
    @Column("user_agent")
    private String userAgent;

    public enum Status {

        AGREED, BOUND
    }
}

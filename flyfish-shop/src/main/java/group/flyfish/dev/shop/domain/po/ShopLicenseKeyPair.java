package group.flyfish.dev.shop.domain.po;

import group.flyfish.dev.common.base.reactive.AuditDomain;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 用户授权密钥对。
 * <p>每笔授权订单生成独立密钥对，并由统一根证书签名，方便后续离线或在线验签。</p>
 */
@Getter
@Setter
@Table("shop_license_key_pair")
public class ShopLicenseKeyPair extends AuditDomain {

    /**
     * 授权编号。
     */
    @Column("license_no")
    private String licenseNo;

    /**
     * 订单号。
     */
    @Column("order_no")
    private String orderNo;

    /**
     * 商品 ID。
     */
    @Column("item_id")
    private Long itemId;

    /**
     * 购买用户 ID。
     */
    @Column("buyer_id")
    private Long buyerId;

    /**
     * 根证书 ID。
     */
    @Column("root_id")
    private Long rootId;

    /**
     * 密钥算法，例如 Ed25519。
     */
    private String algorithm;

    /**
     * 授权公钥，Base64 编码。
     */
    @Column("public_key")
    private String publicKey;

    /**
     * 授权私钥，Base64 编码；用户提取后可妥善保存。
     */
    @Column("private_key")
    private String privateKey;

    /**
     * 授权证书 JSON。
     */
    private String certificate;

    /**
     * 根证书对授权证书的签名，Base64 编码。
     */
    private String signature;
}

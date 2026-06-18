package group.flyfish.dev.shop.domain.po;

import group.flyfish.dev.common.base.reactive.AuditDomain;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 授权根证书。
 * <p>系统使用同一套根证书签发用户授权，后续验签只需要根公钥和授权证书内容。</p>
 */
@Getter
@Setter
@Table("shop_license_root")
public class ShopLicenseRoot extends AuditDomain {

    /**
     * 根证书名称。
     */
    private String name;

    /**
     * 签名算法，例如 Ed25519。
     */
    private String algorithm;

    /**
     * 根证书公钥，Base64 编码。
     */
    @Column("public_key")
    private String publicKey;

    /**
     * 根证书私钥，Base64 编码。仅服务端签发授权时使用。
     */
    @Column("private_key")
    private String privateKey;
}

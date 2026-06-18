package group.flyfish.dev.shop.license;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 授权交付配置。
 * <p>开源版不包含商业授权签发器；真实签发配置由私有模块自行声明。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "flyfish.shop.license.external")
public class ExternalLicenseProperties {

    private String product = "license-product";

    private String licenseFileName = "license.lic";
}

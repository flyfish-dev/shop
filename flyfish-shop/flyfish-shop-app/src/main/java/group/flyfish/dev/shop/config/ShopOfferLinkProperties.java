package group.flyfish.dev.shop.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 专享购买链接配置。
 */
@Data
@ConfigurationProperties(prefix = "shop.offer-link")
public class ShopOfferLinkProperties {

    /**
     * 客户打开专享链接时使用的前端站点地址。
     */
    private String portalBaseUrl = "https://shop.example.com";
}

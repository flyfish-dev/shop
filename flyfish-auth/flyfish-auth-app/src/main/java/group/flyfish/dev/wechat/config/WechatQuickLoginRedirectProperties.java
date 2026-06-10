package group.flyfish.dev.wechat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信快捷登录回跳配置。
 *
 * <p>认证模块只负责消费登录码和写入登录态，默认回跳保持业务无关；
 * 小铺等业务模块可以在自己的配置中覆盖默认落点。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "wx.quick-login")
public class WechatQuickLoginRedirectProperties {

    /**
     * 未显式传入 redirect 时的站内默认落点。
     */
    private String defaultRedirect = "/";
}

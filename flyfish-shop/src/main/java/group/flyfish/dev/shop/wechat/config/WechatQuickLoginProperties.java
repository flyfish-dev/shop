package group.flyfish.dev.shop.wechat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 公众号快捷登录配置。
 *
 * <p>公众号消息只能回复一个面向用户可点击的网页入口，因此这里单独维护公网基础地址和
 * 购买关键词默认落点。服务端最终仍会校验 redirect 是否为站内路径，避免被构造成外部跳转。</p>
 *
 * @author wangyu
 */
@Data
@Component
@ConfigurationProperties(prefix = "wx.mp.quick-login")
public class WechatQuickLoginProperties {

    /**
     * 用户从微信里点击快捷登录时访问的公网域名。
     */
    private String baseUrl = "https://dev.flyfish.group";

    /**
     * “购买”关键词登录成功后的默认页面。
     */
    private String purchaseRedirect = "/shop/item-list";

    /**
     * 公众号里生成的快捷登录链接有效期，默认 15 分钟。
     */
    private int expireSeconds = 15 * 60;
}

package group.flyfish.dev.shop.wechat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 微信公众号基础配置。
 *
 * <p>只保留当前业务真实使用到的 AppId、密钥、消息令牌、AES Key 和接口域名。
 * 远程调用统一通过 Spring HTTP Interface 完成，避免把完整 SDK 带入运行时。</p>
 */
@Data
@ConfigurationProperties(prefix = "wx.mp")
public class WechatMpProperties {

    private String appId;

    private String secret;

    private String token;

    private String aesKey;

    private String apiBaseUrl = "https://api.weixin.qq.com";

    private String mpBaseUrl = "https://mp.weixin.qq.com";

    /**
     * 客服二维码素材 ID。
     *
     * <p>如果明确知道微信后台素材的 media_id，可以直接配置该值，系统会跳过按名称查询。</p>
     */
    private String customerServiceMediaId;

}

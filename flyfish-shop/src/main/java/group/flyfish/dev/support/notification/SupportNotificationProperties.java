package group.flyfish.dev.support.notification;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "support.notification")
public class SupportNotificationProperties {

    private boolean enabled = true;

    /**
     * 用户和管理员在消息中打开工单详情时使用的前端域名。
     */
    private String portalBaseUrl = "https://dev.flyfish.group";

    private Mail mail = new Mail();

    private Wechat wechat = new Wechat();

    @Data
    public static class Mail {

        private boolean enabled = true;

        /**
         * 发件人地址。为空时由 Spring Mail 的账号配置决定。
         */
        private String from;

        /**
         * 管理员邮箱列表，支持环境变量中使用英文逗号分隔。
         */
        private List<String> adminRecipients = new ArrayList<>();
    }

    @Data
    public static class Wechat {

        private boolean enabled = true;

        /**
         * 管理员公众号 openid 列表，支持环境变量中使用英文逗号分隔。
         */
        private List<String> adminOpenids = new ArrayList<>();

        /**
         * 飞鱼小铺维护者的 Gitea 账号 ID。系统会自动查找该用户绑定的微信 openid。
         */
        private String maintainerGiteaOpenid = "1";
    }
}

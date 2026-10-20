package group.flyfish.dev.customer.notification;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 客服离线提醒配置。
 *
 * <p>邮件和 Webhook 可以独立启停。提醒与离线自动回复共享冷却周期，既保证管理员能及时获知
 * 新咨询，也避免同一客户连续发送消息时形成提醒风暴。</p>
 */
@Data
@ConfigurationProperties(prefix = "customer.service.offline.notification")
public class CustomerOfflineNotificationProperties {

    /**
     * 是否启用客服离线提醒总开关。
     */
    private boolean enabled = true;

    /**
     * 同一会话重复发送离线自动回复和提醒的冷却周期。
     */
    private Duration cooldown = Duration.ofHours(8);

    /**
     * 管理员处理客户会话时使用的前端域名。
     */
    private String portalBaseUrl = "https://shop.example.com";

    /**
     * 邮件提醒配置。
     */
    private Mail mail = new Mail();

    /**
     * Webhook 提醒配置。
     */
    private Webhook webhook = new Webhook();

    @Data
    public static class Mail {

        /**
         * 是否启用邮件提醒。
         */
        private boolean enabled = true;

        /**
         * 发件人地址；为空时使用 Spring Mail 账号配置。
         */
        private String from;

        /**
         * 管理员收件人列表。
         */
        private List<String> recipients = new ArrayList<>();
    }

    @Data
    public static class Webhook {

        /**
         * 是否启用 Webhook 提醒。
         */
        private boolean enabled;

        /**
         * Webhook 接收地址列表；生产环境默认只允许 HTTPS。
         */
        private List<URI> urls = new ArrayList<>();

        /**
         * HMAC-SHA256 签名密钥；为空时不发送签名头。
         */
        private String secret;

        /**
         * 单个 Webhook 请求的总超时时间。
         */
        private Duration timeout = Duration.ofSeconds(5);

        /**
         * 瞬时网络错误或 5xx/429 响应的最大重试次数。
         */
        private int maxRetries = 1;

        /**
         * 首次重试等待时间，后续采用指数退避。
         */
        private Duration retryBackoff = Duration.ofMillis(500);

        /**
         * 是否允许 HTTP 地址，仅建议本地联调时开启。
         */
        private boolean allowInsecureHttp;
    }
}

package group.flyfish.dev.user.email;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "email.magic-link")
public class EmailMagicLinkProperties {

    private boolean enabled = true;

    private String baseUrl = "http://127.0.0.1:9999";

    private String from;

    private String subject = "飞鱼邮箱快速登录";

    private Duration expiresIn = Duration.ofMinutes(15);
}

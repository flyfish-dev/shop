package group.flyfish.dev.user.email;

import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.user.config.JwtProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailMagicLinkTokenServiceTest {

    @Test
    void issueAndConsumeSignedTokenOnce() {
        EmailMagicLinkTokenService service = tokenService(Duration.ofMinutes(15));

        String token = service.issue("user@example.com", 10L, "/account/profile");
        EmailMagicLinkPayload payload = service.consume(token);

        assertThat(payload.email()).isEqualTo("user@example.com");
        assertThat(payload.bindUserId()).isEqualTo(10L);
        assertThat(payload.redirect()).isEqualTo("/account/profile");
        assertThat(payload.nonce()).isNotBlank();
        assertThatThrownBy(() -> service.consume(token))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已使用");
    }

    @Test
    void rejectTamperedToken() {
        EmailMagicLinkTokenService service = tokenService(Duration.ofMinutes(15));
        String token = service.issue("user@example.com", null, "/");

        assertThatThrownBy(() -> service.consume(token + "x"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无效");
    }

    @Test
    void rejectExpiredToken() {
        EmailMagicLinkTokenService service = tokenService(Duration.ofMillis(-1));
        String token = service.issue("user@example.com", null, "/");

        assertThatThrownBy(() -> service.consume(token))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("过期");
    }

    private EmailMagicLinkTokenService tokenService(Duration expiresIn) {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret("email-magic-link-test-secret");
        EmailMagicLinkProperties properties = new EmailMagicLinkProperties();
        properties.setExpiresIn(expiresIn);
        return new EmailMagicLinkTokenService(jwtProperties, properties);
    }
}

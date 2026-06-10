package group.flyfish.dev.user.email;

import group.flyfish.dev.user.domain.UserToken;
import group.flyfish.dev.user.service.PortalUserService;
import group.flyfish.dev.common.exception.BusinessException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailMagicLinkServiceTest {

    @Test
    void sendNormalizesAddressAndSendsMagicLink() {
        EmailMagicLinkProperties properties = properties();
        EmailMagicLinkTokenService tokenService = mock(EmailMagicLinkTokenService.class);
        when(tokenService.issue("user@example.com", null, "/shop/item-list")).thenReturn("signed-token");
        PortalUserService portalUserService = mock(PortalUserService.class);
        CapturingMailSender mailSender = new CapturingMailSender();
        EmailMagicLinkService service = new EmailMagicLinkService(properties, tokenService, portalUserService,
                mailSenderProvider(mailSender));
        EmailMagicLinkRequest request = new EmailMagicLinkRequest();
        request.setEmail(" User@Example.COM ");
        request.setRedirect("/shop/item-list");

        StepVerifier.create(service.send(request, null))
                .assertNext(result -> {
                    assertThat(result.getEmail()).isEqualTo("user@example.com");
                    assertThat(result.getMaskedEmail()).isEqualTo("us***@example.com");
                    assertThat(result.getExpiresInSeconds()).isEqualTo(900L);
                    assertThat(result.getResendCooldownSeconds()).isEqualTo(120L);
                })
                .verifyComplete();

        assertThat(mailSender.lastMessage.getTo()).containsExactly("user@example.com");
        assertThat(mailSender.lastMessage.getText()).contains("/email/magic-links/consume?token=signed-token");
    }

    @Test
    void sendRejectsSameAddressDuringCooldown() {
        EmailMagicLinkProperties properties = properties();
        EmailMagicLinkTokenService tokenService = mock(EmailMagicLinkTokenService.class);
        when(tokenService.issue("user@example.com", null, "/")).thenReturn("signed-token");
        PortalUserService portalUserService = mock(PortalUserService.class);
        CapturingMailSender mailSender = new CapturingMailSender();
        EmailMagicLinkService service = new EmailMagicLinkService(properties, tokenService, portalUserService,
                mailSenderProvider(mailSender));
        EmailMagicLinkRequest request = new EmailMagicLinkRequest();
        request.setEmail("user@example.com");

        StepVerifier.create(service.send(request, null))
                .expectNextCount(1)
                .verifyComplete();

        StepVerifier.create(service.send(request, null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) error).getCode()).isEqualTo("EMAIL_MAGIC_LINK_COOLDOWN");
                    assertThat(error.getMessage()).contains("秒后再试");
                })
                .verify();
    }

    @Test
    void consumeLoginTokenRegistersOrLogsInEmail() {
        EmailMagicLinkTokenService tokenService = mock(EmailMagicLinkTokenService.class);
        when(tokenService.consume("login-token")).thenReturn(new EmailMagicLinkPayload("user@example.com", "nonce-1",
                null, "/shop/item-list", Instant.now().plusSeconds(600)));
        PortalUserService portalUserService = mock(PortalUserService.class);
        UserToken userToken = new UserToken("access-token", Date.from(Instant.now().plusSeconds(600)));
        when(portalUserService.registerOrLoginEmail("user@example.com")).thenReturn(Mono.just(userToken));
        EmailMagicLinkService service = new EmailMagicLinkService(properties(), tokenService, portalUserService,
                mailSenderProvider(null));

        StepVerifier.create(service.consume("login-token"))
                .assertNext(result -> {
                    assertThat(result.token()).isEqualTo(userToken);
                    assertThat(result.redirect()).isEqualTo("/shop/item-list");
                })
                .verifyComplete();

        verify(portalUserService).registerOrLoginEmail("user@example.com");
    }

    @Test
    void consumeBindTokenBindsEmailToCurrentUser() {
        EmailMagicLinkTokenService tokenService = mock(EmailMagicLinkTokenService.class);
        when(tokenService.consume("bind-token")).thenReturn(new EmailMagicLinkPayload("user@example.com", "nonce-1",
                8L, "/account/profile", Instant.now().plusSeconds(600)));
        PortalUserService portalUserService = mock(PortalUserService.class);
        UserToken userToken = new UserToken("access-token", Date.from(Instant.now().plusSeconds(600)));
        when(portalUserService.bindEmailAuthorization("user@example.com", 8L)).thenReturn(Mono.just(userToken));
        EmailMagicLinkService service = new EmailMagicLinkService(properties(), tokenService, portalUserService,
                mailSenderProvider(null));

        StepVerifier.create(service.consume("bind-token"))
                .assertNext(result -> assertThat(result.token()).isEqualTo(userToken))
                .verifyComplete();

        verify(portalUserService).bindEmailAuthorization("user@example.com", 8L);
    }

    private EmailMagicLinkProperties properties() {
        EmailMagicLinkProperties properties = new EmailMagicLinkProperties();
        properties.setBaseUrl("https://shop.example.com");
        properties.setFrom("noreply@example.com");
        properties.setExpiresIn(Duration.ofMinutes(15));
        return properties;
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<JavaMailSender> mailSenderProvider(JavaMailSender mailSender) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mailSender);
        return provider;
    }

    private static class CapturingMailSender implements JavaMailSender {

        private SimpleMailMessage lastMessage;

        @Override
        public MimeMessage createMimeMessage() {
            throw new UnsupportedOperationException();
        }

        @Override
        public MimeMessage createMimeMessage(InputStream contentStream) throws MailException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void send(MimeMessage mimeMessage) throws MailException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void send(MimeMessage... mimeMessages) throws MailException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void send(MimeMessagePreparator mimeMessagePreparator) throws MailException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void send(MimeMessagePreparator... mimeMessagePreparators) throws MailException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void send(SimpleMailMessage simpleMessage) throws MailException {
            this.lastMessage = new SimpleMailMessage(simpleMessage);
        }

        @Override
        public void send(SimpleMailMessage... simpleMessages) throws MailException {
            Stream.of(simpleMessages).forEach(this::send);
        }
    }
}

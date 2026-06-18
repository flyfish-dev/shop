package group.flyfish.dev.oauth.controller;

import group.flyfish.dev.oauth.config.OAuthProperties;
import group.flyfish.dev.oauth.config.Pac4jProperties;
import group.flyfish.dev.oauth.service.OAuthSessionService;
import group.flyfish.dev.user.config.JwtProperties;
import group.flyfish.dev.auth.api.user.OAuthType;
import group.flyfish.dev.user.domain.UserToken;
import group.flyfish.dev.user.domain.bo.PendingOAuthBinding;
import group.flyfish.dev.user.email.EmailMagicLinkProperties;
import group.flyfish.dev.user.service.PortalUserService;
import group.flyfish.dev.user.service.TokenBlockStore;
import group.flyfish.dev.user.service.impl.TokenProvider;
import org.junit.jupiter.api.Test;
import org.pac4j.core.config.Config;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.result.view.Rendering;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CallbackControllerTest {

    @Test
    void providersExposeEmailMagicLinkSwitch() {
        EmailMagicLinkProperties emailProperties = new EmailMagicLinkProperties();
        emailProperties.setEnabled(true);
        CallbackController controller = new CallbackController(mock(Config.class), new Pac4jProperties(),
                new OAuthProperties(), new JwtProperties(), mock(PortalUserService.class),
                new TokenProvider(new JwtProperties(), mock(TokenBlockStore.class)),
                mock(OAuthSessionService.class), emailProperties);

        Map<String, Boolean> providers = controller.providers().getResult();

        assertEquals(Boolean.TRUE, providers.get("wechat"));
        assertEquals(Boolean.TRUE, providers.get("email"));
        assertEquals(Boolean.FALSE, providers.get("github"));
    }

    @Test
    void confirmBindAcceptsTicketFromFormBody() {
        PortalUserService portalUserService = mock(PortalUserService.class);
        when(portalUserService.confirmBindAuthorization(argThat(binding -> "openid-1".equals(binding.openid()))))
                .thenReturn(Mono.just(new UserToken("token-value", Date.from(Instant.now().plusSeconds(600)))));
        CallbackController controller = new CallbackController(mock(Config.class), new Pac4jProperties(),
                new OAuthProperties(), new JwtProperties(), portalUserService,
                new TokenProvider(new JwtProperties(), mock(TokenBlockStore.class)),
                mock(OAuthSessionService.class), new EmailMagicLinkProperties());
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/oauth/bind/confirm")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("ticket=ticket-1"));
        PendingOAuthBinding pending = new PendingOAuthBinding(1L, OAuthType.GITHUB, "openid-1", "{}",
                "/account/profile", Instant.now().plusSeconds(600));
        Map<String, PendingOAuthBinding> pendingBindings = new LinkedHashMap<>();
        pendingBindings.put("ticket-1", pending);
        exchange.getSession().block().getAttributes().put("oauth_pending_bindings", pendingBindings);

        Rendering rendering = controller.confirmBind(null, exchange).block();

        assertEquals("oauth/redirect", rendering.view());
        verify(portalUserService).confirmBindAuthorization(argThat(binding -> "openid-1".equals(binding.openid())));
    }
}

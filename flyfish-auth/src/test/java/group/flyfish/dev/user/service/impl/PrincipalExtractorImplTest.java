package group.flyfish.dev.user.service.impl;

import group.flyfish.dev.user.config.JwtProperties;
import group.flyfish.dev.user.domain.UserToken;
import group.flyfish.dev.user.service.PortalUserService;
import org.springframework.http.HttpCookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class PrincipalExtractorImplTest {

    @Test
    void ignoresBlockedTokenWhenExtractingCurrentUser() {
        TokenProvider tokenProvider = tokenProvider();
        UserToken userToken = tokenProvider.createToken(1001L).block();
        assertNotNull(userToken);
        MockServerWebExchange exchange = exchange(userToken.getToken());

        tokenProvider.removeToken(exchange).block();

        PrincipalExtractorImpl extractor = new PrincipalExtractorImpl(tokenProvider, mock(PortalUserService.class));
        StepVerifier.create(extractor.extractUserId(exchange(userToken.getToken())))
                .verifyComplete();
    }

    @Test
    void fallsBackToValidCookieWhenHeaderTokenIsBlocked() {
        TokenProvider tokenProvider = tokenProvider();
        UserToken blockedHeaderToken = tokenProvider.createToken(1001L).block();
        UserToken cookieToken = tokenProvider.createToken(1002L).block();
        assertNotNull(blockedHeaderToken);
        assertNotNull(cookieToken);

        tokenProvider.removeToken(exchange(blockedHeaderToken.getToken())).block();

        PrincipalExtractorImpl extractor = new PrincipalExtractorImpl(tokenProvider, mock(PortalUserService.class));
        StepVerifier.create(extractor.extractUserId(exchange(blockedHeaderToken.getToken(), cookieToken.getToken())))
                .expectNext(1002L)
                .verifyComplete();
    }

    private MockServerWebExchange exchange(String token) {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/portal/users/current")
                .header(TokenProvider.AUTHORIZATION_HEADER, "Bearer " + token));
    }

    private MockServerWebExchange exchange(String headerToken, String cookieToken) {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/portal/users/current")
                .header(TokenProvider.AUTHORIZATION_HEADER, "Bearer " + headerToken)
                .cookie(new HttpCookie(TokenProvider.AUTHORIZATION_COOKIE, cookieToken)));
    }

    private TokenProvider tokenProvider() {
        TokenProvider provider = new TokenProvider(jwtProperties(), new MemoryTokenBlockStore());
        provider.afterPropertiesSet();
        return provider;
    }

    private JwtProperties jwtProperties() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-secret-for-principal-extractor");
        properties.setTokenValidityInSeconds(60);
        return properties;
    }
}

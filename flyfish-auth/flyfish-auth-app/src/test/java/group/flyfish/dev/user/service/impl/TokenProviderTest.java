package group.flyfish.dev.user.service.impl;

import group.flyfish.dev.user.config.JwtProperties;
import group.flyfish.dev.user.domain.ParsedToken;
import group.flyfish.dev.user.domain.UserToken;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpCookie;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TokenProviderTest {

    @Test
    void createsAndParsesToken() {
        TokenProvider provider = tokenProvider();

        UserToken userToken = provider.createToken(1001L).block();
        assertNotNull(userToken);

        Optional<ParsedToken> parsed = provider.parseToken(userToken.getToken());
        assertTrue(parsed.isPresent());
        assertEquals("1001", parsed.get().subject());
        assertNotNull(parsed.get().id());
        assertEquals(userToken.getExpire().getTime() / 1000, parsed.get().expiration().getTime() / 1000);
    }

    @Test
    void rejectsTamperedTokenWithoutThrowing() {
        TokenProvider provider = tokenProvider();
        UserToken userToken = provider.createToken(3003L).block();
        assertNotNull(userToken);

        String[] parts = userToken.getToken().split("\\.");
        String brokenPayload = parts[1].substring(0, parts[1].length() - 1) + "A";
        assertTrue(provider.parseToken(parts[0] + "." + brokenPayload + "." + parts[2]).isEmpty());
    }

    @Test
    void logoutBlocksHeaderAndCookieTokens() {
        TokenProvider provider = tokenProvider();
        UserToken headerToken = provider.createToken(1001L).block();
        UserToken cookieToken = provider.createToken(1002L).block();
        assertNotNull(headerToken);
        assertNotNull(cookieToken);

        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/portal/users/logout")
                .header(TokenProvider.AUTHORIZATION_HEADER, "Bearer " + headerToken.getToken())
                .cookie(new HttpCookie(TokenProvider.AUTHORIZATION_COOKIE, cookieToken.getToken())));

        provider.removeToken(exchange).block();

        assertFalse(provider.validateToken(headerToken.getToken()).block());
        assertFalse(provider.validateToken(cookieToken.getToken()).block());
    }

    @Test
    void logoutClearsHostAndRootDomainCookiesInProductionDomain() {
        TokenProvider provider = tokenProvider();
        UserToken cookieToken = provider.createToken(1002L).block();
        assertNotNull(cookieToken);

        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/portal/users/logout")
                .header("X-Forwarded-Host", "shop.example.com")
                .header("X-Forwarded-Proto", "https")
                .cookie(new HttpCookie(TokenProvider.AUTHORIZATION_COOKIE, cookieToken.getToken())));

        provider.removeToken(exchange).block();

        List<ResponseCookie> cookies = exchange.getResponse().getCookies().get(TokenProvider.AUTHORIZATION_COOKIE);
        assertNotNull(cookies);
        assertEquals(2, cookies.size());
        assertTrue(cookies.stream().anyMatch(cookie -> cookie.getDomain() == null));
        assertTrue(cookies.stream().anyMatch(cookie -> "flyfish.group".equals(cookie.getDomain())));
        assertTrue(cookies.stream().allMatch(cookie -> cookie.getMaxAge().isZero()));
        assertTrue(cookies.stream().allMatch(ResponseCookie::isSecure));
    }

    private TokenProvider tokenProvider() {
        TokenProvider provider = new TokenProvider(jwtProperties(), new MemoryTokenBlockStore());
        provider.afterPropertiesSet();
        return provider;
    }

    private JwtProperties jwtProperties() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-secret-for-token-provider");
        properties.setTokenValidityInSeconds(60);
        return properties;
    }
}

package group.flyfish.dev.user.service.impl;

import group.flyfish.dev.common.utils.IdGenerators;
import group.flyfish.dev.user.config.JwtProperties;
import group.flyfish.dev.user.domain.ParsedToken;
import group.flyfish.dev.user.domain.UserToken;
import group.flyfish.dev.user.service.TokenBlockStore;
import group.flyfish.dev.user.service.TokenService;
import group.flyfish.dev.user.support.JwtCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.http.ResponseCookie;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * 门户用户 token 服务。
 *
 * <p>本类只负责业务语义：从请求里取 Bearer token、签发用户 token、退出登录时拉黑 token、
 * 校验 token 是否仍可使用。JWT 标准编解码、ES256 签名和验签细节统一交给 {@link JwtCodec}。</p>
 *
 * @author wangyu
 */
@Slf4j
@RequiredArgsConstructor
public class TokenProvider implements TokenService, InitializingBean {

    public static final String AUTHORIZATION_HEADER = "Authorization";

    public static final String AUTHORIZATION_COOKIE = "FF_ACCESS_TOKEN";

    private static final String FLYFISH_ROOT_DOMAIN = "flyfish.group";

    private final JwtProperties jwtProperties;

    private final TokenBlockStore tokenBlockStore;

    private JwtCodec jwtCodec;

    @Override
    public void afterPropertiesSet() {
        this.jwtCodec = JwtCodec.fromSecret(jwtProperties.getSecret());
    }

    /**
     * 从 HTTP Authorization 头中读取 Bearer token。
     *
     * @param exchange 当前 WebFlux 请求上下文
     * @return 去掉 Bearer 前缀后的 token；没有登录态时返回空
     */
    public Optional<String> retrieveToken(ServerWebExchange exchange) {
        return retrieveTokens(exchange).stream().findFirst();
    }

    /**
     * 读取请求中携带的全部登录 token。
     *
     * <p>正常请求只会携带一种登录凭据；OAuth 登录后可能同时存在前端
     * Authorization Bearer 与 HttpOnly Cookie。退出登录时必须把两者都纳入处理，
     * 否则清掉本地 token 后 Cookie 仍会把用户重新识别为已登录。</p>
     */
    public List<String> retrieveTokens(ServerWebExchange exchange) {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        String bearerToken = exchange.getRequest().getHeaders().getFirst(AUTHORIZATION_HEADER);
        if (StringUtils.isNotBlank(bearerToken) && bearerToken.startsWith("Bearer ")) {
            String token = StringUtils.trimToNull(bearerToken.substring(7));
            if (token != null) {
                tokens.add(token);
            }
        }
        var tokenCookie = exchange.getRequest().getCookies().getFirst(AUTHORIZATION_COOKIE);
        if (tokenCookie != null && StringUtils.isNotBlank(tokenCookie.getValue())) {
            tokens.add(tokenCookie.getValue());
        }
        String queryToken = StringUtils.firstNonBlank(
                exchange.getRequest().getQueryParams().getFirst("access_token"),
                exchange.getRequest().getQueryParams().getFirst("token"));
        if (StringUtils.isNotBlank(queryToken)) {
            tokens.add(queryToken.trim());
        }
        return List.copyOf(tokens);
    }

    /**
     * 当前请求携带的所有 token 加入黑名单，后续即使签名有效也不可继续使用。
     */
    public Mono<Void> removeToken(ServerWebExchange exchange) {
        clearTokenCookie(exchange);
        return Flux.fromIterable(retrieveTokens(exchange))
                .mapNotNull(token -> parseToken(token).orElse(null))
                .flatMap(token -> tokenBlockStore.block(token.id(), token.expiration()))
                .then();
    }

    /**
     * 将 OAuth 登录得到的 token 写入 HttpOnly Cookie。
     *
     * <p>前端仍会优先使用 Authorization Bearer；Cookie 是兜底登录态，解决 OAuth 回调页极快跳转、
     * 本地存储被浏览器策略或旧状态清理时用户看起来“闪一下又未登录”的问题。</p>
     */
    public void writeTokenCookie(ServerWebExchange exchange, String token, Date expiration) {
        if (StringUtils.isBlank(token) || expiration == null) {
            return;
        }
        Duration maxAge = Duration.between(new Date().toInstant(), expiration.toInstant());
        if (maxAge.isNegative() || maxAge.isZero()) {
            return;
        }
        ResponseCookie cookie = ResponseCookie.from(AUTHORIZATION_COOKIE, token)
                .path("/")
                .httpOnly(true)
                .secure(isSecure(exchange))
                .sameSite("Lax")
                .maxAge(maxAge)
                .build();
        exchange.getResponse().addCookie(cookie);
    }

    private void clearTokenCookie(ServerWebExchange exchange) {
        addExpiredTokenCookie(exchange, null);
        if (isFlyfishDomain(exchange)) {
            // 早期版本曾经写过主域 Cookie。退出时必须同时清理主域，
            // 否则服务重启后内存黑名单消失，旧 Cookie 会把用户重新识别为已登录。
            addExpiredTokenCookie(exchange, FLYFISH_ROOT_DOMAIN);
        }
    }

    private void addExpiredTokenCookie(ServerWebExchange exchange, String domain) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(AUTHORIZATION_COOKIE, "")
                .path("/")
                .httpOnly(true)
                .secure(isSecure(exchange))
                .sameSite("Lax")
                .maxAge(Duration.ZERO);
        if (StringUtils.isNotBlank(domain)) {
            builder.domain(domain);
        }
        exchange.getResponse().addCookie(builder.build());
    }

    private boolean isFlyfishDomain(ServerWebExchange exchange) {
        String host = exchange.getRequest().getHeaders().getFirst("X-Forwarded-Host");
        if (StringUtils.isBlank(host)) {
            host = exchange.getRequest().getURI().getHost();
        }
        if (StringUtils.isBlank(host)) {
            return false;
        }
        String normalizedHost = StringUtils.substringBefore(host, ":").toLowerCase();
        return FLYFISH_ROOT_DOMAIN.equals(normalizedHost) || normalizedHost.endsWith("." + FLYFISH_ROOT_DOMAIN);
    }

    private boolean isSecure(ServerWebExchange exchange) {
        String forwardedProto = exchange.getRequest().getHeaders().getFirst("X-Forwarded-Proto");
        if (StringUtils.isNotBlank(forwardedProto)) {
            return "https".equalsIgnoreCase(forwardedProto);
        }
        String scheme = exchange.getRequest().getURI().getScheme();
        return "https".equalsIgnoreCase(scheme);
    }

    /**
     * 为指定用户签发 token。
     */
    public Mono<UserToken> createToken(Long userId) {
        try {
            long now = System.currentTimeMillis();
            long duration = jwtProperties.getTokenValidityInSeconds() * 1000;
            Date issuedAt = new Date(now);
            Date expiration = new Date(now + duration);
            String token = jwtCodec.encode(String.valueOf(userId), IdGenerators.uuid32(), issuedAt, expiration);
            return Mono.just(new UserToken(token, expiration));
        } catch (RuntimeException e) {
            log.warn("创建JWT失败：{}", e.getMessage());
            return Mono.error(e);
        }
    }

    /**
     * 解析 token。签名错误、格式错误、过期都会返回空。
     */
    public Optional<ParsedToken> parseToken(String token) {
        return jwtCodec.decode(token);
    }

    /**
     * 解析并校验 token 是否仍然可用。
     *
     * <p>只解析 JWT 只能说明签名和过期时间有效，不能说明用户没有退出登录。
     * 退出登录会把 jti 放入黑名单，因此所有需要识别当前用户的入口都必须走
     * 这个方法，避免已退出的 token 继续被当作有效登录态。</p>
     */
    public Mono<ParsedToken> parseAndValidateToken(String authToken) {
        if (StringUtils.isBlank(authToken)) {
            return Mono.empty();
        }
        return Mono.justOrEmpty(parseToken(authToken))
                .flatMap(claims -> tokenBlockStore.isBlock(claims.id())
                        .filter(blocked -> !blocked)
                        .map(ignored -> claims))
                .onErrorResume(e -> Mono.empty());
    }

    /**
     * 校验 token 是否有效，包含签名、过期时间和黑名单三类检查。
     */
    public Mono<Boolean> validateToken(String authToken) {
        return parseAndValidateToken(authToken)
                .map(claims -> true)
                .defaultIfEmpty(false);
    }
}

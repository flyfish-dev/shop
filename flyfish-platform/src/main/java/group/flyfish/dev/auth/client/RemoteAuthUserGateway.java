package group.flyfish.dev.auth.client;

import group.flyfish.dev.auth.api.client.AuthTokenNames;
import group.flyfish.dev.auth.api.client.AuthUserClient;
import group.flyfish.dev.auth.api.client.AuthUserGateway;
import group.flyfish.dev.auth.api.user.OAuthType;
import group.flyfish.dev.auth.api.user.PortalUserOauthVo;
import group.flyfish.dev.auth.api.user.PortalUserVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

@RequiredArgsConstructor
@Slf4j
public class RemoteAuthUserGateway implements AuthUserGateway {

    private static final String CURRENT_USER_ATTRIBUTE = RemoteAuthUserGateway.class.getName() + ".currentUser";

    private final AuthUserClient authUserClient;

    @Override
    @SuppressWarnings("unchecked")
    public Mono<PortalUserVo> current(ServerWebExchange exchange) {
        Object cached = exchange.getAttribute(CURRENT_USER_ATTRIBUTE);
        if (cached instanceof Mono<?> mono) {
            return (Mono<PortalUserVo>) mono;
        }
        Mono<PortalUserVo> current = authenticate(extractTokens(exchange)).cache();
        exchange.getAttributes().put(CURRENT_USER_ATTRIBUTE, current);
        return current;
    }

    @Override
    public Mono<PortalUserVo> authenticate(Collection<String> tokens) {
        return Flux.fromIterable(tokens == null ? List.of() : tokens)
                .map(StringUtils::trimToNull)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .concatMap(token -> authUserClient.current(asBearer(token))
                        .doOnError(e -> log.warn("远程认证服务解析当前用户失败：{}", e.getMessage()))
                        .onErrorResume(e -> Mono.empty()))
                .next();
    }

    @Override
    public Mono<PortalUserVo> getById(Long id) {
        if (id == null || id <= 0) {
            return Mono.empty();
        }
        return authUserClient.getById(id).onErrorResume(e -> Mono.empty());
    }

    @Override
    public Flux<PortalUserVo> listUsers(String keyword) {
        return authUserClient.listUsers(keyword, null)
                .flatMapMany(Flux::fromIterable)
                .onErrorResume(e -> Flux.empty());
    }

    @Override
    public Mono<Long> countUsers() {
        return authUserClient.countUsers()
                .onErrorResume(e -> {
                    log.warn("远程认证服务查询用户总数失败：{}", e.getMessage());
                    return Mono.just(0L);
                });
    }

    @Override
    public Flux<PortalUserVo> findAllByIds(Collection<Long> ids) {
        String joined = joinIds(ids);
        if (StringUtils.isBlank(joined)) {
            return Flux.empty();
        }
        return authUserClient.listUsers(null, joined)
                .flatMapMany(Flux::fromIterable)
                .onErrorResume(e -> Flux.empty());
    }

    @Override
    public Mono<PortalUserOauthVo> authorizationByOpenid(OAuthType type, String openid) {
        if (type == null || StringUtils.isBlank(openid)) {
            return Mono.empty();
        }
        return authUserClient.authorizationByOpenid(type.getCode(), openid)
                .onErrorResume(e -> Mono.empty());
    }

    @Override
    public Flux<PortalUserOauthVo> authorizationsByUser(Long userId, OAuthType type) {
        if (userId == null || userId <= 0) {
            return Flux.empty();
        }
        String typeCode = type == null ? null : type.getCode();
        return authUserClient.authorizationsByUser(userId, typeCode)
                .flatMapMany(Flux::fromIterable)
                .onErrorResume(e -> Flux.empty());
    }

    private List<String> extractTokens(ServerWebExchange exchange) {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        addToken(tokens, bearerToken(exchange));
        var cookie = exchange.getRequest().getCookies().getFirst(AuthTokenNames.AUTHORIZATION_COOKIE);
        if (cookie != null) {
            addToken(tokens, cookie.getValue());
        }
        addToken(tokens, exchange.getRequest().getQueryParams().getFirst("access_token"));
        addToken(tokens, exchange.getRequest().getQueryParams().getFirst("token"));
        return new ArrayList<>(tokens);
    }

    private String bearerToken(ServerWebExchange exchange) {
        String authorization = exchange.getRequest().getHeaders().getFirst(AuthTokenNames.AUTHORIZATION_HEADER);
        if (!StringUtils.startsWith(authorization, "Bearer ")) {
            return null;
        }
        return authorization.substring(7);
    }

    private void addToken(Collection<String> tokens, String token) {
        String value = StringUtils.trimToNull(token);
        if (value != null) {
            tokens.add(value);
        }
    }

    private String asBearer(String token) {
        return "Bearer " + token;
    }

    private String joinIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        return ids.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .map(String::valueOf)
                .reduce((left, right) -> left + "," + right)
                .orElse(null);
    }
}

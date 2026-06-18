package group.flyfish.dev.auth.client;

import group.flyfish.dev.auth.api.client.AuthUserGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 业务服务用户上下文过滤器。
 *
 * <p>该过滤器只在当前请求确实能从 auth 服务解析出用户时写入审计上下文，
 * 鉴权仍由业务方法根据自身场景调用权限工具完成，避免把认证服务的路由规则耦合进业务服务。</p>
 */
@Order(0)
@RequiredArgsConstructor
public class RemoteReactiveUserFilter implements WebFilter {

    private final AuthUserGateway authUserGateway;

    @Override
    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        Mono<Void> continueWithoutUser = Mono.defer(() -> chain.filter(exchange));
        if (isWebSocketUpgrade(exchange.getRequest())) {
            return continueWithoutUser;
        }
        return authUserGateway.current(exchange)
                .map(user -> {
                    Long userId = user.getId();
                    return userId == null ? 0L : userId;
                })
                .defaultIfEmpty(0L)
                .flatMap(userId -> {
                    if (userId <= 0) {
                        return continueWithoutUser;
                    }
                    return chain.filter(exchange)
                            .contextWrite(ctx -> ctx.put(ReactiveUserContextHolder.CONTEXT_KEY, userId));
                });
    }

    private boolean isWebSocketUpgrade(ServerHttpRequest request) {
        HttpHeaders headers = request.getHeaders();
        if ("websocket".equalsIgnoreCase(headers.getUpgrade())) {
            return true;
        }
        return headers.getConnection().stream()
                .anyMatch(value -> "upgrade".equalsIgnoreCase(value));
    }
}

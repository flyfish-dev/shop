package group.flyfish.dev.user.config.filter;

import group.flyfish.dev.user.config.JwtProperties;
import group.flyfish.dev.user.config.context.ReactiveUserContextHolder;
import group.flyfish.dev.user.service.PrincipalExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Optional;


/**
 * 异步的用户过滤器
 *
 * @author wangyu
 */
@RequiredArgsConstructor
@Order(0)
public class ReactiveUserFilter implements WebFilter {

    private final PrincipalExtractor principalExtractor;
    private final JwtProperties properties;

    /**
     * 从请求上下文中获取用户，并缓存
     *
     * @param exchange 当前交换保温
     * @param chain    过滤器链
     * @return 结果
     */
    @Override
    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        return principalExtractor.extractUserId(exchange)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(userId -> userId
                        .map(id -> filterUser(exchange, chain, id))
                        .orElseGet(() -> filterGuest(exchange, chain)));
    }

    private Mono<Void> filterUser(ServerWebExchange exchange, WebFilterChain chain, Long userId) {
        return chain.filter(exchange)
                .contextWrite(ctx -> ctx.put(ReactiveUserContextHolder.CONTEXT_KEY, userId));
    }

    private Mono<Void> filterGuest(ServerWebExchange exchange, WebFilterChain chain) {
        if (properties.permit(exchange.getRequest().getURI().getPath())) {
            return chain.filter(exchange);
        }
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}

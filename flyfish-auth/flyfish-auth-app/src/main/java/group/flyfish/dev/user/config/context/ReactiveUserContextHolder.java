package group.flyfish.dev.user.config.context;

import reactor.core.publisher.Mono;

/**
 * 缓存于上下文中的用户信息
 *
 * @author wangyu
 */
public final class ReactiveUserContextHolder {

    public static final String CONTEXT_KEY = "portal-user";

    public static Mono<Long> getCurrentUser() {
        return Mono.deferContextual(ctx -> Mono.justOrEmpty(ctx.<Long>getOrEmpty(CONTEXT_KEY)));
    }
}

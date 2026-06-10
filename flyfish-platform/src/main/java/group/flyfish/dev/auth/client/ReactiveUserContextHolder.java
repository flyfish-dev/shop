package group.flyfish.dev.auth.client;

import reactor.core.publisher.Mono;

/**
 * WebFlux 当前用户上下文。
 */
public final class ReactiveUserContextHolder {

    public static final String CONTEXT_KEY = "flyfish.currentUser";

    private ReactiveUserContextHolder() {
    }

    public static Mono<Long> getCurrentUser() {
        return Mono.deferContextual(ctx -> ctx.hasKey(CONTEXT_KEY)
                ? Mono.just(ctx.get(CONTEXT_KEY))
                : Mono.empty());
    }
}

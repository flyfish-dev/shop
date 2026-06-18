package group.flyfish.dev.auth.client;

import group.flyfish.dev.annotations.user.CurrentUser;
import group.flyfish.dev.auth.api.client.AuthUserGateway;
import group.flyfish.dev.auth.api.user.GuestUser;
import group.flyfish.dev.auth.api.user.PortalUserVo;
import org.springframework.core.MethodParameter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.lang.NonNull;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolverSupport;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 业务服务通过认证服务解析 {@link CurrentUser} 参数。
 */
public class RemoteUserArgumentResolver extends HandlerMethodArgumentResolverSupport {

    private final AuthUserGateway authUserGateway;

    public RemoteUserArgumentResolver(ReactiveAdapterRegistry adapterRegistry, AuthUserGateway authUserGateway) {
        super(adapterRegistry);
        this.authUserGateway = authUserGateway;
    }

    @Override
    public boolean supportsParameter(MethodParameter methodParameter) {
        return PortalUserVo.class.isAssignableFrom(methodParameter.getParameterType())
                && methodParameter.hasParameterAnnotation(CurrentUser.class);
    }

    @Override
    @NonNull
    public Mono<Object> resolveArgument(@NonNull MethodParameter methodParameter,
                                        @NonNull BindingContext bindingContext,
                                        @NonNull ServerWebExchange exchange) {
        return authUserGateway.current(exchange)
                .defaultIfEmpty(GuestUser.instance())
                .map(Object.class::cast);
    }
}

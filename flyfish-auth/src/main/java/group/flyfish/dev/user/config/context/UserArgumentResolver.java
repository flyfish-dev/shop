package group.flyfish.dev.user.config.context;

import group.flyfish.dev.annotations.user.CurrentUser;
import group.flyfish.dev.user.domain.bo.GuestUser;
import group.flyfish.dev.user.domain.vo.PortalUserVo;
import group.flyfish.dev.user.service.PrincipalExtractor;
import org.springframework.core.MethodParameter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.lang.NonNull;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolverSupport;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 用户数据解析器
 *
 * @author wybab
 */
public class UserArgumentResolver extends HandlerMethodArgumentResolverSupport {

    private final PrincipalExtractor principalExtractor;

    public UserArgumentResolver(ReactiveAdapterRegistry adapterRegistry, PrincipalExtractor principalExtractor) {
        super(adapterRegistry);
        this.principalExtractor = principalExtractor;
    }

    @Override
    public boolean supportsParameter(MethodParameter methodParameter) {
        return PortalUserVo.class.isAssignableFrom(methodParameter.getParameterType()) &&
                methodParameter.hasParameterAnnotation(CurrentUser.class);
    }

    @Override
    @NonNull
    public Mono<Object> resolveArgument(@NonNull MethodParameter methodParameter,
                                        @NonNull BindingContext bindingContext,
                                        @NonNull ServerWebExchange serverWebExchange) {
        return principalExtractor.extractUser(serverWebExchange)
                .defaultIfEmpty(GuestUser.instance())
                .map(Object.class::cast);
    }
}

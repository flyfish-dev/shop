package group.flyfish.dev.user.config.context;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.ReactiveAuditorAware;
import org.springframework.lang.NonNull;
import reactor.core.publisher.Mono;

/**
 * 注册审计支持
 *
 * @author wangyu
 */
@RequiredArgsConstructor
public class UserAuditAware implements ReactiveAuditorAware<String> {

    /**
     * Returns a {@link Mono} publishing the current auditor of the application.
     *
     * @return the {@link Mono} emitting the current auditor, or an empty one, if the auditor is considered to be unknown.
     */
    @Override
    @NonNull
    public Mono<String> getCurrentAuditor() {
        return ReactiveUserContextHolder.getCurrentUser().map(String::valueOf);
    }
}

package group.flyfish.dev.auth.client;

import org.springframework.data.domain.ReactiveAuditorAware;
import reactor.core.publisher.Mono;

/**
 * 业务服务审计用户提供器。
 */
public class RemoteUserAuditAware implements ReactiveAuditorAware<String> {

    @Override
    public Mono<String> getCurrentAuditor() {
        return ReactiveUserContextHolder.getCurrentUser().map(String::valueOf);
    }
}

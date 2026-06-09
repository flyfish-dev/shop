package group.flyfish.dev.git.repository;

import group.flyfish.dev.common.repository.DefaultReactiveRepository;
import group.flyfish.dev.git.domain.po.GitAccessToken;
import org.springframework.data.domain.Sort;
import org.springframework.data.relational.core.query.Criteria;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface GitAccessTokenRepository extends DefaultReactiveRepository<GitAccessToken> {

    default Flux<GitAccessToken> findAllByProvider(String provider) {
        return findAllBy(Criteria.where("provider").is(provider), defaultSort());
    }

    default Mono<GitAccessToken> findFirstEnabledByProvider(String provider) {
        return findAllBy(Criteria.where("provider").is(provider).and("enabled").is(true), defaultSort()).next();
    }

    private Sort defaultSort() {
        return Sort.by(Sort.Order.asc("sort"), Sort.Order.desc("id"));
    }
}

package group.flyfish.dev.git.repository;

import group.flyfish.dev.common.repository.DefaultReactiveRepository;
import group.flyfish.dev.git.domain.po.GitManagedRepository;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.relational.core.query.Criteria;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface GitManagedRepositoryRepository extends DefaultReactiveRepository<GitManagedRepository> {

    default Flux<GitManagedRepository> findList(String provider, String keyword, boolean includeDisabled) {
        Criteria criteria = Criteria.empty();
        if (StringUtils.isNotBlank(provider)) {
            criteria = criteria.and("provider").is(provider);
        }
        if (!includeDisabled) {
            criteria = criteria.and("enabled").is(true);
        }
        return findAllBy(criteria, defaultSort())
                .filter(repository -> matchesKeyword(repository, keyword));
    }

    default Flux<GitManagedRepository> findAllByIds(List<Long> ids) {
        return findAllByValues("id", ids);
    }

    default Mono<GitManagedRepository> findByProviderAndFullName(String provider, String fullName) {
        return findAllBy(Criteria.where("provider").is(provider).and("full_name").is(fullName), defaultSort()).next();
    }

    private Sort defaultSort() {
        return Sort.by(Sort.Order.asc("sort"), Sort.Order.desc("id"));
    }

    private boolean matchesKeyword(GitManagedRepository repository, String keyword) {
        String value = StringUtils.trimToNull(keyword);
        if (value == null) {
            return true;
        }
        return StringUtils.containsIgnoreCase(repository.getName(), value)
                || StringUtils.containsIgnoreCase(repository.getDescription(), value)
                || StringUtils.containsIgnoreCase(repository.getFullName(), value);
    }
}

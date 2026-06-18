package group.flyfish.dev.common.repository;

import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;


/**
 * 默认的持久层dao
 *
 * @param <T> 泛型
 */
@NoRepositoryBean
public interface DefaultReactiveRepository<T> extends ReactiveCrudRepository<T, Long>, ReactiveQueryModelExecutor<T> {

}

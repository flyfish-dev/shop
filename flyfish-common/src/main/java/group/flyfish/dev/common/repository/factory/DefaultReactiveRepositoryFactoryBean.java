package group.flyfish.dev.common.repository.factory;

import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactoryBean;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.springframework.lang.NonNull;

public class DefaultReactiveRepositoryFactoryBean<T extends Repository<S, Long>, S>
        extends R2dbcRepositoryFactoryBean<T, S, Long> {

    /**
     * Creates a new {@link R2dbcRepositoryFactoryBean} for the given repository interface.
     *
     * @param repositoryInterface must not be {@literal null}.
     */
    public DefaultReactiveRepositoryFactoryBean(Class<? extends T> repositoryInterface) {
        super(repositoryInterface);
    }

    @Override
    @NonNull
    protected RepositoryFactorySupport getFactoryInstance(@NonNull R2dbcEntityOperations operations) {
        return new DefaultReactiveRepositoryFactory(operations);
    }
}

package group.flyfish.dev.common.repository.factory;

import group.flyfish.dev.common.repository.DefaultReactiveRepository;
import group.flyfish.dev.common.repository.impl.DefaultReactiveRepositoryImpl;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.lang.NonNull;

public class DefaultReactiveRepositoryFactory extends R2dbcRepositoryFactory {

    /**
     * Creates a new {@link R2dbcRepositoryFactory} with the given {@link R2dbcEntityOperations}.
     *
     * @param mongoOperations must not be {@literal null}.
     */
    public DefaultReactiveRepositoryFactory(R2dbcEntityOperations mongoOperations) {
        super(mongoOperations);
    }

    @Override
    @NonNull
    protected Class<?> getRepositoryBaseClass(@NonNull RepositoryMetadata metadata) {
        if (DefaultReactiveRepository.class.isAssignableFrom(metadata.getRepositoryInterface())) {
            return DefaultReactiveRepositoryImpl.class;
        }
        return super.getRepositoryBaseClass(metadata);
    }
}

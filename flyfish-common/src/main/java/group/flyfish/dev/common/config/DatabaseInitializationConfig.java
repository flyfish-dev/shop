package group.flyfish.dev.common.config;

import io.r2dbc.spi.ConnectionFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.r2dbc.connection.init.CompositeDatabasePopulator;
import org.springframework.r2dbc.connection.init.ConnectionFactoryInitializer;
import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

/**
 * 模块化数据库初始化配置。
 *
 * <p>每个功能模块只携带自己的 {@code schema/*.sql} 和 {@code dialect/{database}/*.sql}，
 * 应用实例按实际依赖的模块自动加载，避免低代码最小应用重复执行小铺表结构。</p>
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class DatabaseInitializationConfig {

    private static final String SCHEMA_PATTERN = "classpath*:/schema/*.sql";

    private static final String DIALECT_PATTERN = "classpath*:/dialect/%s/*.sql";

    @Bean
    public ConnectionFactoryInitializer flyfishConnectionFactoryInitializer(
            ConnectionFactory connectionFactory,
            ResourcePatternResolver resourcePatternResolver) throws IOException {
        ConnectionFactoryInitializer initializer = new ConnectionFactoryInitializer();
        initializer.setConnectionFactory(connectionFactory);

        CompositeDatabasePopulator populator = new CompositeDatabasePopulator();
        addScripts(populator, resourcePatternResolver.getResources(SCHEMA_PATTERN));
        addScripts(populator, resourcePatternResolver.getResources(dialectPattern(connectionFactory)));
        initializer.setDatabasePopulator(populator);
        return initializer;
    }

    private String dialectPattern(ConnectionFactory connectionFactory) {
        String name = connectionFactory.getMetadata().getName().toLowerCase();
        return DIALECT_PATTERN.formatted(name);
    }

    private void addScripts(CompositeDatabasePopulator populator, Resource[] resources) {
        Resource[] sortedResources = Arrays.stream(resources == null ? new Resource[0] : resources)
                .filter(Resource::exists)
                .sorted(Comparator.comparing(this::filename).thenComparing(this::resourceName))
                .toArray(Resource[]::new);
        if (sortedResources.length == 0) {
            return;
        }
        ResourceDatabasePopulator resourcePopulator = new ResourceDatabasePopulator();
        for (Resource resource : sortedResources) {
            log.debug("加载数据库初始化脚本：{}", resourceName(resource));
            resourcePopulator.addScript(resource);
        }
        populator.addPopulators(resourcePopulator);
    }

    private String resourceName(Resource resource) {
        try {
            return resource.getURL().toString();
        } catch (IOException e) {
            return resource.getDescription();
        }
    }

    private String filename(Resource resource) {
        String filename = resource.getFilename();
        return filename == null ? resourceName(resource) : filename;
    }
}

package group.flyfish.dev.common.config;

import io.r2dbc.spi.ConnectionFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;
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

    private void addScripts(CompositeDatabasePopulator populator, Resource[] resources) throws IOException {
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
            resourcePopulator.addScript(snapshot(resource));
        }
        populator.addPopulators(resourcePopulator);
    }

    /**
     * 将 classpath SQL 固化成内存资源后再交给 Spring 执行。
     *
     * <p>GraalVM native 下，位于文件系统中的资源可能被 {@code DataBufferUtils}
     * 识别为文件并走 {@code AsynchronousFileChannel}，而该路径在当前 native runtime 中
     * 不可用。这里仍然复用 Spring 官方的 {@link ResourceDatabasePopulator}，
     * 只是提前用同步流读取 SQL，既保持初始化生命周期一致，也避免 native 启动失败。</p>
     */
    private Resource snapshot(Resource resource) throws IOException {
        byte[] content;
        try (var inputStream = resource.getInputStream()) {
            content = inputStream.readAllBytes();
        }
        return new NamedByteArrayResource(content, filename(resource), resourceName(resource));
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

    private static final class NamedByteArrayResource extends ByteArrayResource {

        private final String filename;

        private final String description;

        private NamedByteArrayResource(byte[] byteArray, String filename, String description) {
            super(byteArray, description);
            this.filename = filename;
            this.description = description;
        }

        @Override
        public String getFilename() {
            return filename;
        }

        @Override
        public String getDescription() {
            return description;
        }
    }
}

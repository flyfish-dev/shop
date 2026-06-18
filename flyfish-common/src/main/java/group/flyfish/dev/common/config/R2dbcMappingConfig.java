package group.flyfish.dev.common.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.mapping.R2dbcMappingContext;
import org.springframework.data.relational.RelationalManagedTypes;
import org.springframework.data.relational.core.mapping.DefaultNamingStrategy;
import org.springframework.data.relational.core.mapping.NamingStrategy;

/**
 * R2DBC 映射配置。
 *
 * <p>Spring Data 4 的 {@link org.springframework.data.relational.core.mapping.RelationalMappingContext}
 * 默认启用强制引用标识符。这个默认值更严格，但会让未显式 {@code @Column} 的字段在 H2
 * 方言下被渲染为 {@code "NAME"} 这类大写引用标识符，和项目当前 MySQL 风格 schema 的小写
 * snake_case 命名不一致。这里显式使用 plain identifiers，让表字段按数据库方言自然解析，
 * 保持 MySQL 生产环境和 H2 测试环境的行为一致。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(R2dbcMappingContext.class)
public class R2dbcMappingConfig {

    @Bean
    public R2dbcMappingContext r2dbcMappingContext(ObjectProvider<NamingStrategy> namingStrategy,
                                                   R2dbcCustomConversions r2dbcCustomConversions,
                                                   RelationalManagedTypes r2dbcManagedTypes) {
        R2dbcMappingContext mappingContext = R2dbcMappingContext.forPlainIdentifiers(
                namingStrategy.getIfAvailable(() -> DefaultNamingStrategy.INSTANCE));
        mappingContext.setSimpleTypeHolder(r2dbcCustomConversions.getSimpleTypeHolder());
        mappingContext.setManagedTypes(r2dbcManagedTypes);
        return mappingContext;
    }
}

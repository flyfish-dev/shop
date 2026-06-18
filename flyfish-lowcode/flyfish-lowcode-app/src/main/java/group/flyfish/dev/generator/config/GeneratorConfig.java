package group.flyfish.dev.generator.config;

import group.flyfish.dev.generator.handlers.DefaultMysqlHandler;
import group.flyfish.dev.generator.handlers.GeneratorHandler;
import group.flyfish.dev.generator.post.GeneratorPostBean;
import group.flyfish.dev.generator.properties.DevProperties;
import group.flyfish.dev.generator.properties.GeneratorProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 生成器配置
 */
@Configuration
@EnableConfigurationProperties({GeneratorProperties.class, DevProperties.class})
public class GeneratorConfig {

    /**
     * 实例化处理器bean
     *
     * @param properties 配置
     * @return 结果
     */
    @Bean
    public GeneratorHandler generatorHandler(GeneratorProperties properties) {
        return new DefaultMysqlHandler(properties);
    }

    /**
     * 实例化自动生成逻辑
     *
     * @param generatorHandler 生成处理器
     * @return 实例化的处理bean
     */
    @Bean
    @ConditionalOnProperty("generator.auto")
    public GeneratorPostBean generatorPostBean(GeneratorHandler generatorHandler) {
        return new GeneratorPostBean(generatorHandler);
    }
}

package group.flyfish.dev;

import group.flyfish.dev.common.repository.factory.DefaultReactiveRepositoryFactoryBean;
import group.flyfish.dev.common.reactive.ReactorVirtualThreadSupport;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

/**
 * 代码生成器
 *
 * @author wangyu
 * 基于 R2DBC 与 Freemarker 实现
 */
@SpringBootApplication(
        excludeName = {
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
                "org.springframework.boot.data.redis.autoconfigure.RedisAutoConfiguration"
        }
)
@EnableR2dbcRepositories(
        repositoryFactoryBeanClass = DefaultReactiveRepositoryFactoryBean.class
)
@EnableR2dbcAuditing(auditorAwareRef = "userAuditAware")
@EnableScheduling
public class FlyfishDevApplication {

    public static void main(String[] args) {
        ReactorVirtualThreadSupport.enableByDefault();
        SpringApplication.run(FlyfishDevApplication.class, args);
    }

}

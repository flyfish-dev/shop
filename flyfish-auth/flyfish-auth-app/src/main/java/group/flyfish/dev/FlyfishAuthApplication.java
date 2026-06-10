package group.flyfish.dev;

import group.flyfish.dev.common.repository.factory.DefaultReactiveRepositoryFactoryBean;
import group.flyfish.dev.common.reactive.ReactorVirtualThreadSupport;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 飞鱼认证服务启动实例。
 *
 * @author wangyu
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
public class FlyfishAuthApplication {

    public static void main(String[] args) {
        ReactorVirtualThreadSupport.enableByDefault();
        SpringApplication.run(FlyfishAuthApplication.class, args);
    }
}

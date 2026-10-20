package group.flyfish.dev.xianyu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"group.flyfish.dev.xianyu", "group.flyfish.dev.common.config",
        "group.flyfish.dev.common.exception", "group.flyfish.dev.common.bridge"})
@EnableConfigurationProperties(XianyuProperties.class)
@EnableScheduling
public class XianyuApplication {
    public static void main(String[] args) {
        SpringApplication.run(XianyuApplication.class, args);
    }
}

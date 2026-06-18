package group.flyfish.dev.support.notification;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SupportNotificationProperties.class)
public class SupportNotificationConfiguration {
}

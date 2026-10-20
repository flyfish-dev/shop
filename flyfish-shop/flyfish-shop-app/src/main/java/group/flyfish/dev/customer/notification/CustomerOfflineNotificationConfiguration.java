package group.flyfish.dev.customer.notification;

import group.flyfish.dev.common.http.HttpInterfaceClients;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 客服离线提醒基础配置。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CustomerOfflineNotificationProperties.class)
public class CustomerOfflineNotificationConfiguration {

    @Bean
    public CustomerOfflineWebhookClient customerOfflineWebhookClient() {
        WebClient webClient = WebClient.builder()
                .defaultHeader(HttpHeaders.USER_AGENT, "Flyfish-Customer-Service/1.0")
                .build();
        return HttpInterfaceClients.create(webClient, CustomerOfflineWebhookClient.class);
    }
}

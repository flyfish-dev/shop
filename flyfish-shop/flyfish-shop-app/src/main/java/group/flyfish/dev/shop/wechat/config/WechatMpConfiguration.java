package group.flyfish.dev.shop.wechat.config;

import group.flyfish.dev.common.http.HttpInterfaceClients;
import group.flyfish.dev.shop.wechat.client.WechatMpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 微信公众号轻量客户端配置。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WechatMpProperties.class)
public class WechatMpConfiguration {

    @Bean
    public WebClient wechatMpWebClient(WechatMpProperties properties) {
        return WebClient.builder()
                .baseUrl(properties.getApiBaseUrl())
                .defaultHeader(HttpHeaders.USER_AGENT, "Flyfish-Dev")
                .build();
    }

    @Bean
    public WechatMpClient wechatMpClient(@Qualifier("wechatMpWebClient") WebClient wechatMpWebClient) {
        return HttpInterfaceClients.create(wechatMpWebClient, WechatMpClient.class);
    }
}

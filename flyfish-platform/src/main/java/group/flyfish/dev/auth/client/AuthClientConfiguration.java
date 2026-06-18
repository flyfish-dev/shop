package group.flyfish.dev.auth.client;

import group.flyfish.dev.auth.api.client.AuthUserClient;
import group.flyfish.dev.auth.api.client.AuthUserGateway;
import group.flyfish.dev.auth.api.client.WechatLoginClient;
import group.flyfish.dev.common.http.HttpInterfaceClients;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.data.domain.ReactiveAuditorAware;
import org.springframework.http.codec.json.JacksonJsonDecoder;
import org.springframework.http.codec.json.JacksonJsonEncoder;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.result.method.annotation.ArgumentResolverConfigurer;
import tools.jackson.databind.json.JsonMapper;

/**
 * 业务服务访问认证服务的客户端配置。
 */
@Configuration
public class AuthClientConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "flyfish.auth.client")
    public AuthClientProperties authClientProperties() {
        return new AuthClientProperties();
    }

    @Bean
    @ConditionalOnMissingBean(WebClient.Builder.class)
    public WebClient.Builder webClientBuilder(ObjectProvider<JsonMapperBuilderCustomizer> customizers) {
        JsonMapper.Builder jsonBuilder = JsonMapper.builder();
        customizers.orderedStream().forEach(customizer -> customizer.customize(jsonBuilder));
        JsonMapper jsonMapper = jsonBuilder.build();
        return WebClient.builder()
                .codecs(configurer -> {
                    configurer.defaultCodecs().jacksonJsonDecoder(new JacksonJsonDecoder(jsonMapper));
                    configurer.defaultCodecs().jacksonJsonEncoder(new JacksonJsonEncoder(jsonMapper));
                });
    }

    @Bean
    public AuthUserClient authUserClient(AuthClientProperties properties, WebClient.Builder builder) {
        WebClient webClient = builder.baseUrl(properties.getBaseUrl()).build();
        return HttpInterfaceClients.create(webClient, AuthUserClient.class);
    }

    @Bean
    public WechatLoginClient wechatLoginClient(AuthClientProperties properties, WebClient.Builder builder) {
        WebClient webClient = builder.baseUrl(properties.getBaseUrl()).build();
        return HttpInterfaceClients.create(webClient, WechatLoginClient.class);
    }

    @Bean
    public AuthUserGateway authUserGateway(AuthUserClient authUserClient) {
        return new RemoteAuthUserGateway(authUserClient);
    }

    @Bean
    public RemoteUserArgumentResolver remoteUserArgumentResolver(AuthUserGateway authUserGateway) {
        return new RemoteUserArgumentResolver(ReactiveAdapterRegistry.getSharedInstance(), authUserGateway);
    }

    @Bean
    public WebFluxConfigurer remoteUserWebFluxConfigurer(RemoteUserArgumentResolver resolver) {
        return new WebFluxConfigurer() {
            @Override
            public void configureArgumentResolvers(ArgumentResolverConfigurer configurer) {
                configurer.addCustomResolver(resolver);
            }
        };
    }

    @Bean("userAuditAware")
    public ReactiveAuditorAware<String> userAuditAware() {
        return new RemoteUserAuditAware();
    }

    @Bean
    public RemoteReactiveUserFilter remoteReactiveUserFilter(AuthUserGateway authUserGateway) {
        return new RemoteReactiveUserFilter(authUserGateway);
    }
}

package group.flyfish.dev.shop.service.support.h5zhifu.config;

import group.flyfish.dev.common.http.HttpInterfaceClients;
import group.flyfish.dev.shop.service.support.h5zhifu.H5ZhiFuPayService;
import group.flyfish.dev.shop.service.support.h5zhifu.H5ZhiFuService;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

public class H5ZhiFuConfig {

    @Bean
    @ConfigurationProperties(prefix = "shop.payment.h5zhifu")
    public H5ZhiFuProperties h5ZhiFuProperties() {
        return new H5ZhiFuProperties();
    }

    @Bean
    public H5ZhiFuPayService h5ZhiFuPayService(H5ZhiFuProperties properties) {
        WebClient webClient = WebClient.builder()
                .baseUrl(properties.getNormalizedApiBaseUrl())
                .defaultHeader(HttpHeaders.USER_AGENT, properties.getUserAgent())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        H5ZhiFuService h5ZhiFuService = HttpInterfaceClients.create(webClient, H5ZhiFuService.class);
        return new H5ZhiFuPayService(h5ZhiFuService, properties);
    }
}

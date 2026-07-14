package group.flyfish.dev.shop.service.support.stripe.config;

import group.flyfish.dev.common.http.HttpInterfaceClients;
import group.flyfish.dev.shop.service.support.stripe.StripeCheckoutClient;
import group.flyfish.dev.shop.service.support.stripe.StripeCheckoutPayService;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

public class StripePaymentConfig {

    @Bean
    @ConfigurationProperties(prefix = "shop.payment.stripe")
    public StripePaymentProperties stripePaymentProperties() {
        return new StripePaymentProperties();
    }

    @Bean
    public StripeCheckoutClient stripeCheckoutClient(StripePaymentProperties properties) {
        WebClient webClient = WebClient.builder()
                .baseUrl(properties.normalizedApiBaseUrl())
                .defaultHeader(HttpHeaders.USER_AGENT, properties.getUserAgent())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        return HttpInterfaceClients.create(webClient, StripeCheckoutClient.class);
    }

    @Bean
    public StripeCheckoutPayService stripeCheckoutPayService(StripeCheckoutClient stripeCheckoutClient,
                                                             StripePaymentProperties properties) {
        return new StripeCheckoutPayService(stripeCheckoutClient, properties);
    }
}

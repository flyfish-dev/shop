package group.flyfish.dev.shop.controller;

import group.flyfish.dev.shop.service.ShopOrderService;
import group.flyfish.dev.shop.service.support.stripe.StripeWebhookService;
import group.flyfish.dev.shop.service.support.stripe.config.StripePaymentProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class StripePaymentControllerTest {

    @Test
    void rejectsUnsignedWebhookWithBadRequest() {
        StripePaymentProperties properties = new StripePaymentProperties();
        properties.setWebhookSecret("whsec_test_secret");
        StripeWebhookService webhookService = new StripeWebhookService(properties, mock(ShopOrderService.class));
        StripePaymentController controller = new StripePaymentController(webhookService);

        StepVerifier.create(controller.webhook("{\"type\":\"test.ping\",\"data\":{\"object\":{}}}", null))
                .assertNext(response -> {
                    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
                    assertEquals("bad signature", response.getBody());
                })
                .verifyComplete();
    }
}

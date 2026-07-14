package group.flyfish.dev.shop.service.support.stripe;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

/**
 * Stripe Checkout API client.
 */
@HttpExchange(contentType = MediaType.APPLICATION_FORM_URLENCODED_VALUE, accept = MediaType.APPLICATION_JSON_VALUE)
public interface StripeCheckoutClient {

    @PostExchange("/v1/checkout/sessions")
    Mono<JsonNode> createSession(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                 @RequestHeader("Idempotency-Key") String idempotencyKey,
                                 @RequestBody MultiValueMap<String, String> form);
}

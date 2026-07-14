package group.flyfish.dev.shop.service.support.stripe;

import group.flyfish.dev.common.json.JacksonUtils;
import group.flyfish.dev.shop.service.ShopOrderService;
import group.flyfish.dev.shop.service.support.stripe.bean.StripeCheckoutSessionDto;
import group.flyfish.dev.shop.service.support.stripe.config.StripePaymentProperties;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

@Service
@RequiredArgsConstructor
public class StripeWebhookService {

    private static final String CHECKOUT_SESSION_COMPLETED = "checkout.session.completed";
    private static final String CHECKOUT_SESSION_ASYNC_PAYMENT_SUCCEEDED = "checkout.session.async_payment_succeeded";
    private static final String CHECKOUT_SESSION_ASYNC_PAYMENT_FAILED = "checkout.session.async_payment_failed";
    private static final String CHECKOUT_SESSION_EXPIRED = "checkout.session.expired";

    private final StripePaymentProperties properties;
    private final ShopOrderService shopOrderService;
    private final StripeWebhookVerifier verifier = new StripeWebhookVerifier();

    public Mono<Void> handle(String rawPayload, String signature) {
        return Mono.defer(() -> {
            verifier.verify(rawPayload, signature, properties.getWebhookSecret());
            JsonNode root = JacksonUtils.readTree(rawPayload);
            String eventType = root.path("type").asText();
            if (!isCheckoutSessionEvent(eventType)) {
                return Mono.empty();
            }
            JsonNode object = root.path("data").path("object");
            StripeCheckoutSessionDto session = StripeCheckoutSessionDto.from(object);
            return shopOrderService.handleStripeCheckoutSession(session, eventType);
        });
    }

    private boolean isCheckoutSessionEvent(String eventType) {
        return StringUtils.equalsAny(eventType,
                CHECKOUT_SESSION_COMPLETED,
                CHECKOUT_SESSION_ASYNC_PAYMENT_SUCCEEDED,
                CHECKOUT_SESSION_ASYNC_PAYMENT_FAILED,
                CHECKOUT_SESSION_EXPIRED);
    }
}

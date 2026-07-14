package group.flyfish.dev.shop.controller;

import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.shop.service.support.stripe.StripeWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("shops/payments/stripe")
@RequiredArgsConstructor
public class StripePaymentController {

    private final StripeWebhookService stripeWebhookService;

    @PostMapping(value = "webhook", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE)
    public Mono<ResponseEntity<String>> webhook(@RequestBody String rawPayload,
                                                @RequestHeader(value = "Stripe-Signature", required = false)
                                                String signature) {
        return stripeWebhookService.handle(rawPayload, signature)
                .thenReturn(ResponseEntity.ok("success"))
                .onErrorResume(BusinessException.class, this::signatureError);
    }

    private Mono<ResponseEntity<String>> signatureError(BusinessException error) {
        if ((error.getCode() != null && error.getCode().startsWith("STRIPE_WEBHOOK_SIGNATURE"))
                || "STRIPE_WEBHOOK_SECRET_MISSING".equals(error.getCode())) {
            return Mono.just(ResponseEntity.badRequest().body("bad signature"));
        }
        return Mono.error(error);
    }
}

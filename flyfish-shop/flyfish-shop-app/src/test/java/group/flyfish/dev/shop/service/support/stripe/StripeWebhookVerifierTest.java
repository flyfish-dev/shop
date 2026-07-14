package group.flyfish.dev.shop.service.support.stripe;

import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.common.json.JacksonUtils;
import group.flyfish.dev.shop.service.support.stripe.bean.StripeCheckoutSessionDto;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StripeWebhookVerifierTest {

    @Test
    void acceptsValidStripeSignature() {
        StripeWebhookVerifier verifier = new StripeWebhookVerifier();
        String payload = "{\"id\":\"evt_test\",\"type\":\"checkout.session.completed\"}";
        String secret = "webhook-test-secret";
        long timestamp = Instant.now().getEpochSecond();

        verifier.verify(payload, signatureHeader(secret, timestamp, payload), secret);
    }

    @Test
    void rejectsTamperedPayload() {
        StripeWebhookVerifier verifier = new StripeWebhookVerifier();
        String payload = "{\"id\":\"evt_test\",\"type\":\"checkout.session.completed\"}";
        String secret = "webhook-test-secret";
        long timestamp = Instant.now().getEpochSecond();
        String signature = signatureHeader(secret, timestamp, payload);

        assertThrows(BusinessException.class, () -> verifier.verify(payload + " ", signature, secret));
    }

    @Test
    void mapsStripeCheckoutSessionSnakeCaseFieldsWithJackson3() {
        StripeCheckoutSessionDto session = StripeCheckoutSessionDto.from(JacksonUtils.readTree("""
                {
                  "id": "cs_test_123",
                  "client_reference_id": "FF100",
                  "payment_intent": "pi_123",
                  "payment_status": "paid",
                  "amount_total": 1888,
                  "currency": "cny",
                  "customer_email": "buyer@example.com",
                  "expires_at": 1780000000
                }
                """));

        assertEquals("FF100", session.getClientReferenceId());
        assertEquals("pi_123", session.getPaymentIntent());
        assertEquals("paid", session.getPaymentStatus());
        assertEquals(1888L, session.getAmountTotal());
        assertEquals("buyer@example.com", session.getCustomerEmail());
    }

    private String signatureHeader(String secret, long timestamp, String payload) {
        return "t=" + timestamp + ",v1=" + hmacHex(secret, timestamp + "." + payload);
    }

    private String hmacHex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

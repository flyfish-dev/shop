package group.flyfish.dev.shop.service.support.stripe;

import group.flyfish.dev.shop.domain.dto.ShopOrderDto;
import group.flyfish.dev.shop.domain.po.ShopItem;
import group.flyfish.dev.shop.domain.po.ShopOrder;
import group.flyfish.dev.common.json.JacksonUtils;
import group.flyfish.dev.shop.service.support.stripe.config.StripePaymentProperties;
import org.junit.jupiter.api.Test;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StripeCheckoutPayServiceTest {

    @Test
    void createsCheckoutSessionWithServerSideAmountAndReturnUrls() {
        StubStripeCheckoutClient client = new StubStripeCheckoutClient();
        StripeCheckoutPayService service = new StripeCheckoutPayService(client, properties());
        ShopOrderDto request = new ShopOrderDto();
        request.setPaymentLocale("en-US");
        request.setPaymentReturnPath("/shop/sponsor?source=github&payment=stripe&payment_status=success"
                + "&order_no=FF-OLD&session_id=cs_test_old");

        StepVerifier.create(service.pay(order(), item(), request))
                .assertNext(payment -> {
                    assertEquals("stripe", payment.getProvider());
                    assertEquals("checkout", payment.getTradeType());
                    assertEquals("stripe", payment.getPayType());
                    assertEquals("cs_test_123", payment.getTradeNo());
                    assertEquals("https://checkout.stripe.test/pay/cs_test_123", payment.getJumpUrl());
                })
                .verifyComplete();

        MultiValueMap<String, String> form = client.formRef.get();
        assertEquals("payment", form.getFirst("mode"));
        assertEquals("FF-STRIPE-1", form.getFirst("client_reference_id"));
        assertEquals("usd", form.getFirst("line_items[0][price_data][currency]"));
        assertEquals("1888", form.getFirst("line_items[0][price_data][unit_amount]"));
        assertEquals("file-viewer 支持", form.getFirst("line_items[0][price_data][product_data][name]"));
        assertEquals("FF-STRIPE-1", form.getFirst("metadata[flyfish_order_no]"));
        assertEquals("usd", form.getFirst("metadata[flyfish_currency]"));
        assertEquals("en", form.getFirst("locale"));
        String successUrl = form.getFirst("success_url");
        String cancelUrl = form.getFirst("cancel_url");
        assertTrue(successUrl.contains("/shop/sponsor?source=github&payment=stripe"));
        assertTrue(successUrl.contains("session_id={CHECKOUT_SESSION_ID}"));
        assertTrue(cancelUrl.contains("payment_status=cancel"));
        assertFalse(successUrl.contains("FF-OLD"));
        assertFalse(successUrl.contains("cs_test_old"));
        assertFalse(cancelUrl.contains("FF-OLD"));
        assertFalse(cancelUrl.contains("cs_test_old"));
        assertEquals("flyfish:stripe:checkout:FF-STRIPE-1", client.idempotencyKeyRef.get());
    }

    private StripePaymentProperties properties() {
        StripePaymentProperties properties = new StripePaymentProperties();
        properties.setEnabled(true);
        properties.setSecretKey("stripe-test-placeholder");
        properties.setPortalBaseUrl("https://dev.flyfish.group/");
        properties.setDefaultCurrency("cny");
        properties.setCheckoutExpiresMinutes(30);
        return properties;
    }

    private ShopOrder order() {
        ShopOrder order = new ShopOrder();
        order.setId(88L);
        order.setOrderNo("FF-STRIPE-1");
        order.setItemId(1009L);
        order.setItemName("file-viewer 支持");
        order.setAmount(new BigDecimal("18.88"));
        order.setCurrency("USD");
        return order;
    }

    private ShopItem item() {
        ShopItem item = new ShopItem();
        item.setName("file-viewer 支持");
        return item;
    }

    private static class StubStripeCheckoutClient implements StripeCheckoutClient {

        private final AtomicReference<MultiValueMap<String, String>> formRef = new AtomicReference<>();
        private final AtomicReference<String> idempotencyKeyRef = new AtomicReference<>();

        @Override
        public Mono<JsonNode> createSession(String authorization, String idempotencyKey,
                                            MultiValueMap<String, String> form) {
            assertEquals("Bearer stripe-test-placeholder", authorization);
            idempotencyKeyRef.set(idempotencyKey);
            formRef.set(form);
            return Mono.just(JacksonUtils.readTree("""
                    {
                      "id": "cs_test_123",
                      "url": "https://checkout.stripe.test/pay/cs_test_123",
                      "expires_at": %d
                    }
                    """.formatted(Instant.now().plusSeconds(1800).getEpochSecond())));
        }
    }
}

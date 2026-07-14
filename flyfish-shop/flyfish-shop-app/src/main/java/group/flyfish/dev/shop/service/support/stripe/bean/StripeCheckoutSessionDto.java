package group.flyfish.dev.shop.service.support.stripe.bean;

import lombok.Data;
import tools.jackson.databind.JsonNode;

@Data
public class StripeCheckoutSessionDto {

    private String id;

    private String url;

    private String clientReferenceId;

    private String paymentIntent;

    private String paymentStatus;

    private Long amountTotal;

    private String currency;

    private String customerEmail;

    private Long expiresAt;

    public static StripeCheckoutSessionDto from(JsonNode node) {
        StripeCheckoutSessionDto session = new StripeCheckoutSessionDto();
        session.setId(text(node, "id"));
        session.setUrl(text(node, "url"));
        session.setClientReferenceId(text(node, "client_reference_id"));
        session.setPaymentIntent(text(node, "payment_intent"));
        session.setPaymentStatus(text(node, "payment_status"));
        session.setAmountTotal(number(node, "amount_total"));
        session.setCurrency(text(node, "currency"));
        session.setCustomerEmail(text(node, "customer_email"));
        session.setExpiresAt(number(node, "expires_at"));
        return session;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private static Long number(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asLong();
    }
}

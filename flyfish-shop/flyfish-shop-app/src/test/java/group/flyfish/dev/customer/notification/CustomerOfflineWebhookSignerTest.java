package group.flyfish.dev.customer.notification;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CustomerOfflineWebhookSignerTest {

    @Test
    void signsTimestampAndExactBodyBytesWithHmacSha256() {
        CustomerOfflineWebhookSigner signer = new CustomerOfflineWebhookSigner();

        String signature = signer.sign("secret", "1700000000",
                "{\"event\":\"customer.service.offline_message\"}".getBytes(StandardCharsets.UTF_8));

        assertEquals("sha256=3529ff6b11a20ddaae12fd9a64de4cdd76ebc31d0a00b67281e3f4ebfdb5c2c0",
                signature);
    }
}

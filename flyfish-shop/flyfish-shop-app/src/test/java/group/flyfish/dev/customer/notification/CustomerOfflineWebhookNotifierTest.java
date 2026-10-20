package group.flyfish.dev.customer.notification;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerOfflineWebhookNotifierTest {

    @Test
    void postsSignedExactJsonBodyToHttpsEndpoint() {
        CustomerOfflineWebhookClient client = mock(CustomerOfflineWebhookClient.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        CustomerOfflineNotificationProperties properties = properties();
        byte[] body = "{\"event\":\"customer.service.offline_message\"}".getBytes(StandardCharsets.UTF_8);
        when(objectMapper.writeValueAsBytes(any())).thenReturn(body);
        when(client.send(any(), any(), any())).thenReturn(Mono.empty());
        CustomerOfflineWebhookNotifier notifier = new CustomerOfflineWebhookNotifier(client, properties, objectMapper);

        StepVerifier.create(notifier.send(notification())).verifyComplete();

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<byte[]> bodyCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(client).send(uriCaptor.capture(), headersCaptor.capture(), bodyCaptor.capture());
        assertEquals(URI.create("https://hooks.example.com/flyfish"), uriCaptor.getValue());
        assertEquals(new String(body, StandardCharsets.UTF_8),
                new String(bodyCaptor.getValue(), StandardCharsets.UTF_8));
        Map<String, String> headers = headersCaptor.getValue();
        assertEquals(CustomerOfflineNotificationFactory.EVENT_TYPE,
                headers.get(CustomerOfflineWebhookNotifier.HEADER_EVENT));
        assertEquals("event-1", headers.get(CustomerOfflineWebhookNotifier.HEADER_DELIVERY));
        assertTrue(headers.get(CustomerOfflineWebhookNotifier.HEADER_SIGNATURE).startsWith("sha256="));
        String expected = new CustomerOfflineWebhookSigner().sign(
                properties.getWebhook().getSecret(),
                headers.get(CustomerOfflineWebhookNotifier.HEADER_TIMESTAMP),
                body);
        assertEquals(expected, headers.get(CustomerOfflineWebhookNotifier.HEADER_SIGNATURE));
    }

    @Test
    void rejectsInsecureHttpEndpointByDefault() {
        CustomerOfflineWebhookClient client = mock(CustomerOfflineWebhookClient.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        CustomerOfflineNotificationProperties properties = properties();
        properties.getWebhook().setUrls(List.of(URI.create("http://hooks.example.com/flyfish")));
        CustomerOfflineWebhookNotifier notifier = new CustomerOfflineWebhookNotifier(client, properties, objectMapper);

        StepVerifier.create(notifier.send(notification())).verifyComplete();

        verify(client, never()).send(any(), any(), any());
    }

    @Test
    void completesWhenWebhookFails() {
        CustomerOfflineWebhookClient client = mock(CustomerOfflineWebhookClient.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        CustomerOfflineNotificationProperties properties = properties();
        properties.getWebhook().setMaxRetries(0);
        when(objectMapper.writeValueAsBytes(any())).thenReturn("{}".getBytes(StandardCharsets.UTF_8));
        when(client.send(any(), any(), any())).thenReturn(Mono.error(new IllegalStateException("unavailable")));
        CustomerOfflineWebhookNotifier notifier = new CustomerOfflineWebhookNotifier(client, properties, objectMapper);

        StepVerifier.create(notifier.send(notification())).verifyComplete();
    }

    @Test
    void omitsSignatureWhenSecretIsEmpty() {
        CustomerOfflineWebhookClient client = mock(CustomerOfflineWebhookClient.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        CustomerOfflineNotificationProperties properties = properties();
        properties.getWebhook().setSecret(null);
        when(objectMapper.writeValueAsBytes(any())).thenReturn("{}".getBytes(StandardCharsets.UTF_8));
        when(client.send(any(), any(), any())).thenReturn(Mono.empty());
        CustomerOfflineWebhookNotifier notifier = new CustomerOfflineWebhookNotifier(client, properties, objectMapper);

        StepVerifier.create(notifier.send(notification())).verifyComplete();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
        verify(client).send(any(), headersCaptor.capture(), any());
        assertFalse(headersCaptor.getValue().containsKey(CustomerOfflineWebhookNotifier.HEADER_SIGNATURE));
    }

    private CustomerOfflineNotificationProperties properties() {
        CustomerOfflineNotificationProperties properties = new CustomerOfflineNotificationProperties();
        properties.getWebhook().setEnabled(true);
        properties.getWebhook().setUrls(List.of(URI.create("https://hooks.example.com/flyfish")));
        properties.getWebhook().setSecret("webhook-secret");
        properties.getWebhook().setMaxRetries(0);
        return properties;
    }

    private CustomerOfflineNotification notification() {
        return new CustomerOfflineNotification("subject", "body", new CustomerOfflineWebhookPayload(
                CustomerOfflineNotificationFactory.EVENT_TYPE, "event-1", null,
                1L, 2L, "客户", "WEB", "text", "你好", "https://dev.flyfish.group"));
    }
}

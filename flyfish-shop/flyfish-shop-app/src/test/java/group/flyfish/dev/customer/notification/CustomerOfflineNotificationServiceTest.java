package group.flyfish.dev.customer.notification;

import group.flyfish.dev.customer.domain.po.CustomerConversation;
import group.flyfish.dev.customer.domain.po.CustomerMessage;
import group.flyfish.dev.customer.service.CustomerPresenceRegistry;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerOfflineNotificationServiceTest {

    @Test
    void sendsMailAndWebhookWhenManagerIsOffline() {
        Fixture fixture = new Fixture();

        StepVerifier.create(fixture.service.notifyIfOffline(fixture.conversation, fixture.message))
                .verifyComplete();

        verify(fixture.mailNotifier).send(fixture.notification);
        verify(fixture.webhookNotifier).send(fixture.notification);
    }

    @Test
    void skipsAllNotificationsWhenManagerIsOnline() {
        Fixture fixture = new Fixture();
        Runnable release = fixture.presenceRegistry.registerManager();

        StepVerifier.create(fixture.service.notifyIfOffline(fixture.conversation, fixture.message))
                .verifyComplete();

        verify(fixture.factory, never()).create(any(), any());
        verify(fixture.mailNotifier, never()).send(any());
        verify(fixture.webhookNotifier, never()).send(any());
        release.run();
    }

    @Test
    void isolatesNotifierFailuresFromCustomerMessageFlow() {
        Fixture fixture = new Fixture();
        when(fixture.mailNotifier.send(any())).thenReturn(Mono.error(new IllegalStateException("smtp unavailable")));
        when(fixture.webhookNotifier.send(any())).thenReturn(Mono.error(new IllegalStateException("webhook unavailable")));

        StepVerifier.create(fixture.service.notifyIfOffline(fixture.conversation, fixture.message))
                .verifyComplete();
    }

    private static class Fixture {

        private final CustomerPresenceRegistry presenceRegistry = new CustomerPresenceRegistry();
        private final CustomerOfflineNotificationProperties properties = new CustomerOfflineNotificationProperties();
        private final CustomerOfflineNotificationFactory factory = mock(CustomerOfflineNotificationFactory.class);
        private final CustomerOfflineMailNotifier mailNotifier = mock(CustomerOfflineMailNotifier.class);
        private final CustomerOfflineWebhookNotifier webhookNotifier = mock(CustomerOfflineWebhookNotifier.class);
        private final CustomerOfflineNotificationService service = new CustomerOfflineNotificationService(
                presenceRegistry, properties, factory, mailNotifier, webhookNotifier);
        private final CustomerConversation conversation = new CustomerConversation();
        private final CustomerMessage message = new CustomerMessage();
        private final CustomerOfflineNotification notification = new CustomerOfflineNotification(
                "subject", "body", new CustomerOfflineWebhookPayload(
                CustomerOfflineNotificationFactory.EVENT_TYPE, "event-1", null,
                1L, 2L, "客户", "WEB", "text", "你好", "https://dev.flyfish.group"));

        private Fixture() {
            conversation.setId(1L);
            message.setId(2L);
            when(factory.create(conversation, message)).thenReturn(notification);
            when(mailNotifier.send(notification)).thenReturn(Mono.empty());
            when(webhookNotifier.send(notification)).thenReturn(Mono.empty());
        }
    }
}

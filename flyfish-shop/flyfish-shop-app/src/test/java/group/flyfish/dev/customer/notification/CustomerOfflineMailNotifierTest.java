package group.flyfish.dev.customer.notification;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerOfflineMailNotifierTest {

    @Test
    void filtersRecipientsAndSendsProfessionalMessage() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mailSender);
        CustomerOfflineNotificationProperties properties = new CustomerOfflineNotificationProperties();
        properties.getMail().setFrom("service@flyfish.group");
        properties.getMail().setRecipients(List.of(
                " admin@flyfish.group ", "invalid", "admin@flyfish.group", "owner@flyfish.group"));
        CustomerOfflineMailNotifier notifier = new CustomerOfflineMailNotifier(provider, properties);
        CustomerOfflineNotification notification = notification();

        StepVerifier.create(notifier.send(notification)).verifyComplete();

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage mail = captor.getValue();
        assertEquals("service@flyfish.group", mail.getFrom());
        assertArrayEquals(new String[]{"admin@flyfish.group", "owner@flyfish.group"}, mail.getTo());
        assertEquals("subject", mail.getSubject());
        assertEquals("body", mail.getText());
    }

    private CustomerOfflineNotification notification() {
        return new CustomerOfflineNotification("subject", "body", new CustomerOfflineWebhookPayload(
                CustomerOfflineNotificationFactory.EVENT_TYPE, "event-1", null,
                1L, 2L, "客户", "WEB", "text", "你好", "https://dev.flyfish.group"));
    }
}

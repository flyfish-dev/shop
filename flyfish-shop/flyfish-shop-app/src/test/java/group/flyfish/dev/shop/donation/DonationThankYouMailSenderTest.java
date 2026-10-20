package group.flyfish.dev.shop.donation;

import group.flyfish.dev.support.notification.SupportNotificationProperties;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import reactor.test.StepVerifier;

import java.util.Properties;

import static group.flyfish.dev.shop.donation.DonationThankYouTemplateTest.donor;
import static group.flyfish.dev.shop.donation.DonationThankYouTemplateTest.paidOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DonationThankYouMailSenderTest {

    @Test
    @SuppressWarnings("unchecked")
    void sendsUtf8AlternativeContentOnlyToTheProfileEmail() throws Exception {
        JavaMailSender smtp = mock(JavaMailSender.class);
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(smtp);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(smtp.createMimeMessage()).thenReturn(message);
        SupportNotificationProperties properties = new SupportNotificationProperties();
        properties.getMail().setFrom("thanks@example.test");
        DonationThankYouMailSender sender = new DonationThankYouMailSender(provider, properties);
        var user = donor("  donor@example.test  ");
        StepVerifier.create(sender.send(paidOrder(), user, sender.recipient(user).orElseThrow())).verifyComplete();
        verify(smtp).send(message);
        assertThat(message.getAllRecipients()).hasSize(1);
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("donor@example.test");
        assertThat(message.getSubject()).contains("感谢");
        assertThat(message.getContent()).isInstanceOf(Multipart.class);
        assertThat(sender.recipient(donor(null))).isEmpty();
        assertThat(sender.recipient(donor("  "))).isEmpty();
        assertThat(sender.recipient(donor("invalid"))).isEmpty();
        assertThat(sender.recipient(donor("one@example.test,two@example.test"))).isEmpty();
        assertThat(sender.recipient(donor("x@example.test\r\nBcc: attacker@example.test"))).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void mailFailuresMustPropagateForDurableRetry() {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        var properties = new SupportNotificationProperties();
        properties.getMail().setFrom("thanks@example.test");
        var sender = new DonationThankYouMailSender(provider, properties);
        StepVerifier.create(sender.send(paidOrder(), donor("donor@example.test"), "donor@example.test"))
                .expectError(IllegalStateException.class).verify();
        JavaMailSender smtp = mock(JavaMailSender.class);
        when(provider.getIfAvailable()).thenReturn(smtp);
        when(smtp.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new MailSendException("SMTP unavailable")).when(smtp).send(any(MimeMessage.class));
        StepVerifier.create(sender.send(paidOrder(), donor("donor@example.test"), "donor@example.test"))
                .expectError(MailSendException.class).verify();
    }
}

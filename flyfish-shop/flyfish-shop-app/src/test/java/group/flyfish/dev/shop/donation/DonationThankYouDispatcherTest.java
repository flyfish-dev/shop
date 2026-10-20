package group.flyfish.dev.shop.donation;

import group.flyfish.dev.auth.api.client.AuthUserGateway;
import group.flyfish.dev.auth.api.user.PortalUserVo;
import group.flyfish.dev.shop.domain.po.ShopOrder;
import group.flyfish.dev.shop.repository.ShopOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.Optional;

import static group.flyfish.dev.shop.donation.DonationThankYouTemplateTest.donor;
import static group.flyfish.dev.shop.donation.DonationThankYouTemplateTest.paidOrder;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DonationThankYouDispatcherTest {

    private final DonationThankYouRepository repository = mock(DonationThankYouRepository.class);
    private final ShopOrderRepository orders = mock(ShopOrderRepository.class);
    private final AuthUserGateway users = mock(AuthUserGateway.class);
    private final DonationThankYouMailSender mail = mock(DonationThankYouMailSender.class);
    private final DonationThankYouFollowUpService tickets = mock(DonationThankYouFollowUpService.class);
    private final DonationThankYouDispatcher dispatcher = new DonationThankYouDispatcher(repository, orders, users, mail, tickets);
    private DonationThankYou task;
    private ShopOrder order;
    private PortalUserVo user;

    @BeforeEach
    void setup() {
        task = new DonationThankYou();
        task.setId(1L);
        task.setOrderNo("FFTHANKS1");
        task.setAttempts(0);
        order = paidOrder();
        user = donor("donor@example.test");
        when(repository.findDue(any())).thenReturn(Flux.just(task));
        when(repository.claim(eq(1L), anyString(), any(), any())).thenReturn(Mono.just(1));
        when(repository.complete(eq(1L), anyString(), anyString(), nullable(String.class), any())).thenReturn(Mono.just(1));
        when(repository.retry(eq(1L), anyString(), anyString(), any(), any())).thenReturn(Mono.just(1));
        when(orders.findByOrderNo("FFTHANKS1")).thenReturn(Mono.just(order));
        when(users.getById(100L)).thenReturn(Mono.just(user));
        when(mail.recipient(user)).thenReturn(Optional.of(user.getEmail()));
        when(mail.send(order, user, user.getEmail())).thenReturn(Mono.empty());
        when(tickets.create(eq(order), nullable(PortalUserVo.class), anyString())).thenReturn(Mono.just("DT101"));
    }

    @Test
    void sendsOnceForClaimedTask() {
        StepVerifier.create(dispatcher.runBatch()).verifyComplete();
        verify(mail).send(order, user, user.getEmail());
        verify(repository).complete(eq(1L), anyString(), eq("SENT"), isNull(), any());
        verifyNoInteractions(tickets);
    }

    @Test
    void duplicateWorkerCannotSend() {
        when(repository.claim(eq(1L), anyString(), any(), any())).thenReturn(Mono.just(0));
        StepVerifier.create(dispatcher.runBatch()).verifyComplete();
        verifyNoInteractions(users, mail, tickets);
    }

    @Test
    void noEmailCreatesFollowUpInsteadOfMail() {
        when(mail.recipient(user)).thenReturn(Optional.empty());
        StepVerifier.create(dispatcher.runBatch()).verifyComplete();
        verify(tickets).create(eq(order), eq(user), contains("未设置有效邮箱"));
        verify(mail, never()).send(any(), any(), any());
        verify(repository).complete(eq(1L), anyString(), eq("TICKET"), eq("DT101"), any());
    }

    @Test
    void mailErrorSchedulesRetryAndDoesNotLoseTask() {
        when(mail.send(order, user, user.getEmail())).thenReturn(Mono.error(new MailSendException("secret must not be logged")));
        StepVerifier.create(dispatcher.runBatch()).verifyComplete();
        verify(repository).retry(eq(1L), anyString(), eq("MailSendException"),
                argThat(time -> time.isAfter(LocalDateTime.now().plusSeconds(20))), any());
        verify(repository, never()).complete(any(), any(), any(), any(), any());
        verifyNoInteractions(tickets);
    }

    @Test
    void repeatedFailuresEscalateToOneFollowUp() {
        task.setAttempts(4);
        when(mail.send(order, user, user.getEmail())).thenReturn(Mono.error(new MailSendException("failure")));
        StepVerifier.create(dispatcher.runBatch()).verifyComplete();
        verify(tickets).create(eq(order), isNull(), contains("多次未能完成"));
        verify(repository).complete(eq(1L), anyString(), eq("TICKET"), eq("DT101"), any());
    }

    @Test
    void authFailureIsNotTreatedAsMissingEmail() {
        when(users.getById(100L)).thenReturn(Mono.error(new IllegalStateException("Auth unavailable")));
        StepVerifier.create(dispatcher.runBatch()).verifyComplete();
        verifyNoInteractions(mail, tickets);
        verify(repository).retry(eq(1L), anyString(), eq("IllegalStateException"), any(), any());
    }

    @Test
    void failedTicketCreationRetriesInsteadOfClaimingSuccess() {
        when(mail.recipient(user)).thenReturn(Optional.empty());
        when(tickets.create(eq(order), eq(user), anyString())).thenReturn(Mono.error(new IllegalStateException("DB unavailable")));
        StepVerifier.create(dispatcher.runBatch()).verifyComplete();
        verify(repository).retry(eq(1L), anyString(), anyString(), any(), any());
        verify(repository, never()).complete(any(), any(), any(), any(), any());
    }

    @Test
    void closedOrderIsSkippedAndShutdownReleasesDispatcher() {
        order.setStatus(ShopOrder.Status.CLOSED);
        StepVerifier.create(dispatcher.runBatch()).verifyComplete();
        verify(repository).complete(eq(1L), anyString(), eq("SKIPPED"), isNull(), any());
        verifyNoInteractions(mail, users, tickets);
        dispatcher.stop();
        clearInvocations(repository);
        dispatcher.dispatch();
        verifyNoInteractions(repository);
    }
}

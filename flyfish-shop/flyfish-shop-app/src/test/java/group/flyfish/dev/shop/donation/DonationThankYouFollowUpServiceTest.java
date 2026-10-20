package group.flyfish.dev.shop.donation;

import group.flyfish.dev.customer.service.CustomerRealtimeNotifier;
import group.flyfish.dev.support.domain.po.SupportTicket;
import group.flyfish.dev.support.domain.po.SupportTicketMessage;
import group.flyfish.dev.support.notification.SupportTicketNotificationService;
import group.flyfish.dev.support.repository.SupportTicketMessageRepository;
import group.flyfish.dev.support.repository.SupportTicketRepository;
import io.r2dbc.spi.ConnectionFactories;
import org.junit.jupiter.api.Test;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicReference;

import static group.flyfish.dev.shop.donation.DonationThankYouTemplateTest.donor;
import static group.flyfish.dev.shop.donation.DonationThankYouTemplateTest.paidOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DonationThankYouFollowUpServiceTest {

    @Test
    void createsOneSystemTicketWithAdminUnreadAndNotifiesOnlyAfterCommit() {
        var tickets = mock(SupportTicketRepository.class);
        var messages = mock(SupportTicketMessageRepository.class);
        var notifications = mock(SupportTicketNotificationService.class);
        var realtime = mock(CustomerRealtimeNotifier.class);
        var service = new DonationThankYouFollowUpService(tickets, messages, notifications, realtime);
        var transaction = TransactionalOperator.create(new R2dbcTransactionManager(
                ConnectionFactories.get("r2dbc:h2:mem:///donation-ticket;DB_CLOSE_DELAY=-1")));
        AtomicReference<SupportTicket> saved = new AtomicReference<>();
        when(tickets.findByTicketNo("DT101")).thenAnswer(i -> Mono.justOrEmpty(saved.get()));
        when(tickets.save(any())).thenAnswer(i -> {
            SupportTicket ticket = i.getArgument(0);
            ticket.setId(1001L);
            saved.set(ticket);
            return Mono.just(ticket);
        });
        when(messages.save(any())).thenAnswer(i -> {
            SupportTicketMessage message = i.getArgument(0);
            assertThat(message.getSenderRole()).isEqualTo("SYSTEM");
            assertThat(message.getSenderId()).isZero();
            assertThat(message.getContent()).contains("小明", "FFTHANKS1", "CNY 18.88", "未设置邮箱", "系统自动创建");
            return Mono.just(message);
        });
        when(notifications.systemFollowUpCreated(any(), any())).thenReturn(Mono.empty());
        StepVerifier.create(service.create(paidOrder(), donor(null), "未设置邮箱")
                        .doOnNext(ticket -> verifyNoInteractions(notifications, realtime))
                        .as(transaction::transactional))
                .expectNext("DT101").verifyComplete();
        assertThat(saved.get().getCreatorId()).isEqualTo(100L);
        assertThat(saved.get().getAdminUnreadCount()).isEqualTo(1);
        assertThat(saved.get().getUserUnreadCount()).isZero();
        assertThat(saved.get().getStatus()).isEqualTo("OPEN");
        verify(realtime).ticketsChanged();
        verify(notifications).systemFollowUpCreated(any(), any());
        verify(notifications, never()).ticketCreated(any(), any());
        StepVerifier.create(service.create(paidOrder(), donor(null), "未设置邮箱").as(transaction::transactional))
                .expectNext("DT101").verifyComplete();
        verify(tickets, times(1)).save(any());
        verify(messages, times(1)).save(any());
        verify(realtime, times(1)).ticketsChanged();
    }
}

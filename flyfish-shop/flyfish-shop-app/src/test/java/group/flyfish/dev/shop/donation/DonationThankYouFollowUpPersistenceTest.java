package group.flyfish.dev.shop.donation;

import group.flyfish.dev.common.base.reactive.AuditDomain;
import group.flyfish.dev.customer.service.CustomerRealtimeNotifier;
import group.flyfish.dev.support.domain.po.SupportTicket;
import group.flyfish.dev.support.domain.po.SupportTicketMessage;
import group.flyfish.dev.support.notification.SupportTicketNotificationService;
import group.flyfish.dev.support.repository.SupportTicketMessageRepository;
import group.flyfish.dev.support.repository.SupportTicketRepository;
import io.r2dbc.spi.ConnectionFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.r2dbc.convert.MappingR2dbcConverter;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.dialect.DialectResolver;
import org.springframework.data.r2dbc.mapping.R2dbcMappingContext;
import org.springframework.data.relational.RelationalManagedTypes;
import org.springframework.data.relational.core.mapping.DefaultNamingStrategy;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.UUID;

import static group.flyfish.dev.shop.donation.DonationThankYouTemplateTest.donor;
import static group.flyfish.dev.shop.donation.DonationThankYouTemplateTest.paidOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DonationThankYouFollowUpPersistenceTest {

    private final SupportTicketRepository tickets = mock(SupportTicketRepository.class);
    private final SupportTicketMessageRepository messages = mock(SupportTicketMessageRepository.class);
    private final SupportTicketNotificationService notifications = mock(SupportTicketNotificationService.class);
    private final CustomerRealtimeNotifier realtime = mock(CustomerRealtimeNotifier.class);
    private R2dbcEntityTemplate template;
    private TransactionalOperator transaction;
    private DonationThankYouFollowUpService service;

    @BeforeEach
    void setup() {
        var connection = ConnectionFactories.get("r2dbc:h2:mem:///donation-followup-" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        var dialect = DialectResolver.getDialect(connection);
        var conversions = R2dbcCustomConversions.of(dialect);
        var context = R2dbcMappingContext.forPlainIdentifiers(DefaultNamingStrategy.INSTANCE);
        context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        context.setManagedTypes(RelationalManagedTypes.from(SupportTicket.class, SupportTicketMessage.class));
        template = new R2dbcEntityTemplate(DatabaseClient.create(connection), dialect,
                new MappingR2dbcConverter(context, conversions));
        transaction = TransactionalOperator.create(new R2dbcTransactionManager(connection));
        service = new DonationThankYouFollowUpService(tickets, messages, notifications, realtime);
        StepVerifier.create(new ResourceDatabasePopulator(new ClassPathResource("schema/40-shop.sql"))
                .populate(connection)).verifyComplete();
        when(tickets.findByTicketNo("DT101")).thenAnswer(i -> template.selectOne(
                Query.query(Criteria.where("ticket_no").is("DT101")), SupportTicket.class));
        when(tickets.save(any())).thenAnswer(i -> template.insert(audit(i.getArgument(0, SupportTicket.class))));
        when(messages.save(any())).thenAnswer(i -> template.insert(audit(i.getArgument(0, SupportTicketMessage.class))));
        when(notifications.systemFollowUpCreated(any(), any())).thenReturn(Mono.empty());
    }

    @Test
    void persistsRealSystemMessageAndUnreadTicketWithLongNickname() {
        var user = donor(null);
        user.setUsername("谢".repeat(120));
        StepVerifier.create(service.create(paidOrder(), user, "没有邮箱").as(transaction::transactional))
                .expectNext("DT101").verifyComplete();
        StepVerifier.create(template.select(SupportTicket.class).all())
                .assertNext(ticket -> {
                    assertThat(ticket.getTitle()).hasSize(120);
                    assertThat(ticket.getAdminUnreadCount()).isEqualTo(1);
                    assertThat(ticket.getCreatorId()).isEqualTo(100L);
                }).verifyComplete();
        StepVerifier.create(template.select(SupportTicketMessage.class).all())
                .assertNext(message -> {
                    assertThat(message.getSenderId()).isZero();
                    assertThat(message.getSenderRole()).isEqualTo("SYSTEM");
                    assertThat(message.getContent()).contains("FFTHANKS1", "18.88");
                }).verifyComplete();
        StepVerifier.create(service.create(paidOrder(), user, "没有邮箱").as(transaction::transactional)
                        .then(template.count(Query.empty(), SupportTicketMessage.class)))
                .expectNext(1L).verifyComplete();
    }

    @Test
    void messageFailureRollsBackTicketAndDoesNotNotify() {
        doReturn(Mono.error(new IllegalStateException("Message failure"))).when(messages).save(any());
        StepVerifier.create(service.create(paidOrder(), donor(null), "没有邮箱").as(transaction::transactional))
                .expectError(IllegalStateException.class).verify();
        StepVerifier.create(template.count(Query.empty(), SupportTicket.class)).expectNext(0L).verifyComplete();
        verifyNoInteractions(notifications, realtime);
    }

    private <T extends AuditDomain> T audit(T row) {
        // 独立映射 harness 补齐生产 ReactiveAuditingEntityCallback 维护的时间。
        row.setCreateTime(LocalDateTime.now());
        row.setUpdateTime(LocalDateTime.now());
        return row;
    }
}

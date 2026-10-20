package group.flyfish.dev.shop.donation;

import io.r2dbc.spi.ConnectionFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.r2dbc.convert.MappingR2dbcConverter;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.dialect.DialectResolver;
import org.springframework.data.r2dbc.mapping.R2dbcMappingContext;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory;
import org.springframework.data.relational.RelationalManagedTypes;
import org.springframework.data.relational.core.mapping.DefaultNamingStrategy;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.UUID;

import static group.flyfish.dev.shop.donation.DonationThankYouTemplateTest.paidOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DonationThankYouPersistenceTest {

    private DonationThankYouRepository repository;
    private TransactionalOperator transaction;
    private DonationThankYouService service;
    private DonationThankYouDispatcher dispatcher;

    @BeforeEach
    void setup() {
        var connection = ConnectionFactories.get("r2dbc:h2:mem:///donation-" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        var dialect = DialectResolver.getDialect(connection);
        var conversions = R2dbcCustomConversions.of(dialect);
        var context = R2dbcMappingContext.forPlainIdentifiers(DefaultNamingStrategy.INSTANCE);
        context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        context.setManagedTypes(RelationalManagedTypes.from(DonationThankYou.class));
        var template = new R2dbcEntityTemplate(DatabaseClient.create(connection), dialect,
                new MappingR2dbcConverter(context, conversions));
        repository = new R2dbcRepositoryFactory(template).getRepository(DonationThankYouRepository.class);
        transaction = TransactionalOperator.create(new R2dbcTransactionManager(connection));
        dispatcher = mock(DonationThankYouDispatcher.class);
        service = new DonationThankYouService(repository, dispatcher);
        var schema = new ResourceDatabasePopulator(new ClassPathResource("schema/50-shop-donation-thanks.sql"));
        StepVerifier.create(schema.populate(connection).then(schema.populate(connection))).verifyComplete();
    }

    @Test
    void paymentCommitCreatesOneTaskAndOnlyThenWakesDispatcher() {
        var order = paidOrder();
        StepVerifier.create(service.recordPaid(order)
                        .then(Mono.fromRunnable(() -> verifyNoInteractions(dispatcher)))
                        .as(transaction::transactional))
                .verifyComplete();
        verify(dispatcher).dispatch();
        StepVerifier.create(Flux.range(0, 8)
                        .flatMap(i -> service.recordPaid(order).as(transaction::transactional), 2)
                        .then(repository.count()))
                .expectNext(1L).verifyComplete();
        StepVerifier.create(repository.findDue(LocalDateTime.now()))
                .assertNext(task -> {
                    assertThat(task.getOrderNo()).isEqualTo(order.getOrderNo());
                    assertThat(task.getState()).isEqualTo("PENDING");
                    assertThat(task.getAttempts()).isZero();
                }).verifyComplete();
    }

    @Test
    void paymentRollbackDoesNotNotifyOrLeaveTask() {
        StepVerifier.create(service.recordPaid(paidOrder())
                        .then(Mono.error(new IllegalStateException("Payment rollback")))
                        .as(transaction::transactional))
                .expectError(IllegalStateException.class).verify();
        verifyNoInteractions(dispatcher);
        StepVerifier.create(repository.count()).expectNext(0L).verifyComplete();
    }

    @Test
    void leaseIsExclusiveRecoverableAndCompletionIsFenced() {
        StepVerifier.create(repository.enqueue("FFLEASE")).expectNextCount(1).verifyComplete();
        DonationThankYou task = repository.findDue(LocalDateTime.now()).single().block();
        LocalDateTime now = LocalDateTime.now();
        StepVerifier.create(repository.claim(task.getId(), "worker-1", now, now.plusMinutes(5)))
                .expectNext(1).verifyComplete();
        StepVerifier.create(repository.claim(task.getId(), "worker-2", now, now.plusMinutes(5)))
                .expectNext(0).verifyComplete();
        StepVerifier.create(repository.findDue(now)).verifyComplete();
        StepVerifier.create(repository.findDue(now.plusMinutes(6))).expectNextCount(1).verifyComplete();
        StepVerifier.create(repository.claim(task.getId(), "worker-2", now.plusMinutes(6), now.plusMinutes(11)))
                .expectNext(1).verifyComplete();
        StepVerifier.create(repository.complete(task.getId(), "worker-1", "SENT", null, now))
                .expectNext(0).verifyComplete();
        StepVerifier.create(repository.complete(task.getId(), "worker-2", "TICKET", "DT101", now))
                .expectNext(1).verifyComplete();
        StepVerifier.create(repository.enqueue("FFLEASE").then(repository.findById(task.getId())))
                .assertNext(done -> {
                    assertThat(done.getState()).isEqualTo("TICKET");
                    assertThat(done.getAttempts()).isEqualTo(2);
                    assertThat(done.getTicketNo()).isEqualTo("DT101");
                }).verifyComplete();
        StepVerifier.create(repository.findDue(now.plusDays(1))).verifyComplete();
    }

    @Test
    void retryPersistsBackoffAndNonDonationDoesNotCreateTask() {
        StepVerifier.create(repository.enqueue("FFRETRY")).expectNextCount(1).verifyComplete();
        DonationThankYou task = repository.findDue(LocalDateTime.now()).single().block();
        LocalDateTime now = LocalDateTime.now();
        StepVerifier.create(repository.claim(task.getId(), "worker", now, now.plusMinutes(5))
                        .then(repository.retry(task.getId(), "worker", "MailSendException", now.plusMinutes(1), now)))
                .expectNext(1).verifyComplete();
        StepVerifier.create(repository.findDue(now)).verifyComplete();
        StepVerifier.create(repository.findDue(now.plusMinutes(2)))
                .assertNext(retry -> assertThat(retry.getLastError()).isEqualTo("MailSendException"))
                .verifyComplete();
        var nonDonation = paidOrder();
        nonDonation.setItemType("SERVICE_PACKAGE");
        StepVerifier.create(service.recordPaid(nonDonation)).verifyComplete();
        StepVerifier.create(repository.count()).expectNext(1L).verifyComplete();
        verifyNoInteractions(dispatcher);
    }
}

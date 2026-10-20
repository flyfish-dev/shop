package group.flyfish.dev.shop.donation;

import group.flyfish.dev.auth.api.client.AuthUserGateway;
import group.flyfish.dev.shop.domain.po.ShopOrder;
import group.flyfish.dev.shop.repository.ShopOrderRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** 小批次、有界并发的通知分发器；数据库租约支持重启恢复及多实例互斥。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DonationThankYouDispatcher {

    private static final int MAX_AUTOMATIC_ATTEMPTS = 5;
    private static final Duration LEASE = Duration.ofMinutes(5);

    private final DonationThankYouRepository repository;
    private final ShopOrderRepository orderRepository;
    private final AuthUserGateway users;
    private final DonationThankYouMailSender mailSender;
    private final DonationThankYouFollowUpService followUpService;
    private final AtomicBoolean running = new AtomicBoolean();
    private final Disposable.Swap subscription = Disposables.swap();

    @Scheduled(initialDelayString = "${shop.donation.thank-you.scan-delay:5000}",
            fixedDelayString = "${shop.donation.thank-you.scan-delay:5000}")
    public synchronized void dispatch() {
        if (subscription.isDisposed() || !running.compareAndSet(false, true)) {
            return;
        }
        subscription.update(Mono.defer(this::runBatch)
                .doFinally(signal -> running.set(false))
                .subscribe(ignored -> { }, error -> log.warn("打赏感谢任务扫描失败。error={}",
                        error.getClass().getSimpleName())));
    }

    Mono<Void> runBatch() {
        return repository.findDue(LocalDateTime.now())
                .flatMap(task -> claimAndSend(task).onErrorResume(error -> {
                    // 单条任务的数据库故障不能取消另一条正在发送的邮件；未完成租约可由后续扫描恢复。
                    log.warn("打赏感谢任务暂不可用，将在租约到期后恢复。order={}, error={}",
                            task.getOrderNo(), error.getClass().getSimpleName());
                    return Mono.empty();
                }), 2)
                .then();
    }

    private Mono<Void> claimAndSend(DonationThankYou task) {
        String token = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        return repository.claim(task.getId(), token, now, now.plus(LEASE))
                .filter(count -> count == 1)
                .flatMap(ignored -> {
                    task.setAttempts(task.getAttempts() + 1);
                    return orderRepository.findByOrderNo(task.getOrderNo())
                            .switchIfEmpty(Mono.error(new IllegalStateException("Donation order not found")))
                            .flatMap(order -> send(task, token, order))
                            .onErrorResume(error -> {
                                long delay = Math.min(3600L, 30L << Math.min(7, task.getAttempts() - 1));
                                log.warn("打赏感谢通知等待重试。order={}, attempt={}, error={}",
                                        task.getOrderNo(), task.getAttempts(), error.getClass().getSimpleName());
                                return repository.retry(task.getId(), token, error.getClass().getSimpleName(),
                                        LocalDateTime.now().plusSeconds(delay), LocalDateTime.now()).then();
                            });
                });
    }

    private Mono<Void> send(DonationThankYou task, String token, ShopOrder order) {
        if (!DonationThankYouService.eligible(order)) {
            return complete(task, token, "SKIPPED", null);
        }
        return users.getById(order.getBuyerId())
                .timeout(Duration.ofSeconds(15))
                .switchIfEmpty(Mono.error(new IllegalStateException("Donor profile not found")))
                .flatMap(user -> mailSender.recipient(user)
                        .map(email -> mailSender.send(order, user, email)
                                .then(Mono.defer(() -> complete(task, token, "SENT", null))))
                        .orElseGet(() -> followUpService.create(order, user, "用户资料未设置有效邮箱，无法发送感谢邮件。")
                                .flatMap(ticket -> complete(task, token, "TICKET", ticket))))
                .onErrorResume(error -> task.getAttempts() < MAX_AUTOMATIC_ATTEMPTS
                        ? Mono.error(error)
                        : followUpService.create(order, null, "自动感谢通知多次未能完成，请核实用户资料并人工致谢。")
                                .flatMap(ticket -> complete(task, token, "TICKET", ticket)));
    }

    private Mono<Void> complete(DonationThankYou task, String token, String state, String ticket) {
        return repository.complete(task.getId(), token, state, ticket, LocalDateTime.now())
                .flatMap(count -> count == 1 ? Mono.empty()
                        : Mono.error(new IllegalStateException("Donation notification lease lost")));
    }

    @PreDestroy
    public synchronized void stop() {
        subscription.dispose();
    }
}

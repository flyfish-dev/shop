package group.flyfish.dev.shop.donation;

import group.flyfish.dev.shop.domain.po.ShopItem;
import group.flyfish.dev.shop.domain.po.ShopOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionSynchronization;
import org.springframework.transaction.reactive.TransactionSynchronizationManager;
import reactor.core.publisher.Mono;

/** 将感谢任务与付款确认一同提交，邮件发送不占用支付事务。 */
@Service
@RequiredArgsConstructor
public class DonationThankYouService {

    private final DonationThankYouRepository repository;
    private final DonationThankYouDispatcher dispatcher;

    public Mono<Void> recordPaid(ShopOrder order) {
        if (!eligible(order)) {
            return Mono.empty();
        }
        return repository.enqueue(order.getOrderNo())
                .then(TransactionSynchronizationManager.forCurrentTransaction())
                .doOnNext(manager -> manager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public Mono<Void> afterCommit() {
                        return Mono.fromRunnable(dispatcher::dispatch);
                    }
                }))
                .then();
    }

    static boolean eligible(ShopOrder order) {
        return order != null && order.getPaidTime() != null
                && order.getStatus() != ShopOrder.Status.CLOSED
                && order.getStatus() != ShopOrder.Status.PENDING
                && order.getStatus() != ShopOrder.Status.PAYING
                && order.getAmount() != null && order.getAmount().signum() > 0
                && (ShopItem.Type.DONATION.name().equals(order.getItemType())
                || ShopItem.Type.GIT_REPOSITORY_DONATION_ACCESS.name().equals(order.getItemType()));
    }
}

package group.flyfish.dev.shop.donation;

import group.flyfish.dev.auth.api.user.PortalUserVo;
import group.flyfish.dev.customer.service.CustomerRealtimeNotifier;
import group.flyfish.dev.shop.domain.po.ShopOrder;
import group.flyfish.dev.support.domain.po.SupportTicket;
import group.flyfish.dev.support.domain.po.SupportTicketMessage;
import group.flyfish.dev.support.notification.SupportTicketNotificationService;
import group.flyfish.dev.support.repository.SupportTicketMessageRepository;
import group.flyfish.dev.support.repository.SupportTicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.reactive.TransactionSynchronization;
import org.springframework.transaction.reactive.TransactionSynchronizationManager;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;

/** 系统创建的感谢跟进工单，与普通工单共用提醒、回复和处理流程。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DonationThankYouFollowUpService {

    private final SupportTicketRepository ticketRepository;
    private final SupportTicketMessageRepository messageRepository;
    private final SupportTicketNotificationService notificationService;
    private final CustomerRealtimeNotifier realtimeNotifier;

    @Transactional
    public Mono<String> create(ShopOrder order, PortalUserVo user, String reason) {
        String ticketNo = "DT" + Objects.requireNonNull(order.getId(), "Paid order ID is required");
        return ticketRepository.findByTicketNo(ticketNo)
                .switchIfEmpty(Mono.defer(() -> createTicket(ticketNo, order, user, reason)))
                .map(SupportTicket::getTicketNo);
    }

    private Mono<SupportTicket> createTicket(String ticketNo, ShopOrder order, PortalUserVo user, String reason) {
        String nickname = user == null ? "用户 " + order.getBuyerId()
                : StringUtils.defaultIfBlank(user.getUsername(), "用户 " + order.getBuyerId());
        String content = """
                【系统自动创建 · 打赏感谢跟进】
                感谢 %s 对飞鱼的支持！这份心意我们已经收到。

                打赏订单：%s
                支持项目：%s
                实付金额：%s %s
                跟进原因：%s

                请管理员通过站内客服或用户已绑定的联系方式，向打赏者表达感谢；完成联系后将此工单标记为已解决。
                这是系统生成的跟进记录，不是用户主动提交的故障报告。
                """.formatted(nickname, order.getOrderNo(), StringUtils.defaultIfBlank(order.getItemName(), "支持飞鱼"),
                StringUtils.defaultIfBlank(order.getCurrency(), "CNY"), order.getAmount().toPlainString(), reason);
        SupportTicket ticket = new SupportTicket();
        ticket.setTicketNo(ticketNo);
        ticket.setCreatorId(order.getBuyerId());
        ticket.setTitle(StringUtils.left("打赏感谢跟进 · " + nickname, 120));
        ticket.setCategory("GENERAL");
        ticket.setPriority(SupportTicket.Priority.NORMAL.name());
        ticket.setStatus(SupportTicket.Status.OPEN.name());
        ticket.setLastMessage(StringUtils.left(content, 1024));
        ticket.setAdminUnreadCount(1);
        ticket.setUserUnreadCount(0);
        ticket.setCreateBy("donation-thanks");
        ticket.setUpdateBy("donation-thanks");
        return ticketRepository.save(ticket).flatMap(saved -> {
            SupportTicketMessage message = new SupportTicketMessage();
            message.setTicketId(saved.getId());
            message.setSenderId(0L);
            message.setSenderRole(SupportTicketMessage.SenderRole.SYSTEM.name());
            message.setContent(content);
            message.setCreateBy("donation-thanks");
            message.setUpdateBy("donation-thanks");
            return messageRepository.save(message)
                    .flatMap(firstMessage -> TransactionSynchronizationManager.forCurrentTransaction()
                            .doOnNext(manager -> manager.registerSynchronization(new TransactionSynchronization() {
                                @Override
                                public Mono<Void> afterCommit() {
                                    return Mono.fromRunnable(realtimeNotifier::ticketsChanged)
                                            .then(Mono.defer(() -> notificationService.systemFollowUpCreated(saved, firstMessage)))
                                            .timeout(Duration.ofSeconds(30))
                                            .onErrorResume(error -> {
                                                log.warn("打赏跟进工单提醒失败，工单已保留。ticket={}, error={}",
                                                        saved.getTicketNo(), error.getClass().getSimpleName());
                                                return Mono.empty();
                                            });
                                }
                            })))
                    .thenReturn(saved);
        });
    }
}

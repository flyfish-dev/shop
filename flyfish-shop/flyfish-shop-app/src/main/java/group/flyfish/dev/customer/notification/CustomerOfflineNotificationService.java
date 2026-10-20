package group.flyfish.dev.customer.notification;

import group.flyfish.dev.customer.domain.po.CustomerConversation;
import group.flyfish.dev.customer.domain.po.CustomerMessage;
import group.flyfish.dev.customer.service.CustomerPresenceRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 客服离线提醒编排服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerOfflineNotificationService {

    private final CustomerPresenceRegistry presenceRegistry;
    private final CustomerOfflineNotificationProperties properties;
    private final CustomerOfflineNotificationFactory notificationFactory;
    private final CustomerOfflineMailNotifier mailNotifier;
    private final CustomerOfflineWebhookNotifier webhookNotifier;

    public Mono<Void> notifyIfOffline(CustomerConversation conversation, CustomerMessage customerMessage) {
        if (!properties.isEnabled() || presenceRegistry.isManagerOnline()
                || conversation == null || customerMessage == null) {
            return Mono.empty();
        }
        return Mono.fromSupplier(() -> notificationFactory.create(conversation, customerMessage))
                .flatMap(notification -> Mono.whenDelayError(
                        mailNotifier.send(notification),
                        webhookNotifier.send(notification)
                ).then())
                .onErrorResume(error -> {
                    log.warn("客服离线提醒流程异常，已降级跳过。conversationId={}, error={}",
                            conversation.getId(), error.getMessage());
                    return Mono.empty();
                });
    }
}

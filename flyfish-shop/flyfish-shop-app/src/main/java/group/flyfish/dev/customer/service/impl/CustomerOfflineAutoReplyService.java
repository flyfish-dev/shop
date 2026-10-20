package group.flyfish.dev.customer.service.impl;

import group.flyfish.dev.common.base.reactive.AuditDomain;
import group.flyfish.dev.customer.notification.CustomerOfflineNotificationProperties;
import group.flyfish.dev.customer.notification.CustomerOfflineNotificationService;
import group.flyfish.dev.customer.domain.po.CustomerConversation;
import group.flyfish.dev.customer.domain.po.CustomerMessage;
import group.flyfish.dev.customer.repository.CustomerConversationRepository;
import group.flyfish.dev.customer.repository.CustomerMessageRepository;
import group.flyfish.dev.customer.service.CustomerPresenceRegistry;
import group.flyfish.dev.customer.service.CustomerRealtimeNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 客服离线自动回复。
 *
 * <p>该服务只处理站内客户消息：管理员存在有效 WebSocket 会话时不回复；客服离线时，
 * 同一会话在冷却期内只写入一条系统提示。数据库历史记录保证应用重启后仍能防重复，
 * {@code inFlightReplies} 则合并同一实例内的并发请求，避免客户连续发送消息时被刷屏。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerOfflineAutoReplyService {

    public static final String RELATED_TYPE = "SUPPORT_OFFLINE_AUTO_REPLY";

    private static final String AUTO_REPLY_CONTENT =
            "客服当前不在线，我们已经收到你的消息，后续会尽快回复。若需更快获得帮助，"
                    + "请扫描下方二维码添加客服微信 Yous_Gift。";
    private static final String LAST_MESSAGE_SUMMARY = "客服离线自动回复：已引导添加客服微信";
    private static final String AUDIT_SYSTEM = "customer-service";
    private final CustomerConversationRepository conversationRepository;
    private final CustomerMessageRepository messageRepository;
    private final CustomerPresenceRegistry presenceRegistry;
    private final CustomerRealtimeNotifier realtimeNotifier;
    private final CustomerOfflineNotificationProperties notificationProperties;
    private final CustomerOfflineNotificationService notificationService;
    private final ConcurrentMap<Long, Mono<CustomerConversation>> inFlightReplies = new ConcurrentHashMap<>();

    public Mono<CustomerConversation> replyIfNeeded(CustomerConversation conversation, CustomerMessage customerMessage) {
        if (conversation == null || conversation.getId() == null || presenceRegistry.isManagerOnline()) {
            return Mono.justOrEmpty(conversation);
        }
        Long conversationId = conversation.getId();
        Mono<CustomerConversation> operation = inFlightReplies.computeIfAbsent(conversationId,
                ignored -> Mono.defer(() -> evaluateAndReply(conversation, customerMessage)).cache());
        return operation.doFinally(signal -> inFlightReplies.remove(conversationId, operation));
    }

    private Mono<CustomerConversation> evaluateAndReply(CustomerConversation conversation, CustomerMessage customerMessage) {
        if (presenceRegistry.isManagerOnline()) {
            return Mono.just(conversation);
        }
        LocalDateTime cutoff = LocalDateTime.now().minus(replyCooldown());
        return messageRepository.findLatestByRelatedType(conversation.getId(), RELATED_TYPE)
                .map(CustomerMessage::getCreateTime)
                .filter(time -> time != null && time.isAfter(cutoff))
                .hasElement()
                .onErrorResume(error -> {
                    log.warn("查询客服离线自动回复历史失败，本次跳过自动回复。conversationId={}",
                            conversation.getId(), error);
                    return Mono.just(true);
                })
                .flatMap(repliedRecently -> repliedRecently
                        ? Mono.just(conversation)
                        : persistReply(conversation, customerMessage));
    }

    private Mono<CustomerConversation> persistReply(CustomerConversation conversation, CustomerMessage customerMessage) {
        LocalDateTime now = LocalDateTime.now();
        conversation.setLastMessage(LAST_MESSAGE_SUMMARY);
        conversation.setLastMessageTime(now);
        conversation.setUserUnreadCount(safe(conversation.getUserUnreadCount()) + 1);
        applyAudit(conversation);

        CustomerMessage message = new CustomerMessage();
        message.setConversationId(conversation.getId());
        message.setUserId(conversation.getUserId());
        message.setWechatOpenid(conversation.getWechatOpenid());
        message.setDirection(CustomerMessage.Direction.OUTBOUND.name());
        message.setChannel(CustomerMessage.Channel.WEB.name());
        message.setSenderRole(CustomerMessage.SenderRole.SYSTEM.name());
        message.setMessageType("text");
        message.setContent(AUTO_REPLY_CONTENT);
        message.setRelatedType(RELATED_TYPE);
        message.setSendStatus(CustomerMessage.SendStatus.SENT.name());
        message.setReadByAdmin(true);
        message.setReadByUser(false);
        applyAudit(message);

        return conversationRepository.save(conversation)
                .flatMap(saved -> messageRepository.save(message)
                        .then(notificationService.notifyIfOffline(saved, customerMessage))
                        .thenReturn(saved))
                .doOnNext(realtimeNotifier::conversationChanged);
    }

    private Duration replyCooldown() {
        Duration configured = notificationProperties.getCooldown();
        return configured == null || configured.isZero() || configured.isNegative()
                ? Duration.ofHours(8)
                : configured;
    }

    private void applyAudit(AuditDomain domain) {
        if (StringUtils.isBlank(domain.getCreateBy())) {
            domain.setCreateBy(AUDIT_SYSTEM);
        }
        domain.setUpdateBy(AUDIT_SYSTEM);
        if (domain.getDelete() == null) {
            domain.setDelete(false);
        }
    }

    private int safe(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }
}

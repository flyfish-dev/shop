package group.flyfish.dev.customer.notification;

import java.time.OffsetDateTime;

/**
 * 客服离线消息 Webhook 载荷。
 *
 * @param event           事件类型
 * @param eventId         幂等事件编号
 * @param occurredAt      客户消息发生时间
 * @param conversationId  客服会话 ID
 * @param userId          平台用户 ID
 * @param customerName    客户展示名称
 * @param channel         消息来源渠道
 * @param messageType     消息类型
 * @param messagePreview  脱敏后的消息摘要
 * @param conversationUrl 管理员处理入口
 */
public record CustomerOfflineWebhookPayload(
        String event,
        String eventId,
        OffsetDateTime occurredAt,
        Long conversationId,
        Long userId,
        String customerName,
        String channel,
        String messageType,
        String messagePreview,
        String conversationUrl
) {
}

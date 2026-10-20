package group.flyfish.dev.customer.notification;

/**
 * 一次客服离线提醒的通道无关消息。
 *
 * @param subject  邮件主题
 * @param mailBody 邮件正文
 * @param payload  Webhook 结构化载荷
 */
public record CustomerOfflineNotification(
        String subject,
        String mailBody,
        CustomerOfflineWebhookPayload payload
) {
}

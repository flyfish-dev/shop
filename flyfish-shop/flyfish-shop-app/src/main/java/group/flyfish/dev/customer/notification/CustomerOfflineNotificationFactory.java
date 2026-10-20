package group.flyfish.dev.customer.notification;

import group.flyfish.dev.customer.domain.po.CustomerConversation;
import group.flyfish.dev.customer.domain.po.CustomerMessage;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 客服离线提醒文案和结构化事件工厂。
 */
@Component
@RequiredArgsConstructor
public class CustomerOfflineNotificationFactory {

    public static final String EVENT_TYPE = "customer.service.offline_message";

    private static final int PREVIEW_MAX_LENGTH = 500;

    private final CustomerOfflineNotificationProperties properties;

    public CustomerOfflineNotification create(CustomerConversation conversation, CustomerMessage message) {
        String customerName = customerName(conversation);
        String preview = messagePreview(message);
        String conversationUrl = conversationUrl(conversation.getId());
        OffsetDateTime occurredAt = occurredAt(message);
        String eventId = eventId(conversation, message, occurredAt);
        String subject = "[飞鱼小铺] 客服离线期间收到新消息 - " + customerName;
        String body = """
                飞鱼小铺客服离线期间收到一条新的客户消息，请及时跟进。

                客户：%s
                用户 ID：%s
                会话 ID：%s
                消息渠道：%s
                消息类型：%s
                消息时间：%s
                消息摘要：%s

                处理入口：%s

                此提醒仅在没有管理员客服在线时发送，并按会话冷却周期自动去重。
                """.formatted(
                customerName,
                value(conversation.getUserId()),
                value(conversation.getId()),
                defaultValue(message.getChannel(), "WEB"),
                defaultValue(message.getMessageType(), "text"),
                occurredAt,
                preview,
                conversationUrl
        );
        CustomerOfflineWebhookPayload payload = new CustomerOfflineWebhookPayload(
                EVENT_TYPE,
                eventId,
                occurredAt,
                conversation.getId(),
                conversation.getUserId(),
                customerName,
                defaultValue(message.getChannel(), "WEB"),
                defaultValue(message.getMessageType(), "text"),
                preview,
                conversationUrl
        );
        return new CustomerOfflineNotification(subject, body, payload);
    }

    private String customerName(CustomerConversation conversation) {
        if (StringUtils.isNotBlank(conversation.getDisplayName())) {
            return conversation.getDisplayName().trim();
        }
        return "客户 #" + value(conversation.getUserId() == null
                ? conversation.getId()
                : conversation.getUserId());
    }

    private String messagePreview(CustomerMessage message) {
        String content = StringUtils.normalizeSpace(message.getContent());
        if (StringUtils.isBlank(content)) {
            content = switch (StringUtils.defaultString(message.getMessageType()).toLowerCase()) {
                case "image" -> "[图片]";
                case "file" -> "[附件]";
                default -> "[无文字内容]";
            };
        }
        return StringUtils.abbreviate(content, PREVIEW_MAX_LENGTH);
    }

    private OffsetDateTime occurredAt(CustomerMessage message) {
        if (message.getCreateTime() == null) {
            return OffsetDateTime.now();
        }
        return message.getCreateTime().atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    private String eventId(CustomerConversation conversation, CustomerMessage message, OffsetDateTime occurredAt) {
        if (message.getId() != null) {
            return "customer-message-" + message.getId();
        }
        return "customer-conversation-" + value(conversation.getId()) + "-" + occurredAt.toInstant().toEpochMilli();
    }

    private String conversationUrl(Long conversationId) {
        String baseUrl = StringUtils.removeEnd(
                StringUtils.defaultIfBlank(properties.getPortalBaseUrl(), "https://shop.example.com").trim(), "/");
        return baseUrl + "/shop/manage/workbench?customerConversation=" + value(conversationId);
    }

    private String defaultValue(String value, String fallback) {
        return StringUtils.defaultIfBlank(value, fallback);
    }

    private String value(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }
}

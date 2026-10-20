package group.flyfish.dev.customer.notification;

import group.flyfish.dev.customer.domain.po.CustomerConversation;
import group.flyfish.dev.customer.domain.po.CustomerMessage;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerOfflineNotificationFactoryTest {

    @Test
    void buildsTraceableNotificationWithoutRawPayload() {
        CustomerOfflineNotificationProperties properties = new CustomerOfflineNotificationProperties();
        properties.setPortalBaseUrl("https://dev.flyfish.group/");
        CustomerOfflineNotificationFactory factory = new CustomerOfflineNotificationFactory(properties);
        CustomerConversation conversation = conversation();
        CustomerMessage message = message();
        message.setRawPayload("sensitive-raw-payload");
        message.setAttachments("[{\"url\":\"private-file\"}]");

        CustomerOfflineNotification notification = factory.create(conversation, message);

        assertEquals(CustomerOfflineNotificationFactory.EVENT_TYPE, notification.payload().event());
        assertEquals("customer-message-88", notification.payload().eventId());
        assertEquals("客户小王", notification.payload().customerName());
        assertEquals("订单付款后还没有收到授权", notification.payload().messagePreview());
        assertEquals("https://dev.flyfish.group/shop/manage/workbench?customerConversation=12",
                notification.payload().conversationUrl());
        assertTrue(notification.mailBody().contains("客户小王"));
        assertTrue(notification.mailBody().contains("会话 ID：12"));
        assertFalse(notification.mailBody().contains("sensitive-raw-payload"));
        assertFalse(notification.mailBody().contains("private-file"));
    }

    @Test
    void describesImageWhenMessageHasNoText() {
        CustomerOfflineNotificationProperties properties = new CustomerOfflineNotificationProperties();
        CustomerOfflineNotificationFactory factory = new CustomerOfflineNotificationFactory(properties);
        CustomerMessage message = message();
        message.setContent(null);
        message.setMessageType("image");

        CustomerOfflineNotification notification = factory.create(conversation(), message);

        assertEquals("[图片]", notification.payload().messagePreview());
    }

    private CustomerConversation conversation() {
        CustomerConversation conversation = new CustomerConversation();
        conversation.setId(12L);
        conversation.setUserId(36L);
        conversation.setDisplayName("客户小王");
        return conversation;
    }

    private CustomerMessage message() {
        CustomerMessage message = new CustomerMessage();
        message.setId(88L);
        message.setContent("订单付款后还没有收到授权");
        message.setMessageType("text");
        message.setChannel(CustomerMessage.Channel.WEB.name());
        message.setCreateTime(LocalDateTime.of(2026, 8, 16, 9, 30));
        return message;
    }
}

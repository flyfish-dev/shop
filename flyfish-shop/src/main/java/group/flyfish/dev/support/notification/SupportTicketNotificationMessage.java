package group.flyfish.dev.support.notification;

public record SupportTicketNotificationMessage(String subject, String mailBody, String wechatText) {
}

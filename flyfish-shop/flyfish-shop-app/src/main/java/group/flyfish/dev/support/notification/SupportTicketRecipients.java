package group.flyfish.dev.support.notification;

import java.util.List;

public record SupportTicketRecipients(List<String> emails, List<String> wechatOpenids) {

    public static SupportTicketRecipients of(List<String> emails, List<String> wechatOpenids) {
        return new SupportTicketRecipients(List.copyOf(emails), List.copyOf(wechatOpenids));
    }
}

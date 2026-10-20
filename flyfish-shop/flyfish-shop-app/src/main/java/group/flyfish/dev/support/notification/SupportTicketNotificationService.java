package group.flyfish.dev.support.notification;

import group.flyfish.dev.support.domain.po.SupportTicket;
import group.flyfish.dev.support.domain.po.SupportTicketMessage;
import reactor.core.publisher.Mono;

public interface SupportTicketNotificationService {

    Mono<Void> ticketCreated(SupportTicket ticket, SupportTicketMessage message);

    /** 系统跟进工单仅提醒管理员，不向用户发送“已提交工单”的误导通知。 */
    Mono<Void> systemFollowUpCreated(SupportTicket ticket, SupportTicketMessage message);

    Mono<Void> userReplied(SupportTicket ticket, SupportTicketMessage message);

    Mono<Void> adminReplied(SupportTicket ticket, SupportTicketMessage message);

    Mono<Void> ticketResolved(SupportTicket ticket, SupportTicketMessage message);
}

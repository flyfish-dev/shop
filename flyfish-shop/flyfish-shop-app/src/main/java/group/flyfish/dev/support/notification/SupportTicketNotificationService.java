package group.flyfish.dev.support.notification;

import group.flyfish.dev.support.domain.po.SupportTicket;
import group.flyfish.dev.support.domain.po.SupportTicketMessage;
import reactor.core.publisher.Mono;

public interface SupportTicketNotificationService {

    Mono<Void> ticketCreated(SupportTicket ticket, SupportTicketMessage message);

    Mono<Void> userReplied(SupportTicket ticket, SupportTicketMessage message);

    Mono<Void> adminReplied(SupportTicket ticket, SupportTicketMessage message);

    Mono<Void> ticketResolved(SupportTicket ticket, SupportTicketMessage message);
}

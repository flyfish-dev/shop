package group.flyfish.dev.support.service;

import group.flyfish.dev.support.domain.dto.SupportTicketCreateDto;
import group.flyfish.dev.support.domain.dto.SupportTicketMessageDto;
import group.flyfish.dev.support.domain.vo.SupportTicketDetailVo;
import group.flyfish.dev.support.domain.vo.SupportTicketVo;
import group.flyfish.dev.user.domain.vo.PortalUserVo;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SupportTicketService {

    Flux<SupportTicketVo> getMyTickets(PortalUserVo user, String status);

    Mono<Long> countUnreadTickets(PortalUserVo user);

    Flux<SupportTicketVo> getUnreadTickets(PortalUserVo user, int limit);

    Mono<SupportTicketDetailVo> createTicket(PortalUserVo user, SupportTicketCreateDto dto);

    Mono<SupportTicketDetailVo> getMyTicket(PortalUserVo user, String ticketNo);

    Mono<SupportTicketDetailVo> addUserMessage(PortalUserVo user, String ticketNo, SupportTicketMessageDto dto);

    Flux<SupportTicketVo> getManagementTickets(PortalUserVo user, String status);

    Mono<SupportTicketDetailVo> getManagementTicket(PortalUserVo user, String ticketNo);

    Mono<SupportTicketDetailVo> addManagementMessage(PortalUserVo user, String ticketNo, SupportTicketMessageDto dto);

    Mono<SupportTicketDetailVo> resolveTicket(PortalUserVo user, String ticketNo);
}

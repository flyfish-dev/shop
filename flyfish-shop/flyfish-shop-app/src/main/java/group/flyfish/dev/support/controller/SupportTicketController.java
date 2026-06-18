package group.flyfish.dev.support.controller;

import group.flyfish.dev.annotations.user.CurrentUser;
import group.flyfish.dev.common.bean.Result;
import group.flyfish.dev.support.domain.dto.SupportTicketCreateDto;
import group.flyfish.dev.support.domain.dto.SupportTicketMessageDto;
import group.flyfish.dev.support.domain.vo.SupportTicketDetailVo;
import group.flyfish.dev.support.domain.vo.SupportTicketVo;
import group.flyfish.dev.support.service.SupportTicketService;
import group.flyfish.dev.auth.api.user.PortalUserVo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@Validated
@RestController
@RequestMapping("/portal/tickets")
@RequiredArgsConstructor
public class SupportTicketController {

    private final SupportTicketService supportTicketService;

    @GetMapping
    public Mono<Result<List<SupportTicketVo>>> getMyTickets(@CurrentUser PortalUserVo user,
                                                            @RequestParam(required = false) String status) {
        return supportTicketService.getMyTickets(user, status).collectList().map(Result::ok);
    }

    @PostMapping
    public Mono<Result<SupportTicketDetailVo>> createTicket(@CurrentUser PortalUserVo user,
                                                            @Valid @RequestBody SupportTicketCreateDto dto) {
        return supportTicketService.createTicket(user, dto).map(Result::ok);
    }

    @GetMapping("{ticketNo}")
    public Mono<Result<SupportTicketDetailVo>> getMyTicket(@CurrentUser PortalUserVo user,
                                                           @PathVariable String ticketNo) {
        return supportTicketService.getMyTicket(user, ticketNo).map(Result::ok);
    }

    @PostMapping("{ticketNo}/messages")
    public Mono<Result<SupportTicketDetailVo>> addUserMessage(@CurrentUser PortalUserVo user,
                                                              @PathVariable String ticketNo,
                                                              @Valid @RequestBody SupportTicketMessageDto dto) {
        return supportTicketService.addUserMessage(user, ticketNo, dto).map(Result::ok);
    }

    @GetMapping("managements")
    public Mono<Result<List<SupportTicketVo>>> getManagementTickets(@CurrentUser PortalUserVo user,
                                                                    @RequestParam(required = false) String status) {
        return supportTicketService.getManagementTickets(user, status).collectList().map(Result::ok);
    }

    @GetMapping("managements/{ticketNo}")
    public Mono<Result<SupportTicketDetailVo>> getManagementTicket(@CurrentUser PortalUserVo user,
                                                                   @PathVariable String ticketNo) {
        return supportTicketService.getManagementTicket(user, ticketNo).map(Result::ok);
    }

    @PostMapping("managements/{ticketNo}/messages")
    public Mono<Result<SupportTicketDetailVo>> addManagementMessage(@CurrentUser PortalUserVo user,
                                                                    @PathVariable String ticketNo,
                                                                    @Valid @RequestBody SupportTicketMessageDto dto) {
        return supportTicketService.addManagementMessage(user, ticketNo, dto).map(Result::ok);
    }

    @PostMapping("managements/{ticketNo}/resolve")
    public Mono<Result<SupportTicketDetailVo>> resolveTicket(@CurrentUser PortalUserVo user,
                                                             @PathVariable String ticketNo) {
        return supportTicketService.resolveTicket(user, ticketNo).map(Result::ok);
    }
}

package group.flyfish.dev.support.service.impl;

import group.flyfish.dev.customer.service.CustomerRealtimeNotifier;
import group.flyfish.dev.support.domain.dto.SupportTicketCreateDto;
import group.flyfish.dev.support.domain.dto.SupportTicketMessageDto;
import group.flyfish.dev.support.domain.po.SupportTicket;
import group.flyfish.dev.support.domain.po.SupportTicketMessage;
import group.flyfish.dev.support.notification.SupportTicketNotificationService;
import group.flyfish.dev.support.repository.SupportTicketMessageRepository;
import group.flyfish.dev.support.repository.SupportTicketRepository;
import group.flyfish.dev.user.domain.OAuthType;
import group.flyfish.dev.user.domain.po.PortalUser;
import group.flyfish.dev.user.domain.po.PortalUserOauth;
import group.flyfish.dev.user.domain.vo.PortalUserOauthVo;
import group.flyfish.dev.user.domain.vo.PortalUserVo;
import group.flyfish.dev.user.repository.PortalUserRepository;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupportTicketServiceImplTest {

    @Test
    void createsUserTicketWithInitialMessage() {
        Fixture fixture = new Fixture();
        SupportTicketCreateDto dto = new SupportTicketCreateDto();
        dto.setTitle("支付后仓库未开通");
        dto.setCategory("DELIVERY");
        dto.setPriority("HIGH");
        dto.setContact("customer@example.com");
        dto.setContent("GitHub 私有仓库没有协作权限");

        StepVerifier.create(fixture.service.createTicket(fixture.customer(), dto))
                .assertNext(detail -> {
                    assertNotNull(detail.getTicketNo());
                    assertEquals("支付后仓库未开通", detail.getTitle());
                    assertEquals("HIGH", detail.getPriority());
                    assertEquals("OPEN", detail.getStatus());
                    assertEquals(1, detail.getAdminUnreadCount());
                    assertEquals(0, detail.getUserUnreadCount());
                    assertEquals(1, detail.getMessages().size());
                    assertEquals("USER", detail.getMessages().get(0).getSenderRole());
                })
                .verifyComplete();
        verify(fixture.notificationService).ticketCreated(any(SupportTicket.class), any(SupportTicketMessage.class));
    }

    @Test
    void adminReplyMovesTicketToWaitingUserAndResolveClosesIt() {
        Fixture fixture = new Fixture();
        SupportTicket ticket = fixture.seedTicket();
        SupportTicketMessageDto reply = new SupportTicketMessageDto();
        reply.setContent("已重新开通，请确认。");

        StepVerifier.create(fixture.service.addManagementMessage(fixture.maintainer(), ticket.getTicketNo(), reply))
                .assertNext(detail -> {
                    assertEquals("WAITING_USER", detail.getStatus());
                    assertEquals("已重新开通，请确认。", detail.getLastMessage());
                    assertEquals(1, detail.getUserUnreadCount());
                    assertEquals("ADMIN", detail.getMessages().get(detail.getMessages().size() - 1).getSenderRole());
                })
                .verifyComplete();
        verify(fixture.notificationService).adminReplied(any(SupportTicket.class), any(SupportTicketMessage.class));

        StepVerifier.create(fixture.service.resolveTicket(fixture.maintainer(), ticket.getTicketNo()))
                .assertNext(detail -> {
                    assertEquals("RESOLVED", detail.getStatus());
                    assertEquals(1L, detail.getResolvedBy());
                    assertNotNull(detail.getResolvedTime());
                    assertEquals("问题已标记解决", detail.getLastMessage());
                    assertEquals(2, detail.getUserUnreadCount());
                })
                .verifyComplete();
        verify(fixture.notificationService).ticketResolved(any(SupportTicket.class), any(SupportTicketMessage.class));
    }

    @Test
    void countsUnreadTicketsByCurrentRoleAndClearsWhenDetailOpened() {
        Fixture fixture = new Fixture();
        SupportTicket ticket = fixture.seedTicket();
        ticket.setAdminUnreadCount(2);
        ticket.setUserUnreadCount(3);
        when(fixture.ticketRepository.findAllUnreadForManagement()).thenReturn(Flux.just(ticket));
        when(fixture.ticketRepository.findAllUnreadByCreatorId(100L)).thenReturn(Flux.just(ticket));

        StepVerifier.create(fixture.service.countUnreadTickets(fixture.maintainer()))
                .expectNext(2L)
                .verifyComplete();
        StepVerifier.create(fixture.service.getUnreadTickets(fixture.customer(), 5))
                .assertNext(vo -> {
                    assertEquals(ticket.getTicketNo(), vo.getTicketNo());
                    assertEquals(3, vo.getUnreadCount());
                })
                .verifyComplete();
        StepVerifier.create(fixture.service.getManagementTicket(fixture.maintainer(), ticket.getTicketNo()))
                .assertNext(detail -> assertEquals(0, detail.getAdminUnreadCount()))
                .verifyComplete();
    }

    private static class Fixture {

        private final SupportTicketRepository ticketRepository = mock(SupportTicketRepository.class);
        private final SupportTicketMessageRepository messageRepository = mock(SupportTicketMessageRepository.class);
        private final PortalUserRepository portalUserRepository = mock(PortalUserRepository.class);
        private final SupportTicketNotificationService notificationService = mock(SupportTicketNotificationService.class);
        private final CustomerRealtimeNotifier realtimeNotifier = mock(CustomerRealtimeNotifier.class);
        private final SupportTicketServiceImpl service = new SupportTicketServiceImpl(ticketRepository,
                messageRepository, portalUserRepository, notificationService, realtimeNotifier);
        private final AtomicLong ticketIds = new AtomicLong(1);
        private final AtomicLong messageIds = new AtomicLong(1);
        private final List<SupportTicketMessage> messages = new ArrayList<>();
        private SupportTicket currentTicket;

        private Fixture() {
            PortalUser customer = new PortalUser();
            customer.setId(100L);
            customer.setUsername("客户王");
            customer.setEmail("customer@example.com");
            when(portalUserRepository.findById(100L)).thenReturn(Mono.just(customer));
            when(portalUserRepository.findById(1L)).thenReturn(Mono.empty());
            when(notificationService.ticketCreated(any(), any())).thenReturn(Mono.empty());
            when(notificationService.userReplied(any(), any())).thenReturn(Mono.empty());
            when(notificationService.adminReplied(any(), any())).thenReturn(Mono.empty());
            when(notificationService.ticketResolved(any(), any())).thenReturn(Mono.empty());
            when(ticketRepository.save(any(SupportTicket.class))).thenAnswer(invocation -> {
                SupportTicket ticket = invocation.getArgument(0);
                if (ticket.getId() == null) {
                    ticket.setId(ticketIds.getAndIncrement());
                    ticket.setCreateTime(LocalDateTime.now());
                }
                ticket.setUpdateTime(LocalDateTime.now());
                currentTicket = ticket;
                when(ticketRepository.findByTicketNo(ticket.getTicketNo())).thenReturn(Mono.just(ticket));
                return Mono.just(ticket);
            });
            when(messageRepository.save(any(SupportTicketMessage.class))).thenAnswer(invocation -> {
                SupportTicketMessage message = invocation.getArgument(0);
                message.setId(messageIds.getAndIncrement());
                message.setCreateTime(LocalDateTime.now());
                messages.add(message);
                return Mono.just(message);
            });
            when(messageRepository.findAllByTicketIdOrderByCreateTimeAsc(any())).thenAnswer(invocation -> {
                Long ticketId = invocation.getArgument(0);
                return Flux.fromIterable(messages.stream()
                        .filter(message -> ticketId.equals(message.getTicketId()))
                        .toList());
            });
            when(ticketRepository.clearAdminUnread(any())).thenAnswer(invocation -> {
                Long ticketId = invocation.getArgument(0);
                if (currentTicket != null && ticketId.equals(currentTicket.getId())) {
                    currentTicket.setAdminUnreadCount(0);
                }
                return Mono.just(1);
            });
            when(ticketRepository.clearUserUnread(any())).thenAnswer(invocation -> {
                Long ticketId = invocation.getArgument(0);
                if (currentTicket != null && ticketId.equals(currentTicket.getId())) {
                    currentTicket.setUserUnreadCount(0);
                }
                return Mono.just(1);
            });
        }

        private SupportTicket seedTicket() {
            SupportTicket ticket = new SupportTicket();
            ticket.setId(1L);
            ticket.setTicketNo("TK1001");
            ticket.setCreatorId(100L);
            ticket.setTitle("GitHub 仓库权限问题");
            ticket.setCategory("DELIVERY");
            ticket.setPriority(SupportTicket.Priority.HIGH.name());
            ticket.setStatus(SupportTicket.Status.OPEN.name());
            ticket.setContact("customer@example.com");
            ticket.setLastMessage("仓库没有权限");
            ticket.setAdminUnreadCount(1);
            ticket.setUserUnreadCount(0);
            ticket.setCreateTime(LocalDateTime.now());
            ticket.setUpdateTime(LocalDateTime.now());
            currentTicket = ticket;
            when(ticketRepository.findByTicketNo(eq("TK1001"))).thenReturn(Mono.just(currentTicket));

            SupportTicketMessage message = new SupportTicketMessage();
            message.setId(messageIds.getAndIncrement());
            message.setTicketId(1L);
            message.setSenderId(100L);
            message.setSenderRole(SupportTicketMessage.SenderRole.USER.name());
            message.setContent("仓库没有权限");
            message.setCreateTime(LocalDateTime.now());
            messages.add(message);
            return ticket;
        }

        private PortalUserVo customer() {
            PortalUserVo user = new PortalUserVo();
            user.setId(100L);
            user.setUsername("客户王");
            user.setEmail("customer@example.com");
            return user;
        }

        private PortalUserVo maintainer() {
            PortalUserOauth oauth = new PortalUserOauth();
            oauth.setType(OAuthType.GITEA);
            oauth.setOpenid("1");
            oauth.setUserInfo("{\"id\":\"1\",\"login\":\"wybaby168\"}");
            PortalUserVo user = new PortalUserVo();
            user.setId(1L);
            user.setUsername("wybaby168");
            user.setAuthorizations(Map.of("gitea", new PortalUserOauthVo(oauth)));
            return user;
        }
    }
}

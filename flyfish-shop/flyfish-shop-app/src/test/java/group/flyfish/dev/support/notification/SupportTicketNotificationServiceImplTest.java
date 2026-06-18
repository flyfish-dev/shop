package group.flyfish.dev.support.notification;

import group.flyfish.dev.support.domain.po.SupportTicket;
import group.flyfish.dev.support.domain.po.SupportTicketMessage;
import group.flyfish.dev.auth.api.client.AuthUserGateway;
import group.flyfish.dev.auth.api.user.OAuthType;
import group.flyfish.dev.auth.api.user.PortalUserOauthVo;
import group.flyfish.dev.auth.api.user.PortalUserVo;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Collection;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupportTicketNotificationServiceImplTest {

    @Test
    void ticketCreatedNotifiesUserAndMaintainerByAvailableChannels() {
        Fixture fixture = new Fixture();
        fixture.seedCustomer(true, true);
        fixture.seedMaintainer(true, true);

        StepVerifier.create(fixture.service.ticketCreated(fixture.ticket(), fixture.message()))
                .verifyComplete();

        verify(fixture.mailNotifier).send(argThat(recipients -> contains(recipients, "customer@example.com")),
                any(SupportTicketNotificationMessage.class));
        verify(fixture.mailNotifier).send(argThat(recipients -> contains(recipients, "admin@example.com")),
                any(SupportTicketNotificationMessage.class));
        verify(fixture.wechatNotifier).send(argThat(recipients -> contains(recipients, "wechat-customer-openid")),
                any(SupportTicketNotificationMessage.class));
        verify(fixture.wechatNotifier).send(argThat(recipients -> contains(recipients, "wechat-admin-openid")),
                any(SupportTicketNotificationMessage.class));
    }

    @Test
    void adminReplySkipsUserChannelsWhenUserHasNoEmailAndNoWechatBinding() {
        Fixture fixture = new Fixture();
        fixture.seedCustomer(false, false);

        StepVerifier.create(fixture.service.adminReplied(fixture.ticket(), fixture.message()))
                .verifyComplete();

        verify(fixture.mailNotifier).send(argThat(Collection::isEmpty),
                any(SupportTicketNotificationMessage.class));
        verify(fixture.wechatNotifier).send(argThat(Collection::isEmpty),
                any(SupportTicketNotificationMessage.class));
        verify(fixture.mailNotifier, never()).send(argThat(recipients -> contains(recipients, "customer@example.com")),
                any(SupportTicketNotificationMessage.class));
        verify(fixture.wechatNotifier, never()).send(argThat(recipients -> contains(recipients, "wechat-customer-openid")),
                any(SupportTicketNotificationMessage.class));
    }

    @Test
    void ticketCreatedToleratesMaintainerWithoutEmail() {
        Fixture fixture = new Fixture();
        fixture.seedCustomer(false, false);
        fixture.seedMaintainer(false, false);

        StepVerifier.create(fixture.service.ticketCreated(fixture.ticket(), fixture.message()))
                .verifyComplete();

        verify(fixture.mailNotifier, atLeastOnce()).send(argThat(Collection::isEmpty),
                any(SupportTicketNotificationMessage.class));
    }

    private static boolean contains(Collection<String> values, String expected) {
        return values != null && values.contains(expected);
    }

    private static class Fixture {

        private final AuthUserGateway authUserGateway = mock(AuthUserGateway.class);
        private final SupportNotificationProperties properties = new SupportNotificationProperties();
        private final SupportTicketNotificationMessageFactory messageFactory =
                new SupportTicketNotificationMessageFactory(properties);
        private final SupportTicketMailNotifier mailNotifier = mock(SupportTicketMailNotifier.class);
        private final SupportTicketWechatNotifier wechatNotifier = mock(SupportTicketWechatNotifier.class);
        private final SupportTicketNotificationServiceImpl service = new SupportTicketNotificationServiceImpl(
                authUserGateway, properties, messageFactory, mailNotifier, wechatNotifier);

        private Fixture() {
            when(mailNotifier.normalizeEmails(any())).thenAnswer(invocation -> invocation.<Collection<String>>getArgument(0)
                    .stream().filter(value -> value != null && value.contains("@")).distinct().toList());
            when(wechatNotifier.normalizeOpenids(any())).thenAnswer(invocation -> invocation.<Collection<String>>getArgument(0)
                    .stream().filter(value -> value != null && !value.isBlank()).distinct().toList());
            when(mailNotifier.send(any(), any())).thenReturn(Mono.empty());
            when(wechatNotifier.send(any(), any())).thenReturn(Mono.empty());
            when(authUserGateway.authorizationByOpenid(OAuthType.GITEA, "1")).thenReturn(Mono.empty());
        }

        private void seedCustomer(boolean hasEmail, boolean hasWechat) {
            PortalUserVo customer = new PortalUserVo();
            customer.setId(100L);
            customer.setUsername("客户王");
            if (hasEmail) {
                customer.setEmail("customer@example.com");
            }
            when(authUserGateway.getById(100L)).thenReturn(Mono.just(customer));
            when(authUserGateway.authorizationsByUser(100L, OAuthType.WECHAT))
                    .thenReturn(hasWechat
                            ? Flux.just(oauth(100L, OAuthType.WECHAT, "wechat-customer-openid"))
                            : Flux.empty());
        }

        private void seedMaintainer(boolean hasEmail, boolean hasWechat) {
            when(authUserGateway.authorizationByOpenid(OAuthType.GITEA, "1"))
                    .thenReturn(Mono.just(oauth(1L, OAuthType.GITEA, "1")));
            PortalUserVo admin = new PortalUserVo();
            admin.setId(1L);
            admin.setUsername("wybaby168");
            if (hasEmail) {
                admin.setEmail("admin@example.com");
            }
            when(authUserGateway.getById(1L)).thenReturn(Mono.just(admin));
            when(authUserGateway.authorizationsByUser(1L, OAuthType.WECHAT))
                    .thenReturn(hasWechat
                            ? Flux.just(oauth(1L, OAuthType.WECHAT, "wechat-admin-openid"))
                            : Flux.empty());
        }

        private SupportTicket ticket() {
            SupportTicket ticket = new SupportTicket();
            ticket.setId(10L);
            ticket.setTicketNo("TK1001");
            ticket.setCreatorId(100L);
            ticket.setTitle("仓库权限未开通");
            ticket.setCategory("DELIVERY");
            ticket.setPriority("HIGH");
            ticket.setStatus("OPEN");
            ticket.setContact("customer@example.com");
            return ticket;
        }

        private SupportTicketMessage message() {
            SupportTicketMessage message = new SupportTicketMessage();
            message.setTicketId(10L);
            message.setSenderId(100L);
            message.setSenderRole("USER");
            message.setContent("我支付后还没有仓库权限");
            return message;
        }

        private PortalUserOauthVo oauth(Long userId, OAuthType type, String openid) {
            return PortalUserOauthVo.of(userId, type, openid, null, null, null, null,
                    null, null, null, null, null);
        }
    }
}

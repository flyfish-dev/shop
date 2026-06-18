package group.flyfish.dev.customer.service.impl;

import group.flyfish.dev.customer.domain.dto.CustomerMessageSendDto;
import group.flyfish.dev.customer.domain.po.CustomerConversation;
import group.flyfish.dev.customer.domain.po.CustomerMessage;
import group.flyfish.dev.customer.repository.CustomerConversationRepository;
import group.flyfish.dev.customer.repository.CustomerMessageRepository;
import group.flyfish.dev.customer.service.CustomerRealtimeNotifier;
import group.flyfish.dev.support.service.SupportTicketService;
import group.flyfish.dev.auth.api.client.AuthUserGateway;
import group.flyfish.dev.auth.api.user.OAuthType;
import group.flyfish.dev.auth.api.user.PortalUserOauthVo;
import group.flyfish.dev.auth.api.user.PortalUserVo;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Collection;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomerServiceCenterServiceImplTest {

    @Test
    void managerReplyPersistsBrowserMessageAndUnreadCount() {
        Fixture fixture = new Fixture();
        fixture.seedConversation();
        CustomerMessageSendDto dto = new CustomerMessageSendDto();
        dto.setContent("已收到，我来帮你确认。");
        dto.setRelatedType("ORDER");
        dto.setRelatedNo("FO202606050001");

        StepVerifier.create(fixture.service.sendManagementMessage(fixture.maintainer(), 1L, dto))
                .assertNext(detail -> {
                    assertEquals(1L, detail.getConversation().getId());
                    assertEquals(1, detail.getMessages().size());
                    assertEquals("wybaby168", detail.getMessages().get(0).getSenderName());
                    CustomerMessage message = fixture.messages.get(0);
                    assertEquals(CustomerMessage.Direction.OUTBOUND.name(), message.getDirection());
                    assertEquals(CustomerMessage.Channel.WEB.name(), message.getChannel());
                    assertEquals(CustomerMessage.SendStatus.SENT.name(), message.getSendStatus());
                    assertEquals("ORDER", message.getRelatedType());
                    assertEquals("FO202606050001", message.getRelatedNo());
                    assertEquals("admin:1", message.getCreateBy());
                })
                .verifyComplete();

        assertEquals(1, fixture.conversations.get(0).getUserUnreadCount());
    }

    @Test
    void browserUserWithoutWechatCanCreateConversation() {
        Fixture fixture = new Fixture();
        CustomerMessageSendDto dto = new CustomerMessageSendDto();
        dto.setContent("浏览器里咨询一下");

        StepVerifier.create(fixture.service.sendMyMessage(fixture.browserUser(), dto))
                .assertNext(detail -> {
                    assertNotNull(detail.getConversation().getId());
                    assertEquals(101L, detail.getConversation().getUserId());
                    assertEquals("浏览器用户", detail.getConversation().getDisplayName());
                    assertEquals(1, detail.getMessages().size());
                    assertEquals("浏览器用户", detail.getMessages().get(0).getSenderName());
                    CustomerMessage message = fixture.messages.get(0);
                    assertEquals(CustomerMessage.Direction.INBOUND.name(), message.getDirection());
                    assertEquals(CustomerMessage.Channel.WEB.name(), message.getChannel());
                    assertEquals(CustomerMessage.SendStatus.RECEIVED.name(), message.getSendStatus());
                    assertEquals("user:101", message.getCreateBy());
                })
                .verifyComplete();

        assertEquals("web:user:101", fixture.conversations.get(0).getWechatOpenid());
        assertEquals(1, fixture.conversations.get(0).getAdminUnreadCount());
    }

    @Test
    void browserUserRestoresSoftDeletedWebConversation() {
        Fixture fixture = new Fixture();
        CustomerConversation hidden = fixture.seedSoftDeletedWebConversation(101L);

        StepVerifier.create(fixture.service.getMyConversation(fixture.browserUser()))
                .assertNext(detail -> {
                    assertEquals(hidden.getId(), detail.getConversation().getId());
                    assertEquals(101L, detail.getConversation().getUserId());
                    assertEquals("浏览器用户", detail.getConversation().getDisplayName());
                })
                .verifyComplete();

        assertEquals(1, fixture.conversations.size());
        assertEquals(Boolean.FALSE, fixture.conversations.get(0).getDelete());
        assertEquals(CustomerConversation.Status.OPEN.name(), fixture.conversations.get(0).getStatus());
    }

    @Test
    void createWechatConversationRecoversWhenOpenidWasInsertedConcurrently() {
        Fixture fixture = new Fixture();
        AtomicBoolean firstInsert = new AtomicBoolean(true);
        when(fixture.conversationRepository.save(any(CustomerConversation.class))).thenAnswer(invocation -> {
            CustomerConversation conversation = invocation.getArgument(0);
            if (conversation.getId() == null
                    && "wechat-openid".equals(conversation.getWechatOpenid())
                    && firstInsert.getAndSet(false)) {
                CustomerConversation concurrentlyInserted = new CustomerConversation();
                concurrentlyInserted.setId(99L);
                concurrentlyInserted.setWechatOpenid("wechat-openid");
                concurrentlyInserted.setStatus(CustomerConversation.Status.OPEN.name());
                concurrentlyInserted.setAdminUnreadCount(0);
                concurrentlyInserted.setUserUnreadCount(0);
                concurrentlyInserted.setCreateTime(LocalDateTime.now());
                concurrentlyInserted.setUpdateTime(LocalDateTime.now());
                fixture.conversations.add(concurrentlyInserted);
                return Mono.error(new DuplicateKeyException(
                        "Duplicate entry 'wechat-openid' for key 'wechat_openid'"));
            }
            return fixture.saveConversation(conversation);
        });
        CustomerMessageSendDto dto = new CustomerMessageSendDto();
        dto.setContent("并发创建后继续发送");

        StepVerifier.create(fixture.service.sendMyMessage(fixture.wechatUser(), dto))
                .assertNext(detail -> {
                    assertEquals(99L, detail.getConversation().getId());
                    assertEquals(100L, detail.getConversation().getUserId());
                    assertEquals("微信客户", detail.getConversation().getDisplayName());
                    assertEquals("https://example.com/avatar.jpg", detail.getConversation().getAvatar());
                    assertEquals(1, detail.getMessages().size());
                })
                .verifyComplete();

        long conversationCount = fixture.conversations.stream()
                .filter(conversation -> "wechat-openid".equals(conversation.getWechatOpenid()))
                .count();
        assertEquals(1, conversationCount);
        assertEquals(1, fixture.conversations.get(0).getAdminUnreadCount());
    }

    @Test
    void customerMessageUsesPortalUserAvatarWhenConversationAvatarIsStale() {
        Fixture fixture = new Fixture();
        fixture.seedConversation();
        fixture.conversations.get(0).setAvatar("https://example.com/old-wechat-avatar.jpg");
        fixture.seedInboundMessage("客户发来的消息");

        StepVerifier.create(fixture.service.getManagementConversation(fixture.maintainer(), 1L))
                .assertNext(detail -> {
                    assertEquals("https://example.com/avatar.jpg", detail.getConversation().getAvatar());
                    assertEquals("https://example.com/avatar.jpg", detail.getMessages().get(0).getSenderAvatar());
                })
                .verifyComplete();
    }

    @Test
    void summaryAddsTicketUnreadCountToBellTotal() {
        Fixture fixture = new Fixture();
        fixture.seedConversation();
        fixture.conversations.get(0).setAdminUnreadCount(2);
        when(fixture.supportTicketService.countUnreadTickets(any())).thenReturn(Mono.just(3L));

        StepVerifier.create(fixture.service.summary(fixture.maintainer()))
                .assertNext(summary -> {
                    assertEquals(5L, summary.getUnreadCount());
                    assertEquals(2L, summary.getCustomerMessageUnreadCount());
                    assertEquals(3L, summary.getTicketUnreadCount());
                })
                .verifyComplete();
    }

    private static class Fixture {

        private final CustomerConversationRepository conversationRepository = mock(CustomerConversationRepository.class);
        private final CustomerMessageRepository messageRepository = mock(CustomerMessageRepository.class);
        private final AuthUserGateway authUserGateway = mock(AuthUserGateway.class);
        private final SupportTicketService supportTicketService = mock(SupportTicketService.class);
        private final CustomerRealtimeNotifier realtimeNotifier = mock(CustomerRealtimeNotifier.class);
        private final CustomerServiceCenterServiceImpl service = new CustomerServiceCenterServiceImpl(
                conversationRepository, messageRepository, authUserGateway, supportTicketService,
                realtimeNotifier);
        private final AtomicLong conversationIds = new AtomicLong(1);
        private final AtomicLong messageIds = new AtomicLong(1);
        private final List<CustomerConversation> conversations = new ArrayList<>();
        private final List<CustomerMessage> messages = new ArrayList<>();
        private final PortalUserOauthVo wechatOauth = wechatOauth();

        private Fixture() {
            when(authUserGateway.authorizationByOpenid(eq(OAuthType.WECHAT), eq("wechat-openid")))
                    .thenReturn(Mono.just(wechatOauth));
            when(authUserGateway.authorizationsByUser(eq(100L), eq(OAuthType.WECHAT)))
                    .thenReturn(Flux.just(wechatOauth));
            when(authUserGateway.authorizationsByUser(eq(1L), eq(OAuthType.WECHAT)))
                    .thenReturn(Flux.empty());
            when(authUserGateway.authorizationsByUser(eq(101L), eq(OAuthType.WECHAT)))
                    .thenReturn(Flux.empty());
            when(authUserGateway.getById(eq(100L))).thenReturn(Mono.just(wechatUser()));
            when(authUserGateway.getById(eq(101L))).thenReturn(Mono.just(browserUser()));
            when(authUserGateway.getById(eq(1L))).thenReturn(Mono.just(maintainer()));
            when(authUserGateway.findAllByIds(any())).thenAnswer(invocation -> {
                Collection<Long> ids = invocation.getArgument(0);
                List<PortalUserVo> users = new ArrayList<>();
                ids.forEach(id -> {
                    if (Long.valueOf(1L).equals(id)) {
                        users.add(maintainer());
                    }
                    if (Long.valueOf(101L).equals(id)) {
                        users.add(browserUser());
                    }
                });
                return Flux.fromIterable(users);
            });
            when(conversationRepository.findByWechatOpenid(anyString())).thenAnswer(invocation -> {
                String openid = invocation.getArgument(0);
                return Flux.fromIterable(conversations)
                        .filter(this::active)
                        .filter(conversation -> openid.equals(conversation.getWechatOpenid()))
                        .next();
            });
            when(conversationRepository.findByWechatOpenidIncludingDeleted(anyString())).thenAnswer(invocation -> {
                String openid = invocation.getArgument(0);
                return Flux.fromIterable(conversations)
                        .filter(conversation -> openid.equals(conversation.getWechatOpenid()))
                        .next();
            });
            when(conversationRepository.findById(anyLong())).thenAnswer(invocation -> {
                Long id = invocation.getArgument(0);
                return Flux.fromIterable(conversations)
                        .filter(this::active)
                        .filter(conversation -> id.equals(conversation.getId()))
                        .next();
            });
            when(conversationRepository.findAllByUserId(anyLong())).thenAnswer(invocation -> {
                Long userId = invocation.getArgument(0);
                return Flux.defer(() -> Flux.fromIterable(conversations)
                        .filter(this::active)
                        .filter(conversation -> userId.equals(conversation.getUserId())));
            });
            when(conversationRepository.findAllForManagement())
                    .thenReturn(Flux.defer(() -> Flux.fromIterable(conversations).filter(this::active)));
            when(conversationRepository.save(any(CustomerConversation.class))).thenAnswer(invocation -> {
                CustomerConversation conversation = invocation.getArgument(0);
                return saveConversation(conversation);
            });
            when(messageRepository.save(any(CustomerMessage.class))).thenAnswer(invocation -> {
                CustomerMessage message = invocation.getArgument(0);
                if (message.getId() == null) {
                    message.setId(messageIds.getAndIncrement());
                    message.setCreateTime(LocalDateTime.now());
                    messages.add(message);
                }
                message.setUpdateTime(LocalDateTime.now());
                return Mono.just(message);
            });
            when(messageRepository.findAllByConversationId(anyLong())).thenAnswer(invocation -> {
                Long conversationId = invocation.getArgument(0);
                return Flux.defer(() -> Flux.fromIterable(messages.stream()
                        .filter(message -> conversationId.equals(message.getConversationId()))
                        .sorted(Comparator.comparing(CustomerMessage::getCreateTime)
                                .thenComparing(CustomerMessage::getId))
                        .toList()));
            });
            when(messageRepository.markAdminRead(anyLong())).thenReturn(Mono.just(1));
            when(messageRepository.markUserRead(anyLong())).thenReturn(Mono.just(1));
            when(supportTicketService.countUnreadTickets(any())).thenReturn(Mono.just(0L));
            when(supportTicketService.getUnreadTickets(any(), anyInt())).thenReturn(Flux.empty());
        }

        private void seedConversation() {
            CustomerConversation conversation = new CustomerConversation();
            conversation.setId(1L);
            conversation.setUserId(100L);
            conversation.setWechatOpenid("wechat-openid");
            conversation.setDisplayName("微信客户");
            conversation.setStatus(CustomerConversation.Status.OPEN.name());
            conversation.setAdminUnreadCount(0);
            conversation.setUserUnreadCount(0);
            conversation.setCreateTime(LocalDateTime.now());
            conversation.setUpdateTime(LocalDateTime.now());
            conversations.add(conversation);
        }

        private CustomerConversation seedSoftDeletedWebConversation(Long userId) {
            CustomerConversation conversation = new CustomerConversation();
            conversation.setId(conversationIds.getAndIncrement());
            conversation.setUserId(userId);
            conversation.setWechatOpenid("web:user:" + userId);
            conversation.setDisplayName("用户 " + userId);
            conversation.setStatus(CustomerConversation.Status.OPEN.name());
            conversation.setAdminUnreadCount(0);
            conversation.setUserUnreadCount(0);
            conversation.setDelete(true);
            conversation.setCreateTime(LocalDateTime.now());
            conversation.setUpdateTime(LocalDateTime.now());
            conversations.add(conversation);
            return conversation;
        }

        private void seedInboundMessage(String content) {
            CustomerMessage message = new CustomerMessage();
            message.setId(messageIds.getAndIncrement());
            message.setConversationId(1L);
            message.setUserId(100L);
            message.setWechatOpenid("wechat-openid");
            message.setDirection(CustomerMessage.Direction.INBOUND.name());
            message.setChannel(CustomerMessage.Channel.WECHAT.name());
            message.setSenderRole(CustomerMessage.SenderRole.USER.name());
            message.setMessageType("text");
            message.setContent(content);
            message.setSendStatus(CustomerMessage.SendStatus.RECEIVED.name());
            message.setCreateTime(LocalDateTime.now());
            message.setUpdateTime(LocalDateTime.now());
            messages.add(message);
        }

        private Mono<CustomerConversation> saveConversation(CustomerConversation conversation) {
            if (conversation.getId() == null) {
                boolean duplicated = conversations.stream()
                        .anyMatch(existing -> conversation.getWechatOpenid().equals(existing.getWechatOpenid()));
                if (duplicated) {
                    return Mono.error(new DuplicateKeyException(
                            "Duplicate entry '%s' for key 'wechat_openid'".formatted(conversation.getWechatOpenid())));
                }
                conversation.setId(conversationIds.getAndIncrement());
                conversation.setCreateTime(LocalDateTime.now());
                conversations.add(conversation);
            }
            conversation.setUpdateTime(LocalDateTime.now());
            return Mono.just(conversation);
        }

        private boolean active(CustomerConversation conversation) {
            return !Boolean.TRUE.equals(conversation.getDelete());
        }

        private PortalUserVo maintainer() {
            PortalUserVo user = new PortalUserVo();
            user.setId(1L);
            user.setUsername("wybaby168");
            user.setAuthorizations(Map.of("gitea", oauth(1L, OAuthType.GITEA, "1",
                    "{\"id\":\"1\",\"login\":\"wybaby168\"}", "wybaby168", "wybaby168", null)));
            return user;
        }

        private PortalUserVo browserUser() {
            PortalUserVo user = new PortalUserVo();
            user.setId(101L);
            user.setUsername("浏览器用户");
            user.setAuthorizations(Map.of());
            return user;
        }

        private PortalUserVo wechatUser() {
            PortalUserVo user = new PortalUserVo();
            user.setId(100L);
            user.setUsername("微信客户");
            user.setAvatar("https://example.com/avatar.jpg");
            return user;
        }

        private PortalUserOauthVo wechatOauth() {
            return oauth(100L, OAuthType.WECHAT, "wechat-openid",
                    "{\"nickname\":\"微信客户\",\"headimgurl\":\"https://example.com/avatar.jpg\"}",
                    null, "微信客户", "https://example.com/avatar.jpg");
        }

        private PortalUserOauthVo oauth(Long userId, OAuthType type, String openid, String userInfo,
                                        String loginName, String displayName, String avatarUrl) {
            return PortalUserOauthVo.of(userId, type, openid, userInfo, null, loginName, displayName,
                    displayName, avatarUrl, null, null, null);
        }
    }
}

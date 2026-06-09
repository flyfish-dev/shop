package group.flyfish.dev.customer.service.impl;

import group.flyfish.dev.customer.domain.po.CustomerWechatActivity;
import group.flyfish.dev.customer.repository.CustomerWechatActivityRepository;
import group.flyfish.dev.customer.service.CustomerRealtimeNotifier;
import group.flyfish.dev.shop.wechat.protocol.WechatInboundMessage;
import group.flyfish.dev.user.domain.OAuthType;
import group.flyfish.dev.user.domain.po.PortalUserOauth;
import group.flyfish.dev.user.repository.PortalUserOauthRepository;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerWechatActivityServiceImplTest {

    @Test
    void recordsWechatInboundMessageWithBoundUserSnapshot() {
        Fixture fixture = new Fixture();

        StepVerifier.create(fixture.service.recordInbound(WechatInboundMessage.builder()
                        .msgType(WechatInboundMessage.MSG_TYPE_TEXT)
                        .fromUserName("wechat-openid")
                        .toUserName("gh-test")
                        .content("我想咨询订单")
                        .msgId("10001")
                        .build(), "<xml>raw</xml>"))
                .assertNext(activity -> {
                    assertEquals(1L, activity.getId());
                    assertEquals(100L, activity.getUserId());
                    assertEquals("wechat-openid", activity.getWechatOpenid());
                    assertEquals("union-100", activity.getWechatUnionId());
                    assertEquals("微信客户", activity.getDisplayName());
                    assertEquals("https://example.com/avatar.jpg", activity.getAvatar());
                    assertEquals(CustomerWechatActivity.ActivityType.TEXT.name(), activity.getActivityType());
                    assertEquals("text", activity.getMessageType());
                    assertEquals("发送文字消息", activity.getTitle());
                    assertEquals("我想咨询订单", activity.getContent());
                    assertEquals("10001", activity.getWechatMsgId());
                    assertEquals("<xml>raw</xml>", activity.getRawPayload());
                    assertEquals("wechat-activity", activity.getCreateBy());
                    assertEquals("wechat-activity", activity.getUpdateBy());
                    assertFalse(activity.getDelete());
                })
                .verifyComplete();

        verify(fixture.realtimeNotifier).wechatActivitiesChanged();
    }

    @Test
    void returnsExistingWechatActivityForDuplicateMessageId() {
        Fixture fixture = new Fixture();
        CustomerWechatActivity existing = new CustomerWechatActivity();
        existing.setId(9L);
        existing.setWechatMsgId("duplicate-msg");
        existing.setContent("已记录");
        when(fixture.activityRepository.findByWechatMsgId(eq("duplicate-msg")))
                .thenReturn(Mono.just(existing));

        StepVerifier.create(fixture.service.recordInbound(WechatInboundMessage.builder()
                        .msgType(WechatInboundMessage.MSG_TYPE_TEXT)
                        .fromUserName("wechat-openid")
                        .content("重复消息")
                        .msgId("duplicate-msg")
                        .build(), "<xml>duplicate</xml>"))
                .expectNext(existing)
                .verifyComplete();

        verify(fixture.activityRepository, never()).save(any(CustomerWechatActivity.class));
        verify(fixture.realtimeNotifier, never()).wechatActivitiesChanged();
    }

    private static class Fixture {

        private final CustomerWechatActivityRepository activityRepository = mock(CustomerWechatActivityRepository.class);
        private final PortalUserOauthRepository oauthRepository = mock(PortalUserOauthRepository.class);
        private final CustomerRealtimeNotifier realtimeNotifier = mock(CustomerRealtimeNotifier.class);
        private final CustomerWechatActivityServiceImpl service = new CustomerWechatActivityServiceImpl(
                activityRepository, oauthRepository, realtimeNotifier);
        private final AtomicLong activityIds = new AtomicLong(1);

        private Fixture() {
            when(activityRepository.findByWechatMsgId(any())).thenReturn(Mono.empty());
            when(oauthRepository.findAllByTypeAndOpenid(eq(OAuthType.WECHAT), eq("wechat-openid")))
                    .thenReturn(Flux.just(wechatOauth()));
            when(activityRepository.save(any(CustomerWechatActivity.class))).thenAnswer(invocation -> {
                CustomerWechatActivity activity = invocation.getArgument(0);
                if (activity.getId() == null) {
                    activity.setId(activityIds.getAndIncrement());
                    activity.setCreateTime(LocalDateTime.now());
                }
                activity.setUpdateTime(LocalDateTime.now());
                return Mono.just(activity);
            });
        }

        private PortalUserOauth wechatOauth() {
            PortalUserOauth oauth = new PortalUserOauth();
            oauth.setUserId(100L);
            oauth.setType(OAuthType.WECHAT);
            oauth.setOpenid("wechat-openid");
            oauth.setUnionId("union-100");
            oauth.setNickname("微信客户");
            oauth.setDisplayName("微信客户");
            oauth.setAvatarUrl("https://example.com/avatar.jpg");
            oauth.setUserInfo("{\"nickname\":\"微信客户\",\"headimgurl\":\"https://example.com/avatar.jpg\"}");
            return oauth;
        }
    }
}

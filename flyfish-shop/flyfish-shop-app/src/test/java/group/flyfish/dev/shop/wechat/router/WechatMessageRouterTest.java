package group.flyfish.dev.shop.wechat.router;

import group.flyfish.dev.auth.api.client.WechatLoginClient;
import group.flyfish.dev.shop.wechat.config.WechatQuickLoginProperties;
import group.flyfish.dev.shop.wechat.protocol.WechatInboundMessage;
import group.flyfish.dev.shop.wechat.protocol.WechatReplyMessage;
import group.flyfish.dev.shop.wechat.rule.ContactSupportRule;
import group.flyfish.dev.shop.wechat.rule.LoginCodeConfirmRule;
import group.flyfish.dev.shop.wechat.rule.PurchaseQuickLoginRule;
import group.flyfish.dev.shop.wechat.rule.WechatMessageRuleEngine;
import group.flyfish.dev.shop.wechat.rule.WechatMessageRuleResult;
import group.flyfish.dev.shop.wechat.service.WechatMpApiService;
import group.flyfish.dev.shop.wechat.service.impl.WechatServiceImpl;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WechatMessageRouterTest {

    @Test
    void purchaseKeywordRepliesWithQuickLoginTextLink() {
        WechatMessageRuleResult text = handleText(" 购 买 ");

        assertTrue(text.content().contains("<a href=\"https://shop.example.com/wx/quick-login/"));
        assertTrue(text.content().contains("redirect=%2Fshop%2Fitem-list"));
        assertTrue(text.content().contains("15分钟内有效"));
        assertTrue(text.content().contains("点击进入飞鱼小铺</a>"));
    }

    @Test
    void purchaseIntentTextRepliesWithQuickLoginTextLink() {
        WechatMessageRuleResult text = handleText("我想开通 office 全格式源码");

        assertTrue(text.content().contains("点击进入飞鱼小铺</a>"));
    }

    @Test
    void contactKeywordRepliesWithSupportWechat() {
        WechatMessageRuleResult text = handleText("想咨询一下怎么联系客服");

        assertTrue(text.content().contains("Yous_Gift"));
        assertEquals(WechatMessageRuleResult.ReplyAction.CUSTOMER_SUPPORT, text.action());
    }

    @Test
    void purchaseRuleHasHigherPriorityThanContactRule() {
        WechatMessageRuleResult text = handleText("想联系客服开通源码");

        assertTrue(text.content().contains("点击进入飞鱼小铺</a>"));
    }

    @Test
    void scanEventMarksLoginSessionAndRepliesCode() {
        FakeWechatLoginClient loginClient = new FakeWechatLoginClient();
        WechatServiceImpl wechatService = new WechatServiceImpl(loginClient);
        String scene = loginClient.newScene();
        WechatMpApiService mpApiService = mock(WechatMpApiService.class);
        WechatMessageRouter router = new WechatMessageRouter(
                new WechatMessageRuleEngine(List.of()), wechatService, mpApiService);

        WechatReplyMessage reply = router.route(WechatInboundMessage.builder()
                .msgType(WechatInboundMessage.MSG_TYPE_EVENT)
                .event(WechatInboundMessage.EVENT_SCAN)
                .eventKey("qrscene_login_" + scene)
                .fromUserName("wechat-openid")
                .toUserName("gh-test")
                .build()).block();

        assertNotNull(reply);
        assertEquals(WechatReplyMessage.Type.TEXT, reply.type());
        assertTrue(reply.content().contains(scene));
        assertTrue(loginClient.scanned(scene));
    }

    @Test
    void unmatchedMessageTransfersToOnlineCustomerService() {
        WechatMpApiService mpApiService = mock(WechatMpApiService.class);
        when(mpApiService.hasOnlineKefu()).thenReturn(Mono.just(true));
        WechatMessageRouter router = new WechatMessageRouter(
                new WechatMessageRuleEngine(List.of()), newWechatService(), mpApiService);

        WechatReplyMessage reply = router.route(WechatInboundMessage.builder()
                .msgType(WechatInboundMessage.MSG_TYPE_TEXT)
                .content("没有命中的内容")
                .fromUserName("wechat-openid")
                .toUserName("gh-test")
                .build()).block();

        assertNotNull(reply);
        assertEquals(WechatReplyMessage.Type.TRANSFER_CUSTOMER_SERVICE, reply.type());
    }

    @Test
    void contactKeywordRepliesWithCustomerSupportQrImage() {
        WechatMpApiService mpApiService = mock(WechatMpApiService.class);
        when(mpApiService.customerSupportImageMediaId()).thenReturn(Mono.just("support-media-id"));
        when(mpApiService.sendKefuText("wechat-openid",
                "如需详细了解产品、购买或交付问题，请添加客服微信号：Yous_Gift。\n"
                        + "添加时可备注“飞鱼小铺”，我们会尽快协助你处理。"))
                .thenReturn(Mono.empty());
        WechatMessageRouter router = new WechatMessageRouter(ruleEngine(), newWechatService(),
                mpApiService);

        WechatReplyMessage reply = router.route(WechatInboundMessage.builder()
                .msgType(WechatInboundMessage.MSG_TYPE_TEXT)
                .content("我想联系客服")
                .fromUserName("wechat-openid")
                .toUserName("gh-test")
                .build()).block();

        assertNotNull(reply);
        assertEquals(WechatReplyMessage.Type.IMAGE, reply.type());
        assertEquals("support-media-id", reply.content());
        verify(mpApiService).sendKefuText("wechat-openid",
                "如需详细了解产品、购买或交付问题，请添加客服微信号：Yous_Gift。\n"
                        + "添加时可备注“飞鱼小铺”，我们会尽快协助你处理。");
    }

    @Test
    void contactKeywordFallsBackToTextWhenQrImageUnavailable() {
        WechatMpApiService mpApiService = mock(WechatMpApiService.class);
        when(mpApiService.customerSupportImageMediaId())
                .thenReturn(Mono.error(new IllegalStateException("material not found")));
        WechatMessageRouter router = new WechatMessageRouter(ruleEngine(), newWechatService(),
                mpApiService);

        WechatReplyMessage reply = router.route(WechatInboundMessage.builder()
                .msgType(WechatInboundMessage.MSG_TYPE_TEXT)
                .content("客服")
                .fromUserName("wechat-openid")
                .toUserName("gh-test")
                .build()).block();

        assertNotNull(reply);
        assertEquals(WechatReplyMessage.Type.TEXT, reply.type());
        assertTrue(reply.content().contains("Yous_Gift"));
    }

    private WechatMessageRuleResult handleText(String content) {
        WechatQuickLoginProperties properties = new WechatQuickLoginProperties();
        properties.setBaseUrl("https://shop.example.com");
        properties.setPurchaseRedirect("/shop/item-list");
        properties.setExpireSeconds(900);
        WechatServiceImpl wechatService = newWechatService();
        WechatMessageRuleEngine ruleEngine = ruleEngine(wechatService, properties);
        WechatInboundMessage message = WechatInboundMessage.builder()
                .msgType(WechatInboundMessage.MSG_TYPE_TEXT)
                .content(content)
                .fromUserName("wechat-openid")
                .toUserName("gh-test")
                .build();

        return ruleEngine.handle(message).block(Duration.ofSeconds(2));
    }

    private WechatMessageRuleEngine ruleEngine() {
        WechatQuickLoginProperties properties = new WechatQuickLoginProperties();
        properties.setBaseUrl("https://shop.example.com");
        properties.setPurchaseRedirect("/shop/item-list");
        properties.setExpireSeconds(900);
        return ruleEngine(newWechatService(), properties);
    }

    private WechatMessageRuleEngine ruleEngine(WechatServiceImpl wechatService, WechatQuickLoginProperties properties) {
        return new WechatMessageRuleEngine(List.of(
                new PurchaseQuickLoginRule(wechatService, properties),
                new ContactSupportRule(),
                new LoginCodeConfirmRule(wechatService)
        ));
    }

    private WechatServiceImpl newWechatService() {
        return new WechatServiceImpl(new FakeWechatLoginClient());
    }

    private static class FakeWechatLoginClient implements WechatLoginClient {

        private final AtomicInteger ids = new AtomicInteger();

        private final Map<String, String> scannedOpenids = new ConcurrentHashMap<>();

        private final Map<String, String> confirmedOpenids = new ConcurrentHashMap<>();

        private String newScene() {
            String scene = "%06d".formatted(ids.incrementAndGet());
            confirmedOpenids.put(scene, "");
            return scene;
        }

        @Override
        public Mono<Boolean> exists(String scene) {
            return Mono.just(confirmedOpenids.containsKey(scene));
        }

        @Override
        public Mono<Void> scan(String scene, String openid) {
            scannedOpenids.put(scene, openid);
            return Mono.empty();
        }

        @Override
        public Mono<Void> confirm(String scene, String openid) {
            confirmedOpenids.put(scene, openid);
            return Mono.empty();
        }

        @Override
        public Mono<String> createConfirmed(String openid, int expireSeconds) {
            String scene = newScene();
            confirmedOpenids.put(scene, openid);
            return Mono.just(scene);
        }

        private boolean scanned(String scene) {
            return scannedOpenids.containsKey(scene);
        }
    }
}

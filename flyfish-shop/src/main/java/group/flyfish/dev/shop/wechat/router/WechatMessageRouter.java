package group.flyfish.dev.shop.wechat.router;

import group.flyfish.dev.shop.wechat.protocol.WechatInboundMessage;
import group.flyfish.dev.shop.wechat.protocol.WechatReplyMessage;
import group.flyfish.dev.shop.wechat.rule.WechatMessageRuleEngine;
import group.flyfish.dev.shop.wechat.rule.WechatMessageRuleResult;
import group.flyfish.dev.shop.wechat.service.WechatMpApiService;
import group.flyfish.dev.shop.wechat.service.WechatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 公众号消息业务路由。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WechatMessageRouter {

    private final WechatMessageRuleEngine ruleEngine;

    private final WechatService wechatService;

    private final WechatMpApiService wechatMpApiService;

    public Mono<WechatReplyMessage> route(WechatInboundMessage message) {
        if (message == null) {
            return Mono.empty();
        }
        log.info("接收到微信消息。msgType={}, event={}, openid={}",
                message.getMsgType(), message.getEvent(), message.getFromUserName());
        if (message.isEvent()) {
            return handleEvent(message);
        }
        if (message.isLocationMessage()) {
            return Mono.just(WechatReplyMessage.text(message, "感谢反馈，您的地理位置已收到！"));
        }
        return handleCommonMessage(message);
    }

    private Mono<WechatReplyMessage> handleEvent(WechatInboundMessage message) {
        if (message.eventIs(WechatInboundMessage.EVENT_SCAN)) {
            return handleScan(message, "已扫码，请回复 %s 确认登录飞鱼小铺。");
        }
        if (message.eventIs(WechatInboundMessage.EVENT_SUBSCRIBE)) {
            return handleScan(message, "关注成功，请回复 %s 确认登录飞鱼小铺。")
                    .switchIfEmpty(Mono.just(WechatReplyMessage.text(message, "感谢关注")));
        }
        if (message.eventIs(WechatInboundMessage.EVENT_UNSUBSCRIBE)) {
            log.info("微信用户取消关注。openid={}", message.getFromUserName());
        }
        return Mono.empty();
    }

    private Mono<WechatReplyMessage> handleScan(WechatInboundMessage message, String template) {
        String scene = wechatService.normalizeSessionCode(message.getEventKey());
        if (!wechatService.isSessionCode(scene)) {
            return Mono.empty();
        }
        wechatService.markScanned(scene, message.getFromUserName());
        return Mono.just(WechatReplyMessage.text(message, template.formatted(scene)));
    }

    private Mono<WechatReplyMessage> handleCommonMessage(WechatInboundMessage message) {
        WechatMessageRuleResult ruleResult = ruleEngine.handle(message);
        if (ruleResult.hit()) {
            log.info("微信消息命中业务规则。rule={}, msgType={}, openid={}",
                    ruleResult.ruleName(), message.getMsgType(), message.getFromUserName());
            if (ruleResult.action() == WechatMessageRuleResult.ReplyAction.CUSTOMER_SUPPORT) {
                return replyCustomerSupport(message, ruleResult.content());
            }
            return Mono.just(WechatReplyMessage.text(message, ruleResult.content()));
        }
        return transferToCustomerServiceIfPossible(message);
    }

    private Mono<WechatReplyMessage> replyCustomerSupport(WechatInboundMessage message, String guideText) {
        return wechatMpApiService.customerSupportImageMediaId()
                .map(mediaId -> {
                    sendCustomerSupportGuideTextAsync(message, guideText);
                    return WechatReplyMessage.image(message, mediaId);
                })
                .onErrorResume(e -> {
                    log.warn("客服二维码图片回复准备失败，降级为文字指引。openid={}, reason={}",
                            message.getFromUserName(), e.getMessage());
                    return Mono.just(WechatReplyMessage.text(message, guideText));
                });
    }

    private void sendCustomerSupportGuideTextAsync(WechatInboundMessage message, String guideText) {
        wechatMpApiService.sendKefuText(message.getFromUserName(), guideText)
                .subscribe(null, e -> log.warn("客服文字指引发送失败，仅保留图片被动回复。openid={}, reason={}",
                        message.getFromUserName(), e.getMessage()));
    }

    private Mono<WechatReplyMessage> transferToCustomerServiceIfPossible(WechatInboundMessage message) {
        return wechatMpApiService.hasOnlineKefu()
                .flatMap(hasOnlineKefu -> {
                    if (!hasOnlineKefu) {
                        log.info("微信消息未命中业务规则，且无在线客服，返回空响应。msgType={}, openid={}",
                                message.getMsgType(), message.getFromUserName());
                        return Mono.empty();
                    }
                    log.info("微信消息转发至官方客服系统。msgType={}, openid={}",
                            message.getMsgType(), message.getFromUserName());
                    return Mono.just(WechatReplyMessage.transferCustomerService(message));
                });
    }
}

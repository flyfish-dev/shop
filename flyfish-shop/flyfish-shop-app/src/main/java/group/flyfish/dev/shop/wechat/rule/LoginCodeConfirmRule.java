package group.flyfish.dev.shop.wechat.rule;

import group.flyfish.dev.shop.wechat.protocol.WechatInboundMessage;
import group.flyfish.dev.shop.wechat.service.WechatService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 微信验证码登录确认规则。
 */
@Component
@Order(300)
@RequiredArgsConstructor
public class LoginCodeConfirmRule implements WechatMessageRule {

    private static final String RULE_NAME = "login-code-confirm";

    private final WechatService wechatService;

    @Override
    public Mono<WechatMessageRuleResult> apply(WechatInboundMessage message) {
        if (!isTextMessage(message)) {
            return Mono.just(WechatMessageRuleResult.miss());
        }
        String scene = wechatService.normalizeSessionCode(message.getContent());
        return wechatService.isSessionCode(scene)
                .flatMap(exists -> {
                    if (!exists) {
                        return Mono.just(WechatMessageRuleResult.miss());
                    }
                    return wechatService.updateSession(scene, message.getFromUserName())
                            .thenReturn(WechatMessageRuleResult.reply(RULE_NAME, "恭喜您，已经成功登录飞鱼小铺！"));
                });
    }
}

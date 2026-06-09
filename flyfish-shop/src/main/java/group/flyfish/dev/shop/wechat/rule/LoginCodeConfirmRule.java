package group.flyfish.dev.shop.wechat.rule;

import group.flyfish.dev.shop.wechat.protocol.WechatInboundMessage;
import group.flyfish.dev.shop.wechat.service.WechatService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

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
    public WechatMessageRuleResult apply(WechatInboundMessage message) {
        if (!isTextMessage(message)) {
            return WechatMessageRuleResult.miss();
        }
        String scene = wechatService.normalizeSessionCode(message.getContent());
        if (!wechatService.isSessionCode(scene)) {
            return WechatMessageRuleResult.miss();
        }
        wechatService.updateSession(scene, message.getFromUserName());
        return WechatMessageRuleResult.reply(RULE_NAME, "恭喜您，已经成功登录飞鱼小铺！");
    }
}

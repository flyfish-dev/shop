package group.flyfish.dev.shop.wechat.rule;

import group.flyfish.dev.shop.wechat.protocol.WechatInboundMessage;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 客服咨询规则。
 */
@Component
@Order(200)
public class ContactSupportRule implements WechatMessageRule {

    private static final String RULE_NAME = "contact-support";

    private static final List<String> KEYWORDS = List.of("客服", "联系", "咨询", "人工", "售后", "帮助");

    @Override
    public WechatMessageRuleResult apply(WechatInboundMessage message) {
        if (!isTextMessage(message)) {
            return WechatMessageRuleResult.miss();
        }
        String content = normalizedText(message);
        boolean matched = KEYWORDS.stream().anyMatch(content::contains);
        if (!matched) {
            return WechatMessageRuleResult.miss();
        }
        return WechatMessageRuleResult.customerSupport(RULE_NAME,
                "如需详细了解产品、购买或交付问题，请添加客服微信号：Yous_Gift。\n"
                        + "添加时可备注“飞鱼小铺”，我们会尽快协助你处理。");
    }
}

package group.flyfish.dev.shop.wechat.rule;

/**
 * 公众号消息规则处理结果。
 *
 * <p>规则引擎只关心当前消息是否命中、命中后的处理动作和必要文本。
 * {@code ruleName} 仅用于日志排查，避免以后规则增多后难以定位是哪条规则生效。</p>
 */
public record WechatMessageRuleResult(boolean hit, String ruleName, ReplyAction action, String content) {

    public enum ReplyAction {
        TEXT,
        CUSTOMER_SUPPORT
    }

    public static WechatMessageRuleResult miss() {
        return new WechatMessageRuleResult(false, null, null, null);
    }

    public static WechatMessageRuleResult reply(String ruleName, String content) {
        return new WechatMessageRuleResult(true, ruleName, ReplyAction.TEXT, content);
    }

    public static WechatMessageRuleResult customerSupport(String ruleName, String content) {
        return new WechatMessageRuleResult(true, ruleName, ReplyAction.CUSTOMER_SUPPORT, content);
    }
}

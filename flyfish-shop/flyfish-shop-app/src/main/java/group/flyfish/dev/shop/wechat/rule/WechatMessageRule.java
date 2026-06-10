package group.flyfish.dev.shop.wechat.rule;

import group.flyfish.dev.shop.wechat.protocol.WechatInboundMessage;
import org.apache.commons.lang3.StringUtils;
import reactor.core.publisher.Mono;

/**
 * 公众号消息规则。
 *
 * <p>每条规则只负责一个业务意图判断，按 Spring {@code @Order} 排序后由规则引擎串行执行。
 * 这相当于职责链模式：高优先级规则先判断并短路，低优先级规则只处理前面没有消费的消息。</p>
 */
public interface WechatMessageRule {

    Mono<WechatMessageRuleResult> apply(WechatInboundMessage message);

    default boolean isTextMessage(WechatInboundMessage message) {
        return message != null && message.isText();
    }

    default String normalizedText(WechatInboundMessage message) {
        return StringUtils.deleteWhitespace(StringUtils.trimToEmpty(message == null ? null : message.getContent()));
    }
}

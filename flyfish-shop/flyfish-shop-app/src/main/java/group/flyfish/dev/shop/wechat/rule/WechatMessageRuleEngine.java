package group.flyfish.dev.shop.wechat.rule;

import group.flyfish.dev.shop.wechat.protocol.WechatInboundMessage;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * 公众号消息规则引擎。
 *
 * <p>对外暴露统一入口，内部按优先级串行执行规则，第一条命中的规则即返回。
 * 后续新增自动回复时，只需要实现 {@link WechatMessageRule} 并声明 {@code @Order}，
 * 不需要继续修改消息 Handler 的主流程。</p>
 */
@Component
public class WechatMessageRuleEngine {

    private final List<WechatMessageRule> rules;

    public WechatMessageRuleEngine(List<WechatMessageRule> rules) {
        List<WechatMessageRule> sortedRules = new ArrayList<>(rules == null ? List.of() : rules);
        AnnotationAwareOrderComparator.sort(sortedRules);
        this.rules = List.copyOf(sortedRules);
    }

    public Mono<WechatMessageRuleResult> handle(WechatInboundMessage message) {
        return Flux.fromIterable(rules)
                .concatMap(rule -> rule.apply(message).defaultIfEmpty(WechatMessageRuleResult.miss()))
                .filter(WechatMessageRuleResult::hit)
                .next()
                .defaultIfEmpty(WechatMessageRuleResult.miss());
    }
}

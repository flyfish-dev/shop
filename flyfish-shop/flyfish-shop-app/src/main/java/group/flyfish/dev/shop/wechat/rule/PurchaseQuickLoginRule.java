package group.flyfish.dev.shop.wechat.rule;

import group.flyfish.dev.shop.wechat.config.WechatQuickLoginProperties;
import group.flyfish.dev.shop.wechat.protocol.WechatInboundMessage;
import group.flyfish.dev.shop.wechat.service.WechatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 购买意图规则。
 *
 * <p>优先级最高，只要用户消息包含“购买”或“开通”，就生成已确认的微信快捷登录会话，
 * 引导用户一键进入飞鱼小铺。该规则优先于客服咨询，避免“咨询开通”被误判成普通客服消息。</p>
 */
@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
public class PurchaseQuickLoginRule implements WechatMessageRule {

    private static final String RULE_NAME = "purchase-quick-login";

    private final WechatService wechatService;

    private final WechatQuickLoginProperties quickLoginProperties;

    @Override
    public Mono<WechatMessageRuleResult> apply(WechatInboundMessage message) {
        if (!isTextMessage(message)) {
            return Mono.just(WechatMessageRuleResult.miss());
        }
        String content = normalizedText(message);
        if (!content.contains("购买") && !content.contains("开通")) {
            return Mono.just(WechatMessageRuleResult.miss());
        }
        int expireSeconds = resolveQuickLoginExpireSeconds();
        return wechatService.createConfirmedSession(message.getFromUserName(), expireSeconds)
                .filter(StringUtils::isNotBlank)
                .map(scene -> {
                    String link = buildQuickLoginLink(scene);
                    log.info("公众号购买关键词生成快捷登录入口。openid={}, scene={}", message.getFromUserName(), scene);
                    return WechatMessageRuleResult.reply(RULE_NAME,
                            "已为你生成飞鱼小铺快捷入口，" + formatExpireMinutes(expireSeconds) + "分钟内有效。\n"
                                    + "<a href=\"" + link + "\">点击进入飞鱼小铺</a>");
                })
                .defaultIfEmpty(WechatMessageRuleResult.reply(RULE_NAME,
                        "快捷入口生成失败，请稍后再试，或回复“客服”联系人工处理。"));
    }

    private String buildQuickLoginLink(String scene) {
        String baseUrl = StringUtils.removeEnd(StringUtils.trimToEmpty(quickLoginProperties.getBaseUrl()), "/");
        if (StringUtils.isBlank(baseUrl)) {
            baseUrl = "https://shop.example.com";
        }
        String redirect = URLEncoder.encode(
                StringUtils.defaultIfBlank(quickLoginProperties.getPurchaseRedirect(), "/shop/item-list"),
                StandardCharsets.UTF_8);
        return baseUrl + "/wx/quick-login/" + scene + "?redirect=" + redirect;
    }

    private int resolveQuickLoginExpireSeconds() {
        int seconds = quickLoginProperties.getExpireSeconds();
        return seconds > 0 ? seconds : 15 * 60;
    }

    private int formatExpireMinutes(int expireSeconds) {
        return Math.max(1, (int) Math.ceil(expireSeconds / 60.0));
    }
}

package group.flyfish.dev.support.notification;

import group.flyfish.dev.shop.wechat.service.WechatMpApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SupportTicketWechatNotifier {

    private final WechatMpApiService wechatMpApiService;

    private final SupportNotificationProperties properties;

    public Mono<Void> send(Collection<String> openids, SupportTicketNotificationMessage message) {
        List<String> validOpenids = normalizeOpenids(openids);
        if (!properties.isEnabled() || !properties.getWechat().isEnabled() || validOpenids.isEmpty()) {
            return Mono.empty();
        }
        return Flux.fromIterable(validOpenids)
                .concatMap(openid -> wechatMpApiService.sendKefuText(openid, message.wechatText())
                        .doOnSuccess(ignored -> log.info("工单微信客服消息发送成功。openid={}", maskOpenid(openid)))
                        .onErrorResume(e -> {
                            log.warn("工单微信客服消息发送失败，已跳过该用户。openid={}, error={}",
                                    maskOpenid(openid), e.getMessage());
                            return Mono.empty();
                        }))
                .then();
    }

    public List<String> normalizeOpenids(Collection<String> openids) {
        if (openids == null || openids.isEmpty()) {
            return List.of();
        }
        return openids.stream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private String maskOpenid(String openid) {
        if (StringUtils.length(openid) <= 8) {
            return "****";
        }
        return StringUtils.left(openid, 4) + "****" + StringUtils.right(openid, 4);
    }
}

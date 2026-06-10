package group.flyfish.dev.wechat.controller;

import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.user.service.impl.TokenProvider;
import group.flyfish.dev.wechat.config.WechatQuickLoginRedirectProperties;
import group.flyfish.dev.wechat.service.WechatLoginService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;

/**
 * 公众号一键快捷登录入口。
 *
 * <p>该入口由公众号被动回复生成，登录码已经在消息回复时绑定到发送消息的 openid。用户点击链接后，
 * 服务端会一次性消费该登录码，写入与普通 OAuth 登录一致的 HttpOnly Cookie，然后跳回站内页面。</p>
 *
 * @author wangyu
 */
@RestController
@RequestMapping("wx/quick-login")
@RequiredArgsConstructor
@Slf4j
public class QuickLoginController {

    private final WechatLoginService wechatLoginService;

    private final TokenProvider tokenProvider;

    private final WechatQuickLoginRedirectProperties quickLoginRedirectProperties;

    @GetMapping("{code}")
    public Mono<ResponseEntity<Void>> login(@PathVariable("code") String scene,
                                            @RequestParam(name = "redirect", required = false) String redirect,
                                            ServerWebExchange exchange) {
        String target = normalizeRedirect(redirect);
        return wechatLoginService.consumeConfirmedSession(scene)
                .map(token -> {
                    tokenProvider.writeTokenCookie(exchange, token.getToken(), token.getExpire());
                    return redirectTo(target);
                })
                .onErrorResume(BusinessException.class, e -> {
                    log.warn("公众号快捷登录失败：code={}, reason={}", scene, e.getMessage());
                    return Mono.just(redirectTo("/login?wechat=expired"));
                });
    }

    private ResponseEntity<Void> redirectTo(String target) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(target))
                .build();
    }

    private String normalizeRedirect(String redirect) {
        String value = StringUtils.trimToEmpty(redirect);
        if (StringUtils.isBlank(value)) {
            return normalizeDefaultRedirect();
        }
        if (StringUtils.startsWith(value, "/")
                && !StringUtils.startsWith(value, "//")
                && !StringUtils.containsAny(value, "\r", "\n")) {
            return value;
        }
        return normalizeDefaultRedirect();
    }

    private String normalizeDefaultRedirect() {
        String value = StringUtils.trimToEmpty(quickLoginRedirectProperties.getDefaultRedirect());
        if (StringUtils.startsWith(value, "/")
                && !StringUtils.startsWith(value, "//")
                && !StringUtils.containsAny(value, "\r", "\n")) {
            return value;
        }
        return "/";
    }
}

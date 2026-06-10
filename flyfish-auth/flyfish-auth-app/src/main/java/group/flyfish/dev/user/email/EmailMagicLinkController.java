package group.flyfish.dev.user.email;

import group.flyfish.dev.annotations.user.CurrentUser;
import group.flyfish.dev.common.bean.Result;
import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.oauth.service.OAuthSessionService;
import group.flyfish.dev.auth.api.user.PortalUserVo;
import group.flyfish.dev.user.service.impl.TokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.reactive.result.view.Rendering;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Controller
@RequestMapping("/email/magic-links")
@RequiredArgsConstructor
@Slf4j
public class EmailMagicLinkController {

    private final EmailMagicLinkService emailMagicLinkService;
    private final TokenProvider tokenProvider;
    private final OAuthSessionService oauthSessionService;

    @PostMapping
    @ResponseBody
    public Mono<Result<EmailMagicLinkSentVo>> send(@RequestBody EmailMagicLinkRequest request,
                                                   @CurrentUser PortalUserVo user) {
        return emailMagicLinkService.send(request, user).map(Result::ok);
    }

    @GetMapping("consume")
    public Mono<Rendering> consume(@RequestParam("token") String token, ServerWebExchange exchange) {
        return emailMagicLinkService.consume(token)
                .map(result -> redirectRendering(result, exchange))
                .onErrorResume(BusinessException.class, e -> {
                    log.warn("邮箱快速登录失败：{}", e.getMessage());
                    return Mono.just(Rendering.redirectTo("/login?email=expired").build());
                });
    }

    private Rendering redirectRendering(EmailMagicLinkLoginResult result, ServerWebExchange exchange) {
        tokenProvider.writeTokenCookie(exchange, result.token().getToken(), result.token().getExpire());
        oauthSessionService.clearProfiles(exchange);
        return Rendering.view("oauth/redirect")
                .modelAttribute("redirect", emailMagicLinkService.normalizeRedirect(result.redirect()))
                .modelAttribute("token", result.token().getToken())
                .build();
    }
}

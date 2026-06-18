package group.flyfish.dev.oauth.controller;

import group.flyfish.dev.annotations.user.CurrentUser;
import group.flyfish.dev.common.bean.Result;
import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.oauth.config.OAuthProperties;
import group.flyfish.dev.oauth.config.Pac4jProperties;
import group.flyfish.dev.oauth.service.OAuthSessionService;
import group.flyfish.dev.user.config.JwtProperties;
import group.flyfish.dev.user.domain.bo.OAuthBindPlan;
import group.flyfish.dev.user.domain.bo.PendingOAuthBinding;
import group.flyfish.dev.user.domain.UserToken;
import group.flyfish.dev.auth.api.user.PortalUserVo;
import group.flyfish.dev.user.email.EmailMagicLinkProperties;
import group.flyfish.dev.user.service.PortalUserService;
import group.flyfish.dev.user.service.impl.TokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.pac4j.core.adapter.FrameworkAdapter;
import org.pac4j.core.config.Config;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.profile.ProfileManager;
import org.pac4j.springframework.context.SpringWebFluxFrameworkParameters;
import org.pac4j.springframework.context.SpringWebfluxSessionStore;
import org.pac4j.springframework.context.SpringWebfluxWebContext;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpCookie;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.reactive.result.view.Rendering;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.pac4j.springframework.context.SpringWebfluxWebContext.SAML_BODY_ATTRIBUTE;

/**
 * 授权回调controller
 *
 * @author wangyu
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class CallbackController {

    private static final String BIND_USER_ID = "oauth_bind_user_id";
    private static final String BIND_REDIRECT = "oauth_bind_redirect";
    private static final String BIND_COOKIE = "FF_OAUTH_BIND";
    private static final String PENDING_BINDINGS = "oauth_pending_bindings";
    private static final Duration BIND_TTL = Duration.ofMinutes(10);

    private final Config config;
    private final Pac4jProperties properties;
    private final OAuthProperties oauthProperties;
    private final JwtProperties jwtProperties;
    private final PortalUserService portalUserService;
    private final TokenProvider tokenProvider;
    private final OAuthSessionService oauthSessionService;
    private final EmailMagicLinkProperties emailMagicLinkProperties;

    @GetMapping("/oauth/providers")
    @ResponseBody
    public Result<Map<String, Boolean>> providers() {
        OAuthProperties.Gitea gitea = oauthProperties.getGitea();
        OAuthProperties.Gitee gitee = oauthProperties.getGitee();
        OAuthProperties.Github github = oauthProperties.getGithub();
        Map<String, Boolean> providers = new LinkedHashMap<>();
        providers.put("wechat", true);
        providers.put("email", emailMagicLinkProperties.isEnabled());
        providers.put("gitea", hasClient(gitea.getClientId(), gitea.getClientSecret()));
        providers.put("gitee", hasClient(gitee.getClientId(), gitee.getClientSecret()));
        providers.put("github", hasClient(github.getClientId(), github.getClientSecret()));
        return Result.ok(providers);
    }

    @PostMapping("/oauth/bind/{provider}")
    @ResponseBody
    public Mono<Result<String>> prepareProviderBind(@PathVariable String provider,
                                                    @RequestParam(required = false) String redirect,
                                                    @CurrentUser PortalUserVo user, ServerWebExchange exchange) {
        return prepareBind(provider, redirect, user, exchange);
    }

    private Mono<Result<String>> prepareBind(String provider, String redirect, PortalUserVo user,
                                             ServerWebExchange exchange) {
        if (user == null || user.getId() == null || user.getId() <= 0) {
            return Mono.error(new BusinessException("USER_REQUIRED", "请先登录后再绑定账号"));
        }
        String normalizedProvider = normalizeProvider(provider);
        String redirectPath = normalizeRedirect(redirect);
        return exchange.getSession()
                .doOnNext(session -> {
                    session.getAttributes().put(BIND_USER_ID, user.getId());
                    session.getAttributes().put(BIND_REDIRECT, redirectPath);
                    addBindingCookie(exchange, user.getId(), redirectPath);
                })
                .thenReturn(oauthRedirect("/oauth/" + normalizedProvider));
    }

    /**
     * 要跳转的目标地址
     *
     * @param redirect 真实地址
     * @param exchange 交换报文
     * @return 结果
     */
    @RequestMapping("/oauth/redirect")
    public Mono<Rendering> redirect(String redirect, ServerWebExchange exchange) {
        final WebContext context = new SpringWebfluxWebContext(exchange);
        final SessionStore sessionStore = new SpringWebfluxSessionStore(exchange);
        final ProfileManager profileManager = new ProfileManager(context, sessionStore);
        return exchange.getSession()
                .flatMap(session -> {
                    BindingContext binding = takeBindingContext(session, exchange);
                    clearBindingCookie(exchange);
                    String target = binding == null ? normalizeRedirect(redirect) : binding.redirect();
                    return Mono.justOrEmpty(profileManager.getProfile())
                            .flatMap(profile -> {
                                Long bindingUserId = binding == null ? null : binding.userId();
                                if (bindingUserId != null) {
                                    String ticket = UUID.randomUUID().toString();
                                    return portalUserService.prepareBindAuthorization(profile, bindingUserId, ticket, target)
                                            .map(plan -> {
                                                if (plan.requiresConfirmation()) {
                                                    savePendingBinding(session, ticket, plan);
                                                    return Rendering.view("oauth/bind-confirm")
                                                            .modelAttribute("confirmation", plan.confirmation())
                                                            .build();
                                                }
                                                return redirectRendering(target, plan.token(), exchange);
                                            });
                                }
                                return portalUserService.registerOrLogin(profile)
                                        .map(token -> redirectRendering(target, token, exchange));
                            });
                })
                .switchIfEmpty(Mono.just(Rendering.redirectTo("/login").build()));
    }

    @PostMapping("/oauth/bind/confirm")
    public Mono<Rendering> confirmBind(@RequestParam(required = false) String ticket, ServerWebExchange exchange) {
        return resolveConfirmTicket(ticket, exchange)
                .flatMap(confirmTicket -> exchange.getSession()
                        .flatMap(session -> {
                            PendingOAuthBinding pending = takePendingBinding(session, confirmTicket);
                            if (pending == null) {
                                return Mono.just(Rendering.redirectTo("/account/profile?bind=expired").build());
                            }
                            return portalUserService.confirmBindAuthorization(pending)
                                    .map(token -> redirectRendering(pending.redirect(), token, exchange));
                        }));
    }

    @GetMapping("/oauth/bind/cancel")
    public Mono<Rendering> cancelBind(@RequestParam String ticket, ServerWebExchange exchange) {
        return exchange.getSession()
                .map(session -> {
                    PendingOAuthBinding pending = takePendingBinding(session, ticket);
                    String target = pending == null ? "/account/profile" : pending.redirect();
                    return Rendering.redirectTo(normalizeRedirect(target)).build();
                });
    }

    @RequestMapping("/oauth/**")
    public Mono<Rendering> authorized() {
        return Mono.just(Rendering.redirectTo("/").build());
    }

    @RequestMapping("${pac4j.callback.path:/callback}")
    public Mono<Void> callback(final ServerWebExchange serverWebExchange) {

        final SpringWebFluxFrameworkParameters frameworkParameters = new SpringWebFluxFrameworkParameters(serverWebExchange);
        FrameworkAdapter.INSTANCE.applyDefaultSettingsIfUndefined(config);
        return DataBufferUtils.join(serverWebExchange.getRequest().getBody())
                .map(DataBuffer::asByteBuffer)
                .map(ByteBuffer::array)
                .map(String::new)
                .switchIfEmpty(Mono.just(""))
                .flatMap(s -> {
                            // Include the request content in the attributes so that
                            // downstream authentication mechanisms (e.g. SAML) can use
                            // to extract authentication mechanism.
                            serverWebExchange.getAttributes().put(SAML_BODY_ATTRIBUTE, s);
                            Pac4jProperties.Callback callback = properties.getCallback();
                            return (Mono<Void>) config.getCallbackLogic().perform(config,
                                    callback.getDefaultUrl(), callback.getRenewSession(), callback.getDefaultClient(),
                                    frameworkParameters);
                        }

                )
                .onErrorResume(error -> {
                    log.warn("OAuth callback failed, redirecting to login page: {}", error.getMessage());
                    clearBindingCookie(serverWebExchange);
                    serverWebExchange.getResponse().setStatusCode(HttpStatus.FOUND);
                    serverWebExchange.getResponse().getHeaders().setLocation(URI.create("/login?oauth=failed"));
                    return serverWebExchange.getResponse().setComplete();
                });
    }

    @RequestMapping("${pac4j.callback.path/{cn}:/callback/{cn}}")
    public Mono<Void> callbackWithClientName(final ServerWebExchange serverWebExchange, @PathVariable("cn") final String cn) {
        return callback(serverWebExchange);
    }

    private Rendering redirectRendering(String redirect, UserToken token, ServerWebExchange exchange) {
        tokenProvider.writeTokenCookie(exchange, token.getToken(), token.getExpire());
        oauthSessionService.clearProfiles(exchange);
        return Rendering.view("oauth/redirect")
                .modelAttribute("redirect", normalizeRedirect(redirect))
                .modelAttribute("token", token.getToken())
                .build();
    }

    private void savePendingBinding(WebSession session, String ticket, OAuthBindPlan plan) {
        pendingBindings(session).put(ticket, plan.pending());
    }

    private PendingOAuthBinding takePendingBinding(WebSession session, String ticket) {
        if (StringUtils.isBlank(ticket)) {
            return null;
        }
        PendingOAuthBinding pending = pendingBindings(session).remove(ticket);
        if (pending == null || pending.isExpired()) {
            return null;
        }
        return pending;
    }

    @SuppressWarnings("unchecked")
    private Map<String, PendingOAuthBinding> pendingBindings(WebSession session) {
        Object raw = session.getAttributes().get(PENDING_BINDINGS);
        if (raw instanceof Map<?, ?> map) {
            return (Map<String, PendingOAuthBinding>) map;
        }
        Map<String, PendingOAuthBinding> map = new LinkedHashMap<>();
        session.getAttributes().put(PENDING_BINDINGS, map);
        return map;
    }

    private Mono<String> resolveConfirmTicket(String ticket, ServerWebExchange exchange) {
        if (StringUtils.isNotBlank(ticket)) {
            return Mono.just(ticket);
        }
        MediaType contentType = exchange.getRequest().getHeaders().getContentType();
        if (contentType != null && MediaType.APPLICATION_FORM_URLENCODED.isCompatibleWith(contentType)) {
            return exchange.getFormData()
                    .flatMap(form -> Mono.justOrEmpty(form.getFirst("ticket")))
                    .defaultIfEmpty("");
        }
        return Mono.just("");
    }

    private BindingContext takeBindingContext(WebSession session, ServerWebExchange exchange) {
        Long userId = takeBindingUserId(session);
        if (userId != null) {
            return new BindingContext(userId, takeBindingRedirect(session));
        }
        return parseBindingCookie(exchange);
    }

    private Long takeBindingUserId(WebSession session) {
        Object raw = session.getAttributes().remove(BIND_USER_ID);
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String value) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String takeBindingRedirect(WebSession session) {
        Object raw = session.getAttributes().remove(BIND_REDIRECT);
        if (raw instanceof String value) {
            return normalizeRedirect(value);
        }
        return "/account/profile";
    }

    private String normalizeProvider(String provider) {
        String value = provider == null ? "" : provider.trim().toLowerCase();
        if ("gitea".equals(value) || "gitee".equals(value) || "github".equals(value)) {
            return value;
        }
        throw new BusinessException("UNSUPPORTED_OAUTH_PROVIDER", "暂不支持该授权平台");
    }

    private String normalizeRedirect(String redirect) {
        if (redirect == null || redirect.isBlank() || !redirect.startsWith("/") || redirect.startsWith("//")) {
            return "/account/profile";
        }
        return redirect;
    }

    private Result<String> oauthRedirect(String url) {
        Result<String> result = Result.ok();
        result.setResult(url);
        return result;
    }

    private void addBindingCookie(ServerWebExchange exchange, Long userId, String redirect) {
        String ticket = createBindingTicket(userId, redirect);
        ResponseCookie cookie = ResponseCookie.from(BIND_COOKIE, ticket)
                .path("/oauth")
                .httpOnly(true)
                .secure(isSecure(exchange))
                .sameSite("Lax")
                .maxAge(BIND_TTL)
                .build();
        exchange.getResponse().addCookie(cookie);
    }

    private void clearBindingCookie(ServerWebExchange exchange) {
        ResponseCookie cookie = ResponseCookie.from(BIND_COOKIE, "")
                .path("/oauth")
                .httpOnly(true)
                .secure(isSecure(exchange))
                .sameSite("Lax")
                .maxAge(Duration.ZERO)
                .build();
        exchange.getResponse().addCookie(cookie);
    }

    private String createBindingTicket(Long userId, String redirect) {
        long expiresAt = Instant.now().plus(BIND_TTL).toEpochMilli();
        String payload = userId + "\n" + normalizeRedirect(redirect) + "\n" + expiresAt + "\n" + UUID.randomUUID();
        String encodedPayload = base64Url(payload.getBytes(StandardCharsets.UTF_8));
        return encodedPayload + "." + sign(encodedPayload);
    }

    private BindingContext parseBindingCookie(ServerWebExchange exchange) {
        HttpCookie cookie = exchange.getRequest().getCookies().getFirst(BIND_COOKIE);
        if (cookie == null || StringUtils.isBlank(cookie.getValue())) {
            return null;
        }
        String[] parts = cookie.getValue().split("\\.", 2);
        if (parts.length != 2 || !constantTimeEquals(parts[1], sign(parts[0]))) {
            return null;
        }
        try {
            String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            String[] values = payload.split("\n", 4);
            if (values.length < 3) {
                return null;
            }
            long expiresAt = Long.parseLong(values[2]);
            if (expiresAt < System.currentTimeMillis()) {
                return null;
            }
            long userId = Long.parseLong(values[0]);
            if (userId <= 0) {
                return null;
            }
            return new BindingContext(userId, normalizeRedirect(values[1]));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return base64Url(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new BusinessException("OAUTH_BIND_TICKET_ERROR", "账号绑定初始化失败");
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private boolean isSecure(ServerWebExchange exchange) {
        return "https".equalsIgnoreCase(exchange.getRequest().getURI().getScheme());
    }

    private boolean hasClient(String clientId, String clientSecret) {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }

    private record BindingContext(Long userId, String redirect) {
    }
}

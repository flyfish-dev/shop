package group.flyfish.dev.user.email;

import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.auth.api.user.PortalUserVo;
import group.flyfish.dev.user.service.PortalUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailMagicLinkService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$");

    private final EmailMagicLinkProperties properties;
    private final EmailMagicLinkTokenService tokenService;
    private final PortalUserService portalUserService;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final ConcurrentMap<String, Instant> nextAllowedSendAt = new ConcurrentHashMap<>();

    public Mono<EmailMagicLinkSentVo> send(EmailMagicLinkRequest request, PortalUserVo user) {
        return send(request, user, null);
    }

    public Mono<EmailMagicLinkSentVo> send(EmailMagicLinkRequest request, PortalUserVo user,
                                           ServerWebExchange exchange) {
        if (!properties.isEnabled()) {
            return Mono.error(new BusinessException("EMAIL_MAGIC_LINK_DISABLED", "邮箱快速登录未启用"));
        }
        String email = normalizeEmail(request == null ? null : request.getEmail());
        if (email == null) {
            return Mono.error(new BusinessException("EMAIL_INVALID", "请输入正确的邮箱地址"));
        }
        Long bindUserId = bindUserId(request, user);
        String redirect = normalizeRedirect(request == null ? null : request.getRedirect());
        SendReservation reservation = reserveSend(email);
        if (!reservation.permitted()) {
            return Mono.error(new BusinessException("EMAIL_MAGIC_LINK_COOLDOWN",
                    "验证邮件已发送，请 " + reservation.remainingSeconds() + " 秒后再试"));
        }
        String token = tokenService.issue(email, bindUserId, redirect);
        String link = loginLink(token, exchange);
        return sendMail(email, link)
                .onErrorResume(e -> {
                    releaseReservation(email, reservation);
                    return Mono.error(e);
                })
                .thenReturn(EmailMagicLinkSentVo.builder()
                        .email(email)
                        .maskedEmail(maskEmail(email))
                        .expiresInSeconds(properties.getExpiresIn().toSeconds())
                        .resendCooldownSeconds(reservation.cooldownSeconds())
                        .build());
    }

    public Mono<EmailMagicLinkLoginResult> consume(String token) {
        EmailMagicLinkPayload payload = tokenService.consume(token);
        Mono<group.flyfish.dev.user.domain.UserToken> login = payload.bindUserId() != null && payload.bindUserId() > 0
                ? portalUserService.bindEmailAuthorization(payload.email(), payload.bindUserId())
                : portalUserService.registerOrLoginEmail(payload.email());
        return login.map(userToken -> new EmailMagicLinkLoginResult(userToken, normalizeRedirect(payload.redirect())));
    }

    private Mono<Void> sendMail(String email, String link) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            return Mono.error(new BusinessException("EMAIL_SENDER_MISSING",
                    "邮件服务未配置，请检查 spring.mail.host/username/password"));
        }
        return Mono.fromRunnable(() -> mailSender.send(toMail(email, link)))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> log.warn("邮箱快速登录邮件发送失败：email={}, reason={}", email, e.getMessage()))
                .onErrorMap(e -> e instanceof BusinessException ? e
                        : new BusinessException("EMAIL_SEND_FAILED", "验证邮件发送失败，请稍后重试"))
                .then();
    }

    private SimpleMailMessage toMail(String email, String link) {
        SimpleMailMessage mail = new SimpleMailMessage();
        if (StringUtils.isNotBlank(properties.getFrom())) {
            mail.setFrom(properties.getFrom().trim());
        }
        mail.setTo(email);
        mail.setSubject(StringUtils.defaultIfBlank(properties.getSubject(), "飞鱼邮箱快速登录"));
        mail.setText("""
                你好，

                点击下面的链接即可完成飞鱼邮箱快速登录：
                %s

                该链接将在 %s 分钟后失效，并且只能使用一次。如果这不是你本人操作，可以忽略这封邮件。
                """.formatted(link, Math.max(1, properties.getExpiresIn().toMinutes())));
        return mail;
    }

    private Long bindUserId(EmailMagicLinkRequest request, PortalUserVo user) {
        if (request == null || !"bind".equalsIgnoreCase(StringUtils.trimToEmpty(request.getMode()))) {
            return null;
        }
        if (user == null || user.getId() == null || user.getId() <= 0) {
            throw new BusinessException("USER_REQUIRED", "请先登录后再绑定邮箱");
        }
        return user.getId();
    }

    private String loginLink(String token, ServerWebExchange exchange) {
        String configuredBaseUrl = normalizeBaseUrl(properties.getBaseUrl());
        String requestBaseUrl = requestBaseUrl(exchange);
        String baseUrl = isLocalBaseUrl(configuredBaseUrl) && StringUtils.isNotBlank(requestBaseUrl)
                ? requestBaseUrl
                : StringUtils.defaultIfBlank(configuredBaseUrl, requestBaseUrl);
        if (StringUtils.isBlank(baseUrl)) {
            baseUrl = "http://127.0.0.1:9999";
        }
        return baseUrl + "/email/magic-links/consume?token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    private String requestBaseUrl(ServerWebExchange exchange) {
        if (exchange == null) {
            return null;
        }
        HttpHeaders headers = exchange.getRequest().getHeaders();
        String origin = normalizeBaseUrl(headers.getOrigin());
        if (isTrustedBaseUrl(origin)) {
            return origin;
        }
        String forwardedHost = firstHeaderValue(headers.getFirst("X-Forwarded-Host"));
        String host = StringUtils.defaultIfBlank(forwardedHost, headers.getFirst(HttpHeaders.HOST));
        String scheme = StringUtils.defaultIfBlank(firstHeaderValue(headers.getFirst("X-Forwarded-Proto")),
                exchange.getRequest().getURI().getScheme());
        String forwardedBaseUrl = baseUrl(scheme, host);
        if (isTrustedBaseUrl(forwardedBaseUrl)) {
            return forwardedBaseUrl;
        }
        URI uri = exchange.getRequest().getURI();
        return trustedBaseUrl(uri.getScheme(), uri.getAuthority());
    }

    private String baseUrl(String scheme, String host) {
        if (StringUtils.isAnyBlank(scheme, host)) {
            return null;
        }
        return normalizeBaseUrl(scheme + "://" + host);
    }

    private String trustedBaseUrl(String scheme, String authority) {
        String baseUrl = baseUrl(scheme, authority);
        return isTrustedBaseUrl(baseUrl) ? baseUrl : null;
    }

    private String normalizeBaseUrl(String value) {
        String baseUrl = StringUtils.removeEnd(StringUtils.trimToEmpty(value), "/");
        if (StringUtils.isBlank(baseUrl) || StringUtils.containsAny(baseUrl, "\r", "\n")) {
            return null;
        }
        try {
            URI uri = URI.create(baseUrl);
            if (!StringUtils.equalsAnyIgnoreCase(uri.getScheme(), "http", "https")
                    || StringUtils.isBlank(uri.getHost())) {
                return null;
            }
            return uri.getScheme() + "://" + uri.getAuthority();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean isTrustedBaseUrl(String baseUrl) {
        if (StringUtils.isBlank(baseUrl)) {
            return false;
        }
        try {
            String host = StringUtils.lowerCase(URI.create(baseUrl).getHost());
            return isLocalHost(host)
                    || StringUtils.equalsAny(host, "flyfish.group", "flyfish.dev")
                    || StringUtils.endsWith(host, ".flyfish.group")
                    || StringUtils.endsWith(host, ".flyfish.dev");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean isLocalBaseUrl(String baseUrl) {
        if (StringUtils.isBlank(baseUrl)) {
            return true;
        }
        try {
            return isLocalHost(StringUtils.lowerCase(URI.create(baseUrl).getHost()));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean isLocalHost(String host) {
        return StringUtils.equalsAny(host, "localhost", "127.0.0.1", "::1", "0:0:0:0:0:0:0:1");
    }

    private String firstHeaderValue(String value) {
        return StringUtils.substringBefore(StringUtils.trimToEmpty(value), ",").trim();
    }

    private String normalizeEmail(String value) {
        String email = StringUtils.trimToNull(value);
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            return null;
        }
        return email.toLowerCase();
    }

    public String normalizeRedirect(String redirect) {
        String value = StringUtils.trimToEmpty(redirect);
        if (StringUtils.isBlank(value)) {
            return "/";
        }
        if (StringUtils.startsWith(value, "/")
                && !StringUtils.startsWith(value, "//")
                && !StringUtils.containsAny(value, "\r", "\n")) {
            return value;
        }
        return "/";
    }

    private String maskEmail(String email) {
        String[] parts = StringUtils.split(email, "@");
        if (parts == null || parts.length != 2) {
            return email;
        }
        String name = parts[0];
        String visible = name.length() <= 2 ? name.substring(0, 1) : name.substring(0, 2);
        return visible + "***@" + parts[1];
    }

    private SendReservation reserveSend(String email) {
        long cooldownSeconds = resendCooldownSeconds();
        if (cooldownSeconds <= 0) {
            return SendReservation.permitted(0);
        }
        Instant now = Instant.now();
        AtomicBoolean permitted = new AtomicBoolean(false);
        AtomicLong remainingSeconds = new AtomicLong(0);
        AtomicReference<Instant> reservedUntil = new AtomicReference<>();
        nextAllowedSendAt.compute(email, (key, existing) -> {
            long remaining = remainingSeconds(existing, now);
            if (remaining > 0) {
                remainingSeconds.set(remaining);
                return existing;
            }
            permitted.set(true);
            Instant nextSendAt = now.plusSeconds(cooldownSeconds);
            reservedUntil.set(nextSendAt);
            return nextSendAt;
        });
        if (permitted.get()) {
            return SendReservation.permitted(cooldownSeconds, reservedUntil.get());
        }
        return SendReservation.rejected(remainingSeconds.get(), cooldownSeconds);
    }

    private void releaseReservation(String email, SendReservation reservation) {
        if (reservation != null && reservation.permitted() && reservation.reservedUntil() != null) {
            nextAllowedSendAt.remove(email, reservation.reservedUntil());
        }
    }

    private long resendCooldownSeconds() {
        Duration cooldown = properties.getResendCooldown();
        if (cooldown == null || cooldown.isZero() || cooldown.isNegative()) {
            return 0;
        }
        return Math.max(1, cooldown.toSeconds());
    }

    private long remainingSeconds(Instant nextSendAt, Instant now) {
        if (nextSendAt == null || now == null || !nextSendAt.isAfter(now)) {
            return 0;
        }
        long millis = Duration.between(now, nextSendAt).toMillis();
        return Math.max(1, (long) Math.ceil(millis / 1000.0));
    }

    private record SendReservation(boolean permitted, long remainingSeconds, long cooldownSeconds,
                                   Instant reservedUntil) {

        static SendReservation permitted(long cooldownSeconds) {
            return permitted(cooldownSeconds, null);
        }

        static SendReservation permitted(long cooldownSeconds, Instant reservedUntil) {
            return new SendReservation(true, 0, cooldownSeconds, reservedUntil);
        }

        static SendReservation rejected(long remainingSeconds, long cooldownSeconds) {
            return new SendReservation(false, remainingSeconds, cooldownSeconds, null);
        }
    }
}

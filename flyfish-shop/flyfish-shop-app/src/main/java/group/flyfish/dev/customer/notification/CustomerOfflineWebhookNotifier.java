package group.flyfish.dev.customer.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * 客服离线 Webhook 提醒发送器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerOfflineWebhookNotifier {

    static final String HEADER_EVENT = "X-Flyfish-Event";
    static final String HEADER_DELIVERY = "X-Flyfish-Delivery";
    static final String HEADER_TIMESTAMP = "X-Flyfish-Timestamp";
    static final String HEADER_SIGNATURE = "X-Flyfish-Signature";

    private static final int MAX_CONCURRENCY = 3;

    private final CustomerOfflineWebhookClient webhookClient;
    private final CustomerOfflineNotificationProperties properties;
    private final ObjectMapper objectMapper;
    private final CustomerOfflineWebhookSigner signer = new CustomerOfflineWebhookSigner();

    public Mono<Void> send(CustomerOfflineNotification notification) {
        List<URI> targets = normalizedTargets();
        if (!properties.isEnabled() || !properties.getWebhook().isEnabled() || targets.isEmpty()) {
            return Mono.empty();
        }
        return Mono.fromCallable(() -> objectMapper.writeValueAsBytes(notification.payload()))
                .flatMap(body -> Flux.fromIterable(targets)
                        .flatMap(target -> send(target, notification, body), MAX_CONCURRENCY)
                        .then())
                .onErrorResume(error -> {
                    log.warn("序列化或调度客服离线 Webhook 失败，已降级跳过。eventId={}, error={}",
                            notification.payload().eventId(), error.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<Void> send(URI target, CustomerOfflineNotification notification, byte[] body) {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        Map<String, String> headers = headers(notification, timestamp, body);
        Mono<Void> request = webhookClient.send(target, headers, body)
                .timeout(validDuration(properties.getWebhook().getTimeout(), Duration.ofSeconds(5)));
        int maxRetries = Math.max(0, properties.getWebhook().getMaxRetries());
        if (maxRetries > 0) {
            Duration backoff = validDuration(properties.getWebhook().getRetryBackoff(), Duration.ofMillis(500));
            request = request.retryWhen(Retry.backoff(maxRetries, backoff)
                    .maxBackoff(backoff.multipliedBy(4))
                    .jitter(0.25)
                    .filter(this::retryable));
        }
        return request
                .doOnSuccess(ignored -> log.info("客服离线 Webhook 发送成功。eventId={}, targetHost={}",
                        notification.payload().eventId(), target.getHost()))
                .onErrorResume(error -> {
                    log.warn("客服离线 Webhook 发送失败，已降级跳过。eventId={}, targetHost={}, error={}",
                            notification.payload().eventId(), target.getHost(), error.getMessage());
                    return Mono.empty();
                });
    }

    private Map<String, String> headers(CustomerOfflineNotification notification, String timestamp, byte[] body) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(HttpHeaders.ACCEPT, "application/json");
        headers.put(HEADER_EVENT, notification.payload().event());
        headers.put(HEADER_DELIVERY, notification.payload().eventId());
        headers.put(HEADER_TIMESTAMP, timestamp);
        String signature = signer.sign(properties.getWebhook().getSecret(), timestamp, body);
        if (StringUtils.isNotBlank(signature)) {
            headers.put(HEADER_SIGNATURE, signature);
        }
        return headers;
    }

    private List<URI> normalizedTargets() {
        List<URI> configured = properties.getWebhook().getUrls();
        if (configured == null || configured.isEmpty()) {
            return List.of();
        }
        return configured.stream()
                .filter(this::allowedTarget)
                .distinct()
                .toList();
    }

    private boolean allowedTarget(URI target) {
        if (target == null || target.getHost() == null || target.getUserInfo() != null) {
            return false;
        }
        if ("https".equalsIgnoreCase(target.getScheme())) {
            return true;
        }
        return properties.getWebhook().isAllowInsecureHttp() && "http".equalsIgnoreCase(target.getScheme());
    }

    private boolean retryable(Throwable error) {
        if (error instanceof WebClientResponseException responseError) {
            return responseError.getStatusCode().is5xxServerError()
                    || responseError.getStatusCode().value() == HttpStatus.TOO_MANY_REQUESTS.value();
        }
        return error instanceof WebClientRequestException || error instanceof TimeoutException;
    }

    private Duration validDuration(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }
}

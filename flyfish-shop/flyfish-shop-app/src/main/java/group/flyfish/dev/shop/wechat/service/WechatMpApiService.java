package group.flyfish.dev.shop.wechat.service;

import group.flyfish.dev.shop.wechat.client.WechatAccessTokenResponse;
import group.flyfish.dev.shop.wechat.client.WechatCustomMessageRequest;
import group.flyfish.dev.shop.wechat.client.WechatKfOnlineListResponse;
import group.flyfish.dev.shop.wechat.client.WechatMpClient;
import group.flyfish.dev.shop.wechat.client.WechatQrCodeRequest;
import group.flyfish.dev.shop.wechat.client.WechatQrCodeTicketResponse;
import group.flyfish.dev.shop.wechat.client.WechatUserInfoResponse;
import group.flyfish.dev.shop.wechat.config.WechatMpProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 微信公众号远程 API 服务。
 *
 * <p>该服务负责 access_token 缓存和微信 API 结果校验，业务层只拿
 * Reactor {@link Mono}，不再直接拼 URL 或处理 token 生命周期。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WechatMpApiService {

    private static final String GRANT_TYPE_CLIENT_CREDENTIAL = "client_credential";

    private final WechatMpClient client;

    private final WechatMpProperties properties;

    private final AtomicReference<CachedToken> cachedToken = new AtomicReference<>();

    public Mono<WechatQrCodeTicketResponse> createTemporaryQrCode(String scene, int expireSeconds) {
        return accessToken()
                .flatMap(accessToken -> client.createQrCode(accessToken,
                        WechatQrCodeRequest.temporaryStringScene(scene, expireSeconds)))
                .map(response -> {
                    response.assertSuccess();
                    return response;
                });
    }

    public String qrCodePictureUrl(String ticket) {
        String baseUrl = StringUtils.removeEnd(StringUtils.defaultIfBlank(
                properties.getMpBaseUrl(), "https://mp.weixin.qq.com"), "/");
        return baseUrl + "/cgi-bin/showqrcode?ticket=" + URLEncoder.encode(ticket, StandardCharsets.UTF_8);
    }

    public Mono<WechatUserInfoResponse> userInfo(String openid) {
        return accessToken()
                .flatMap(accessToken -> client.getUserInfo(accessToken, openid, "zh_CN"))
                .map(response -> {
                    response.assertSuccess();
                    return response;
                });
    }

    public Mono<Boolean> hasOnlineKefu() {
        return accessToken()
                .flatMap(client::listOnlineKefu)
                .map(response -> {
                    response.assertSuccess();
                    return response.hasOnlineKefu();
                })
                .onErrorResume(e -> {
                    log.warn("查询微信客服在线状态失败，按无在线客服处理：{}", e.getMessage());
                    return Mono.just(false);
                });
    }

    public Mono<String> customerSupportImageMediaId() {
        if (StringUtils.isNotBlank(properties.getCustomerServiceMediaId())) {
            return Mono.just(properties.getCustomerServiceMediaId());
        }
        return Mono.error(new IllegalStateException("未配置客服二维码微信素材 media_id"));
    }

    public Mono<Void> sendKefuText(String openid, String content) {
        if (StringUtils.isAnyBlank(openid, content)) {
            return Mono.empty();
        }
        return accessToken()
                .flatMap(accessToken -> client.sendCustomMessage(accessToken,
                        WechatCustomMessageRequest.text(openid, content)))
                .doOnNext(response -> {
                    response.assertSuccess();
                    log.info("客服文字指引发送成功。openid={}", openid);
                })
                .then();
    }

    private Mono<String> accessToken() {
        CachedToken token = cachedToken.get();
        if (token != null && token.valid()) {
            return Mono.just(token.value());
        }
        return client.getAccessToken(GRANT_TYPE_CLIENT_CREDENTIAL, properties.getAppId(), properties.getSecret())
                .map(this::cacheAccessToken);
    }

    private String cacheAccessToken(WechatAccessTokenResponse response) {
        response.assertSuccess();
        String accessToken = response.getAccessToken();
        if (StringUtils.isBlank(accessToken)) {
            throw new IllegalStateException("微信 access_token 为空");
        }
        long expiresIn = response.getExpiresIn() > 0 ? response.getExpiresIn() : 7200;
        cachedToken.set(new CachedToken(accessToken, Instant.now().plusSeconds(Math.max(60, expiresIn - 120))));
        return accessToken;
    }

    private record CachedToken(String value, Instant expireAt) {
        boolean valid() {
            return StringUtils.isNotBlank(value) && expireAt != null && Instant.now().isBefore(expireAt);
        }
    }
}

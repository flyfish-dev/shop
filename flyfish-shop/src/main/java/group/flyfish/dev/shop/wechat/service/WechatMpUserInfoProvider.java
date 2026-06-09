package group.flyfish.dev.shop.wechat.service;

import group.flyfish.dev.shop.wechat.client.WechatUserInfoResponse;
import group.flyfish.dev.wechat.service.WechatLoginUserInfoProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 基于微信公众号 API 的登录用户资料提供器。
 */
@Service
@RequiredArgsConstructor
public class WechatMpUserInfoProvider implements WechatLoginUserInfoProvider {

    private final WechatMpApiService wechatMpApiService;

    @Override
    public Mono<Map<String, Object>> userInfo(String openid) {
        return wechatMpApiService.userInfo(openid)
                .map(WechatUserInfoResponse::toSnapshot);
    }
}

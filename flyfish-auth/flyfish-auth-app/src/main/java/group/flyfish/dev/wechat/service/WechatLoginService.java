package group.flyfish.dev.wechat.service;

import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.user.domain.UserToken;
import group.flyfish.dev.user.service.PortalUserService;
import group.flyfish.dev.wechat.bean.WechatLoginResultVo;
import group.flyfish.dev.wechat.bean.WechatLoginSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 微信登录会话服务。
 *
 * <p>二维码轮询登录、公众号验证码登录和公众号快捷链接登录最终都要做同一件事：
 * 校验会话、尽量补全微信用户资料、注册或登录门户用户、消费一次性登录码。该服务集中维护这段流程，
 * 避免多个 Controller 复制资料补全与清理逻辑。</p>
 *
 * @author wangyu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WechatLoginService {

    private final ObjectProvider<WechatLoginUserInfoProvider> userInfoProvider;

    private final WechatLoginStorage wechatLoginStorage;

    private final PortalUserService portalUserService;

    public Mono<WechatLoginResultVo> pollLogin(String scene) {
        WechatLoginSession session = wechatLoginStorage.get(scene);
        if (session == null) {
            return Mono.just(WechatLoginResultVo.expired(scene));
        }
        if (!session.isSuccess()) {
            return Mono.just(WechatLoginResultVo.waiting(session));
        }
        return consume(session)
                .map(token -> WechatLoginResultVo.confirmed(session, token));
    }

    public Mono<UserToken> consumeConfirmedSession(String scene) {
        WechatLoginSession session = wechatLoginStorage.get(scene);
        if (session == null) {
            return Mono.error(new BusinessException("WECHAT_LOGIN_EXPIRED", "登录链接已失效"));
        }
        if (!session.isSuccess()) {
            return Mono.error(new BusinessException("WECHAT_LOGIN_WAITING", "登录链接尚未确认"));
        }
        return consume(session);
    }

    private Mono<UserToken> consume(WechatLoginSession session) {
        return enrichWechatUser(session)
                .flatMap(enrichedSession -> {
                    if (enrichedSession.isBinding()) {
                        return portalUserService.bindAuthorization(enrichedSession, enrichedSession.getBindUserId());
                    }
                    return portalUserService.registerOrLogin(enrichedSession);
                })
                .doOnNext(token -> wechatLoginStorage.clear(session.getScene()));
    }

    private Mono<WechatLoginSession> enrichWechatUser(WechatLoginSession session) {
        if (session.getUserInfo() != null && !session.getUserInfo().isEmpty()) {
            return Mono.just(session);
        }
        WechatLoginUserInfoProvider provider = userInfoProvider.getIfAvailable();
        if (provider == null) {
            return Mono.just(session);
        }
        return provider.userInfo(session.getOpenid())
                .doOnNext(session::mergeUserInfo)
                .thenReturn(session)
                .onErrorResume(e -> {
                    // 公众号验证码登录只能稳定拿到 openid；昵称头像等字段由微信接口权限和隐私策略决定。
                    log.warn("微信用户资料补全失败，继续使用 openid 完成登录：{}", e.getMessage());
                    return Mono.just(session);
                });
    }
}

package group.flyfish.dev.oauth.service;

import lombok.extern.slf4j.Slf4j;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.profile.ProfileManager;
import org.pac4j.springframework.context.SpringWebfluxSessionStore;
import org.pac4j.springframework.context.SpringWebfluxWebContext;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;

/**
 * OAuth 浏览器会话清理服务。
 *
 * <p>飞鱼自己的登录态由 JWT 承载，pac4j 只负责第三方 OAuth 握手。OAuth 握手完成后如果继续
 * 保留 pac4j 的 profile，用户退出飞鱼账号后再次点击 GitHub/Gitee/Gitea 登录时，pac4j 会误以为
 * 当前浏览器已经完成 OAuth 授权，直接放行到兜底路由，最终表现为“跳到首页但没有飞鱼登录态”。</p>
 *
 * <p>因此这里把 OAuth profile 与 WebSession 清理集中管理，避免退出登录和 OAuth 回调各自手写
 * session 细节。</p>
 */
@Slf4j
@Service
public class OAuthSessionService {

    /**
     * 清理 pac4j 保存的第三方 profile，但保留当前 WebSession 中的其他业务数据。
     */
    public void clearProfiles(ServerWebExchange exchange) {
        try {
            SpringWebfluxWebContext context = new SpringWebfluxWebContext(exchange);
            SessionStore sessionStore = new SpringWebfluxSessionStore(exchange);
            new ProfileManager(context, sessionStore).removeProfiles();
        } catch (RuntimeException e) {
            log.warn("清理 OAuth profile 失败：{}", e.getMessage());
        }
    }

    /**
     * 退出登录时销毁整个 WebSession，确保 OAuth state/profile 与临时绑定数据都不会残留。
     */
    public Mono<Void> invalidate(ServerWebExchange exchange) {
        clearProfiles(exchange);
        return exchange.getSession()
                .flatMap(WebSession::invalidate)
                .onErrorResume(e -> {
                    log.warn("销毁 OAuth WebSession 失败：{}", e.getMessage());
                    return Mono.empty();
                });
    }
}

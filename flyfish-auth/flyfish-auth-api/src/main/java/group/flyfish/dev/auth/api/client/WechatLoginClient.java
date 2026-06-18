package group.flyfish.dev.auth.api.client;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import reactor.core.publisher.Mono;

/**
 * 认证服务微信登录会话内部契约。
 */
@HttpExchange("/internal/auth/wechat/login-sessions")
public interface WechatLoginClient {

    @GetExchange("/{scene}/exists")
    Mono<Boolean> exists(@PathVariable("scene") String scene);

    @PostExchange("/{scene}/scan")
    Mono<Void> scan(@PathVariable("scene") String scene, @RequestParam("openid") String openid);

    @PostExchange("/{scene}/confirm")
    Mono<Void> confirm(@PathVariable("scene") String scene, @RequestParam("openid") String openid);

    @PostExchange("/confirmed")
    Mono<String> createConfirmed(@RequestParam("openid") String openid,
                                 @RequestParam("expireSeconds") int expireSeconds);
}

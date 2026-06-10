package group.flyfish.dev.auth.internal;

import group.flyfish.dev.wechat.service.WechatLoginStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 认证服务微信登录会话内部接口。
 */
@RestController
@RequestMapping("/internal/auth/wechat/login-sessions")
@RequiredArgsConstructor
public class AuthInternalWechatLoginController {

    private final WechatLoginStorage wechatLoginStorage;

    @GetMapping("/{scene}/exists")
    public Mono<Boolean> exists(@PathVariable String scene) {
        return Mono.just(wechatLoginStorage.get(scene) != null);
    }

    @PostMapping("/{scene}/scan")
    public Mono<Void> scan(@PathVariable String scene, @RequestParam String openid) {
        wechatLoginStorage.scan(scene, openid);
        return Mono.empty();
    }

    @PostMapping("/{scene}/confirm")
    public Mono<Void> confirm(@PathVariable String scene, @RequestParam String openid) {
        wechatLoginStorage.put(scene, openid);
        return Mono.empty();
    }

    @PostMapping("/confirmed")
    public Mono<String> createConfirmed(@RequestParam String openid,
                                        @RequestParam(defaultValue = "0") int expireSeconds) {
        String scene = wechatLoginStorage.newId();
        var session = wechatLoginStorage.put(scene, openid);
        if (session != null) {
            session.setExpireSeconds(expireSeconds);
        }
        return Mono.just(scene);
    }
}

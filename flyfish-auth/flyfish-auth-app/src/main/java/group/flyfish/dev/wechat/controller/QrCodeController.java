package group.flyfish.dev.wechat.controller;

import group.flyfish.dev.annotations.user.CurrentUser;
import group.flyfish.dev.common.bean.Result;
import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.auth.api.user.PortalUserVo;
import group.flyfish.dev.user.service.impl.TokenProvider;
import group.flyfish.dev.wechat.bean.WechatLoginDto;
import group.flyfish.dev.wechat.bean.WechatLoginResultVo;
import group.flyfish.dev.wechat.service.WechatLoginQrCodeProvider;
import group.flyfish.dev.wechat.service.WechatLoginService;
import group.flyfish.dev.wechat.service.WechatLoginStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 微信二维码控制器
 *
 * @author wangyu
 */
@RestController
@RequestMapping("wx/qr-codes")
@RequiredArgsConstructor
@Slf4j
public class QrCodeController {

    private final ObjectProvider<WechatLoginQrCodeProvider> qrCodeProvider;

    private final WechatLoginStorage wechatLoginStorage;

    private final WechatLoginService wechatLoginService;

    private final TokenProvider tokenProvider;

    /**
     * 获取二维码
     * 获取一个临时二维码，有效期3分钟
     *
     * @return 结果
     */
    @GetMapping
    public Mono<Result<WechatLoginDto>> getQrCode(@RequestParam(name = "mode", required = false) String mode,
                                                  @CurrentUser PortalUserVo user) {
        Long bindUserId = resolveBindUserId(mode, user);
        String scene = wechatLoginStorage.newId(bindUserId);
        return getServiceUrl(scene)
                .map(Result::ok)
                .onErrorResume(e -> {
                    log.warn("微信登录二维码获取失败，降级为验证码回复模式：{}", e.getMessage());
                    return Mono.just(Result.ok(getSimpleUrl(scene)));
                });
    }

    /**
     * 轮询登录接口
     *
     * @param scene 场景值
     * @return 结果
     */
    @GetMapping("{code}")
    public Mono<Result<WechatLoginResultVo>> attemptLogin(@PathVariable("code") String scene,
                                                          ServerWebExchange exchange) {
        return wechatLoginService.pollLogin(scene)
                .doOnNext(result -> writeLoginCookie(exchange, result))
                .map(Result::ok);
    }

    private void writeLoginCookie(ServerWebExchange exchange, WechatLoginResultVo result) {
        if (result == null || result.getToken() == null || result.getExpire() == null) {
            return;
        }
        tokenProvider.writeTokenCookie(exchange, result.getToken(), result.getExpire());
    }

    private Long resolveBindUserId(String mode, PortalUserVo user) {
        if (!"bind".equalsIgnoreCase(mode)) {
            return null;
        }
        if (user == null || user.getId() == null || user.getId() <= 0) {
            throw new BusinessException("USER_REQUIRED", "请先登录后再绑定微信");
        }
        return user.getId();
    }

    private WechatLoginDto getSimpleUrl(String scene) {
        return WechatLoginDto.of(true, "/qr.png", scene);
    }

    private Mono<WechatLoginDto> getServiceUrl(String scene) {
        WechatLoginQrCodeProvider provider = qrCodeProvider.getIfAvailable();
        if (provider == null) {
            return Mono.just(getSimpleUrl(scene));
        }
        return provider.createLoginQrCode(scene);
    }
}

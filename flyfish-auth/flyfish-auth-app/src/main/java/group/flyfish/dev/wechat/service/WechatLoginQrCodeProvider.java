package group.flyfish.dev.wechat.service;

import group.flyfish.dev.wechat.bean.WechatLoginDto;
import reactor.core.publisher.Mono;

/**
 * 微信登录二维码提供器。
 *
 * <p>认证模块只维护登录会话和接口契约，具体公众号二维码生成能力由业务模块按需提供。
 * 未提供时，登录页会使用验证码回复模式。</p>
 */
public interface WechatLoginQrCodeProvider {

    Mono<WechatLoginDto> createLoginQrCode(String scene);
}

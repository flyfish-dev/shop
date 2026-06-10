package group.flyfish.dev.wechat.service;

import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 微信登录用户资料提供器。
 *
 * <p>业务模块可以接入公众号 API 补全昵称、头像等资料；认证模块在没有实现时仍可仅使用 openid 完成登录。</p>
 */
public interface WechatLoginUserInfoProvider {

    Mono<Map<String, Object>> userInfo(String openid);
}

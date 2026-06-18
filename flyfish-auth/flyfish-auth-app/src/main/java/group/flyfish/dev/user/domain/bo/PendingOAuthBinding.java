package group.flyfish.dev.user.domain.bo;

import group.flyfish.dev.auth.api.user.OAuthType;

import java.io.Serializable;
import java.time.Instant;

/**
 * 等待用户二次确认的第三方账号绑定。
 *
 * <p>该对象只保存在当前浏览器会话中，用来承接 OAuth 回调已经拿到的平台账号资料。只有用户在确认页主动
 * 点击“确认换绑”后，系统才会真正移动第三方授权关系。</p>
 */
public record PendingOAuthBinding(Long userId, OAuthType type, String openid, String userInfo,
                                  String redirect, Instant expireTime) implements Serializable {

    private static final long serialVersionUID = 1L;

    public boolean isExpired() {
        return expireTime == null || expireTime.isBefore(Instant.now());
    }
}

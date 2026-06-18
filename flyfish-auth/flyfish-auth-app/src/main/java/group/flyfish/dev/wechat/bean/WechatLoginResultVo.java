package group.flyfish.dev.wechat.bean;

import group.flyfish.dev.user.domain.UserToken;
import lombok.Data;

import java.util.Date;

@Data
public class WechatLoginResultVo {

    private WechatLoginStatus status;

    private String statusText;

    private String sceneId;

    private String token;

    private Date expire;

    public static WechatLoginResultVo waiting(WechatLoginSession session) {
        WechatLoginResultVo result = new WechatLoginResultVo();
        result.setSceneId(session.getScene());
        if (session.isScanned()) {
            result.setStatus(WechatLoginStatus.SCANNED);
            result.setStatusText("已扫码，请在公众号回复验证码确认登录");
        } else {
            result.setStatus(WechatLoginStatus.WAITING);
            result.setStatusText("等待扫码或回复验证码");
        }
        return result;
    }

    public static WechatLoginResultVo confirmed(WechatLoginSession session, UserToken token) {
        WechatLoginResultVo result = new WechatLoginResultVo();
        result.setSceneId(session.getScene());
        result.setStatus(WechatLoginStatus.CONFIRMED);
        result.setStatusText("登录成功");
        result.setToken(token.getToken());
        result.setExpire(token.getExpire());
        return result;
    }

    public static WechatLoginResultVo expired(String scene) {
        WechatLoginResultVo result = new WechatLoginResultVo();
        result.setSceneId(scene);
        result.setStatus(WechatLoginStatus.EXPIRED);
        result.setStatusText("二维码已过期，请重新获取");
        return result;
    }
}

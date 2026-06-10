package group.flyfish.dev.wechat.bean;

import com.fasterxml.jackson.annotation.JsonIgnore;
import group.flyfish.dev.wechat.contants.WxScanScenes;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微信登录会话
 *
 * @author wangyu
 */
@Data
@RequiredArgsConstructor
public class WechatLoginSession {

    private final String scene;

    private final Long bindUserId;

    private String scannedOpenid = "";

    private String openid = "";

    private final Date createTime = new Date();

    @JsonIgnore
    private int expireSeconds = WxScanScenes.QR_CODE_EXPIRE;

    private LocalDateTime scannedTime;

    private LocalDateTime confirmedTime;

    private Map<String, Object> userInfo = new LinkedHashMap<>();

    public WechatLoginSession(String scene) {
        this(scene, null);
    }

    public boolean isBinding() {
        return bindUserId != null && bindUserId > 0;
    }

    public boolean isScanned() {
        return StringUtils.isNotBlank(scannedOpenid) || isSuccess();
    }

    public boolean isSuccess() {
        return StringUtils.isNotBlank(openid);
    }

    public void markScanned(String openid) {
        if (StringUtils.isBlank(openid)) {
            return;
        }
        this.scannedOpenid = openid;
        if (scannedTime == null) {
            this.scannedTime = LocalDateTime.now();
        }
    }

    public void confirm(String openid) {
        if (StringUtils.isBlank(openid)) {
            return;
        }
        markScanned(openid);
        this.openid = openid;
        this.confirmedTime = LocalDateTime.now();
    }

    public void mergeUserInfo(Map<String, Object> userInfo) {
        if (userInfo == null || userInfo.isEmpty()) {
            return;
        }
        this.userInfo.putAll(userInfo);
    }

    public void setExpireSeconds(int expireSeconds) {
        if (expireSeconds <= 0) {
            this.expireSeconds = WxScanScenes.QR_CODE_EXPIRE;
            return;
        }
        this.expireSeconds = expireSeconds;
    }

    @JsonIgnore
    public boolean isExpired() {
        return new Date().getTime() - createTime.getTime() > expireSeconds * 1000L;
    }
}

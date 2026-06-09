package group.flyfish.dev.shop.wechat.service.impl;

import group.flyfish.dev.wechat.bean.WechatLoginSession;
import group.flyfish.dev.wechat.contants.WxScanScenes;
import group.flyfish.dev.wechat.service.WechatLoginStorage;
import group.flyfish.dev.shop.wechat.service.WechatService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class WechatServiceImpl implements WechatService {

    private static final Pattern LOGIN_CODE_PATTERN = Pattern.compile("(?<!\\d)(\\d{6})(?!\\d)");

    private final WechatLoginStorage wechatLoginStorage;

    /**
     * 判断是否是session code
     *
     * @param msg 消息
     * @return 结果
     */
    @Override
    public boolean isSessionCode(String msg) {
        String scene = normalizeSessionCode(msg);
        return StringUtils.isNotBlank(scene) && null != wechatLoginStorage.get(scene);
    }

    @Override
    public String normalizeSessionCode(String raw) {
        String value = normalizeDigits(StringUtils.trimToEmpty(raw));
        if (StringUtils.isBlank(value)) {
            return "";
        }
        if (value.startsWith(WxScanScenes.SCAN_EVENT_PREFIX)) {
            value = StringUtils.substringAfter(value, WxScanScenes.SCAN_EVENT_PREFIX);
        }
        if (value.startsWith(WxScanScenes.LOGIN)) {
            value = StringUtils.substringAfter(value, WxScanScenes.LOGIN);
        }
        value = StringUtils.deleteWhitespace(value);
        if (value.matches("\\d{6}")) {
            return value;
        }
        Matcher matcher = LOGIN_CODE_PATTERN.matcher(value);
        return matcher.find() ? matcher.group(1) : "";
    }

    @Override
    public void markScanned(String scene, String openid) {
        String normalizedScene = normalizeSessionCode(scene);
        if (StringUtils.isNotBlank(normalizedScene)) {
            wechatLoginStorage.scan(normalizedScene, openid);
        }
    }

    @Override
    public String createConfirmedSession(String openid) {
        return createConfirmedSession(openid, 0);
    }

    @Override
    public String createConfirmedSession(String openid, int expireSeconds) {
        if (StringUtils.isBlank(openid)) {
            return "";
        }
        String scene = wechatLoginStorage.newId();
        WechatLoginSession session = wechatLoginStorage.put(scene, openid);
        if (session != null) {
            session.setExpireSeconds(expireSeconds);
        }
        return scene;
    }

    /**
     * 更新session
     *
     * @param scene  场景值
     * @param openid 用户标识
     */
    @Override
    public void updateSession(String scene, String openid) {
        String normalizedScene = normalizeSessionCode(scene);
        if (StringUtils.isNotBlank(normalizedScene)) {
            wechatLoginStorage.put(normalizedScene, openid);
        }
    }

    private String normalizeDigits(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (char ch : value.toCharArray()) {
            if (ch >= '０' && ch <= '９') {
                builder.append((char) ('0' + ch - '０'));
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }
}

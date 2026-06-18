package group.flyfish.dev.auth.api.client;

/**
 * 微信扫码登录场景值约定。
 */
public final class WxScanScenes {

    public static final String LOGIN = "login_";

    public static final String SCAN_EVENT_PREFIX = "qrscene_";

    public static final int QR_CODE_EXPIRE = 180;

    private WxScanScenes() {
    }
}

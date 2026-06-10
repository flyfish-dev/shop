package group.flyfish.dev.wechat.contants;

/**
 * 微信扫码场景
 */
public interface WxScanScenes {

    String LOGIN = "login:";

    int QR_CODE_EXPIRE = 3 * 60;

    String SCAN_EVENT_PREFIX = "qrscene_";
}

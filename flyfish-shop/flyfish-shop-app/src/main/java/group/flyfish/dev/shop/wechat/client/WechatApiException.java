package group.flyfish.dev.shop.wechat.client;

/**
 * 微信接口返回业务错误时抛出的轻量异常。
 */
public class WechatApiException extends RuntimeException {

    public WechatApiException(Integer errCode, String errMsg) {
        super("微信接口调用失败，errcode=" + errCode + ", errmsg=" + errMsg);
    }
}

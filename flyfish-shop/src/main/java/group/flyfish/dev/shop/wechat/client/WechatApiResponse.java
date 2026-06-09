package group.flyfish.dev.shop.wechat.client;

public interface WechatApiResponse {

    Integer getErrcode();

    String getErrmsg();

    default boolean failed() {
        return getErrcode() != null && getErrcode() != 0;
    }

    default void assertSuccess() {
        if (failed()) {
            throw new WechatApiException(getErrcode(), getErrmsg());
        }
    }
}

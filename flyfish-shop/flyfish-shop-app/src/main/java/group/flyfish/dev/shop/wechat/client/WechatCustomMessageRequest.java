package group.flyfish.dev.shop.wechat.client;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 微信客服消息发送请求。
 */
public record WechatCustomMessageRequest(@JsonProperty("touser") String toUser,
                                         @JsonProperty("msgtype") String messageType,
                                         Text text) {

    private static final String MESSAGE_TYPE_TEXT = "text";

    public static WechatCustomMessageRequest text(String openid, String content) {
        return new WechatCustomMessageRequest(openid, MESSAGE_TYPE_TEXT, new Text(content));
    }

    public record Text(String content) {
    }
}

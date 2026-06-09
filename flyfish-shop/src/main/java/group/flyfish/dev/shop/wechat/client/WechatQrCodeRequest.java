package group.flyfish.dev.shop.wechat.client;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * 创建公众号带参数临时二维码的请求体。
 */
public record WechatQrCodeRequest(
        @JsonProperty("expire_seconds") int expireSeconds,
        @JsonProperty("action_name") String actionName,
        @JsonProperty("action_info") Map<String, Object> actionInfo) {

    public static WechatQrCodeRequest temporaryStringScene(String scene, int expireSeconds) {
        return new WechatQrCodeRequest(expireSeconds, "QR_STR_SCENE",
                Map.of("scene", Map.of("scene_str", scene)));
    }
}

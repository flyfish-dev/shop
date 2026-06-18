package group.flyfish.dev.shop.wechat.protocol;

import lombok.Builder;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;

/**
 * 公众号推送给服务端的轻量消息模型。
 */
@Value
@Builder
public class WechatInboundMessage {

    public static final String MSG_TYPE_TEXT = "text";
    public static final String MSG_TYPE_EVENT = "event";
    public static final String MSG_TYPE_LOCATION = "location";

    public static final String EVENT_SUBSCRIBE = "subscribe";
    public static final String EVENT_UNSUBSCRIBE = "unsubscribe";
    public static final String EVENT_SCAN = "SCAN";
    public static final String EVENT_LOCATION = "LOCATION";
    public static final String EVENT_CLICK = "CLICK";
    public static final String EVENT_VIEW = "VIEW";

    String toUserName;
    String fromUserName;
    String createTime;
    String msgType;
    String content;
    String msgId;
    String picUrl;
    String mediaId;
    String event;
    String eventKey;
    String latitude;
    String longitude;
    String precision;

    public boolean isText() {
        return MSG_TYPE_TEXT.equalsIgnoreCase(msgType);
    }

    public boolean isEvent() {
        return MSG_TYPE_EVENT.equalsIgnoreCase(msgType);
    }

    public boolean isLocationMessage() {
        return MSG_TYPE_LOCATION.equalsIgnoreCase(msgType);
    }

    public boolean eventIs(String expected) {
        return StringUtils.equalsIgnoreCase(event, expected);
    }
}

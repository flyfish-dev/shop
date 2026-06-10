package group.flyfish.dev.shop.wechat.protocol;

/**
 * 服务端被动回复给公众号用户的轻量消息模型。
 */
public record WechatReplyMessage(Type type, String toUserName, String fromUserName, String content) {

    public enum Type {
        TEXT,
        IMAGE,
        TRANSFER_CUSTOMER_SERVICE
    }

    public static WechatReplyMessage text(WechatInboundMessage inbound, String content) {
        return new WechatReplyMessage(Type.TEXT, inbound.getFromUserName(), inbound.getToUserName(), content);
    }

    public static WechatReplyMessage image(WechatInboundMessage inbound, String mediaId) {
        return new WechatReplyMessage(Type.IMAGE, inbound.getFromUserName(), inbound.getToUserName(), mediaId);
    }

    public static WechatReplyMessage transferCustomerService(WechatInboundMessage inbound) {
        return new WechatReplyMessage(Type.TRANSFER_CUSTOMER_SERVICE,
                inbound.getFromUserName(), inbound.getToUserName(), null);
    }
}

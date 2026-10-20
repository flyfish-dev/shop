package group.flyfish.dev.xianyu.top;

public enum TopOperation {
    TIME("taobao.time.get", null, false),
    AUTH_TOKEN("taobao.top.auth.token.create", null, true),
    USER_INFO("alibaba.idle.goosefish.user.info.query", null, false),
    ITEM_PUBLISH("alibaba.idle.isv.item.publish", "item_param", true),
    ITEM_EDIT("alibaba.idle.isv.item.edit", "param", true),
    ITEM_QUERY("alibaba.idle.isv.item.query", "param", false),
    ITEM_DOWNSHELF("alibaba.idle.isv.item.downshelf", "param", true),
    MESSAGE_BINDING_QUERY("alibaba.idle.user.permit.query", null, false),
    ITEM_LIST("alibaba.idle.item.user.publishitems", "param_item_page_query", false),
    MEDIA_UPLOAD("alibaba.idle.isv.media.upload", null, true),
    ORDER_CREATE("alibaba.idle.isv.goosefish.order.create", "order_create_request", true),
    ORDER_QUERY("alibaba.idle.isv.order.query", "param_appraise_isv_order_query", false),
    ORDER_CLOSE("alibaba.idle.isv.order.close", "isv_appraise_isv_order_close_dto", true),
    VIRTUAL_DELIVERY("alibaba.idle.isv.goosefish.virtual.delivery", "idle_open_isv_virtual_delivery_param", true),
    DUMMY_CONSIGN("alibaba.idle.order.dummy.consign", "param", true),
    REFUND_QUERY("alibaba.idle.isv.refund.query", "param_appraise_isv_order_query", false),
    PART_REFUND("alibaba.idle.isv.order.part.refund", "top_part_refund_param", true),
    AFTER_SEND_REFUND("alibaba.idle.isv.order.after.send.refund", "top_refund_param", true),
    DEAL_REFUND("alibaba.idle.isv.order.dealrefund", "param_appraise_isv_refund_request", true),
    MESSAGE_CONSUME("taobao.tmc.messages.consume", null, false),
    MESSAGE_CONFIRM("taobao.tmc.messages.confirm", null, true);

    public final String method;
    public final String requestKey;
    public final boolean mutation;
    TopOperation(String method, String requestKey, boolean mutation) {
        this.method = method;
        this.requestKey = requestKey;
        this.mutation = mutation;
    }
}

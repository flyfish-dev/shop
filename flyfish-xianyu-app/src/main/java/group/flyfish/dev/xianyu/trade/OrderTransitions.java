package group.flyfish.dev.xianyu.trade;

import group.flyfish.dev.xianyu.store.XianyuModels.Order;
import java.util.Map;

public final class OrderTransitions {
    private OrderTransitions() { }
    public static Order observe(Order old, int state, long amount, Map<String,Object> snapshot) {
        if (state<0 || state>6 || state==0) throw new IllegalArgumentException("Unknown order state");
        if (amount!=old.amountCents()) throw new IllegalStateException("PAYMENT_AMOUNT_MISMATCH");
        if (old.platformState()==5 && state!=old.platformState())
            return old;
        if (state<old.platformState() && old.platformState()<5) return old;
        String refund=state==5 ? "REFUNDED" : old.refundState();
        return new Order(old.key(),old.seller(),old.localOrderNo(),old.remoteOrderId(),old.buyerSubject(),old.channel(),
                amount,"CREATED",state,refund,state==5 ? amount : old.refundCents(),old.fulfillmentState(),
                state==3 || state==4 ? "CONFIRMED" : old.shipmentState(),snapshot,old.revision(),old.expiresAt());
    }
    public static Order change(Order o, String creation, String remote, String fulfillment, String shipment,
                               String refund, long refunded, Map<String,Object> snapshot) {
        return new Order(o.key(),o.seller(),o.localOrderNo(),remote,o.buyerSubject(),o.channel(),o.amountCents(),
                creation,o.platformState(),refund,refunded,fulfillment,shipment,snapshot,o.revision(),o.expiresAt());
    }
}

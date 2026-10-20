package group.flyfish.dev.shop.repository;

import group.flyfish.dev.common.repository.DefaultReactiveRepository;
import group.flyfish.dev.shop.domain.po.ShopOrder;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.relational.core.query.Criteria;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * 店铺订单仓库
 *
 * @author wangyu
 */
public interface ShopOrderRepository extends DefaultReactiveRepository<ShopOrder> {

    default Mono<ShopOrder> findByChannelOrderKey(String key) { return findOneBy("channel_order_key", key); }

    @Modifying
    @Query("""
        UPDATE shop_order SET paid_time=CURRENT_TIMESTAMP,status='PAID',delivery_status='WAITING',
          transaction_code=:transaction,expire_time=NULL,update_time=CURRENT_TIMESTAMP
        WHERE order_no=:orderNo AND payment_provider='xianyu' AND paid_time IS NULL
          AND status IN ('PENDING','PAYING','CLOSED') AND amount=:amount AND currency='CNY'
        """)
    Mono<Integer> confirmXianyuPaid(String orderNo, java.math.BigDecimal amount, String transaction);

    @Modifying
    @Query("""
        UPDATE shop_order SET delivery_status='PROCESSING',update_time=CURRENT_TIMESTAMP
        WHERE order_no=:orderNo AND payment_provider='xianyu' AND paid_time IS NOT NULL
          AND delivery_status IN ('WAITING','FAILED')
          AND (refund_state IS NULL OR refund_state IN ('NONE','CLOSED','REJECTED'))
          AND COALESCE(refund_amount,0)=0
        """)
    Mono<Integer> claimXianyuDelivery(String orderNo);

    @Modifying
    @Query("""
        UPDATE shop_order SET status=:state,delivery_status=:delivery,delivery_message=:message,update_time=CURRENT_TIMESTAMP
        WHERE order_no=:orderNo AND payment_provider='xianyu' AND paid_time IS NOT NULL
          AND (:requireActive = false OR ((refund_state IS NULL OR refund_state IN ('NONE','CLOSED','REJECTED')) AND COALESCE(refund_amount,0)=0))
        """)
    Mono<Integer> updateXianyuDelivery(String orderNo,String state,String delivery,String message,boolean requireActive);

    @Modifying
    @Query("""
        UPDATE shop_order SET
          refund_state=CASE WHEN :revision>COALESCE(channel_refund_revision,0)
            OR (:revision=COALESCE(channel_refund_revision,0)
              AND CASE :state WHEN 'REFUNDED' THEN 4 WHEN 'PARTIAL' THEN 3 WHEN 'PROCESSING' THEN 2 WHEN 'PENDING' THEN 1 ELSE 0 END
                >= CASE refund_state WHEN 'REFUNDED' THEN 4 WHEN 'PARTIAL' THEN 3 WHEN 'PROCESSING' THEN 2 WHEN 'PENDING' THEN 1 ELSE 0 END)
            THEN :state ELSE refund_state END,
          refund_amount=GREATEST(COALESCE(refund_amount,0),:amount),
          channel_refund_revision=GREATEST(COALESCE(channel_refund_revision,0),:revision)
        WHERE order_no=:orderNo AND channel_order_key=:key AND payment_provider='xianyu'
          AND :revision>0 AND :amount>=0 AND :amount<=amount
        """)
    Mono<Integer> projectXianyuRefund(String orderNo,String key,String state,java.math.BigDecimal amount,long revision);

    default Mono<ShopOrder> findByOrderNo(String orderNo) {
        return findOneBy("order_no", orderNo);
    }

    default Flux<ShopOrder> findAllByBuyerIdOrderByCreateTimeDesc(Long buyerId) {
        return findAllBy(Criteria.where("buyer_id").is(buyerId), orderByCreateTimeDesc());
    }

    default Flux<ShopOrder> findAllByBuyerIdAndItemIdOrderByCreateTimeDesc(Long buyerId, Long itemId) {
        return findAllBy(Criteria.where("buyer_id").is(buyerId).and("item_id").is(itemId), orderByCreateTimeDesc());
    }

    /**
     * 查询用户已经完成支付的订单，用于自动开通类商品的重复购买拦截。
     * <p>自动交付失败后订单状态可能变成 FAILED，但只要存在支付时间，就说明用户已经完成购买；
     * 这里仍然要纳入拦截，避免用户因为重复开通失败而再次付款。</p>
     * <p>状态直接使用数据库字符串，避免 native/R2DBC 下枚举参数编码差异。</p>
     */
    @Query("""
            SELECT *
            FROM shop_order
            WHERE is_delete = false
              AND buyer_id = :buyerId
              AND (status IN ('PAID', 'DELIVERED') OR paid_time IS NOT NULL)
            ORDER BY create_time DESC, id DESC
            """)
    Flux<ShopOrder> findPaidOrDeliveredByBuyerId(Long buyerId);

    default Flux<ShopOrder> findAllByItemIdOrderByCreateTimeDesc(Long itemId) {
        return findAllBy(Criteria.where("item_id").is(itemId), orderByCreateTimeDesc());
    }

    default Flux<ShopOrder> findAllOrderByCreateTimeDesc() {
        return findAllBy(Criteria.where("id").greaterThan(0), orderByCreateTimeDesc());
    }

    /**
     * 批量关闭超时未支付订单。
     * 这里直接写入标准字符串状态，避免不同 R2DBC 驱动对枚举参数编码能力不一致。
     */
    @Modifying
    @Query("""
            UPDATE shop_order
            SET status = 'CLOSED',
                delivery_status = 'SKIPPED',
                delivery_message = :message,
                update_time = CURRENT_TIMESTAMP
            WHERE is_delete = false
              AND status IN ('PENDING', 'PAYING')
              AND (payment_provider IS NULL OR payment_provider <> 'xianyu')
              AND expire_time <= :now
            """)
    Mono<Integer> closeExpiredUnpaidOrders(LocalDateTime now, String message);

    private Sort orderByCreateTimeDesc() {
        return Sort.by(Sort.Order.desc("createTime"), Sort.Order.desc("id"));
    }
}

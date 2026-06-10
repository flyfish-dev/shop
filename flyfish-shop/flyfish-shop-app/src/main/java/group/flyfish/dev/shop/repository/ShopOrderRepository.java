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
              AND expire_time <= :now
            """)
    Mono<Integer> closeExpiredUnpaidOrders(LocalDateTime now, String message);

    private Sort orderByCreateTimeDesc() {
        return Sort.by(Sort.Order.desc("createTime"), Sort.Order.desc("id"));
    }
}

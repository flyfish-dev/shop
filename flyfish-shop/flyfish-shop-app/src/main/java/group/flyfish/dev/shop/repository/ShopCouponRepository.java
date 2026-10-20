package group.flyfish.dev.shop.repository;

import group.flyfish.dev.common.repository.DefaultReactiveRepository;
import group.flyfish.dev.shop.domain.po.ShopCoupon;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 店铺优惠券仓库。
 */
public interface ShopCouponRepository extends DefaultReactiveRepository<ShopCoupon> {

    default Mono<ShopCoupon> findByCode(String code) {
        return findOneBy("code", code);
    }

    @Query("""
            SELECT * FROM shop_coupon
            WHERE is_delete = false
              AND (source IS NULL OR source = 'MANUAL')
            ORDER BY create_time DESC, id DESC
            """)
    Flux<ShopCoupon> findAllOrderByCreateTimeDesc();

    @Modifying
    @Query("UPDATE shop_coupon SET used_count = COALESCE(used_count, 0) + 1, " +
            "update_time = CURRENT_TIMESTAMP WHERE code = :code AND is_delete = false")
    Mono<Integer> increaseUsedCount(String code);

    /**
     * 原子核销全额抵扣优惠券。
     *
     * <p>零元订单没有第三方支付回调作为最终确认点，因此必须在订单结算事务中完成核销。
     * 条件更新同时校验启用状态、有效期和剩余数量，可避免并发请求超发优惠券。</p>
     */
    @Modifying
    @Query("""
            UPDATE shop_coupon
            SET used_count = COALESCE(used_count, 0) + 1,
                update_time = CURRENT_TIMESTAMP
            WHERE code = :code
              AND is_delete = false
              AND enabled = true
              AND (start_time IS NULL OR start_time <= CURRENT_TIMESTAMP)
              AND (end_time IS NULL OR end_time >= CURRENT_TIMESTAMP)
              AND (total_count IS NULL OR total_count <= 0 OR COALESCE(used_count, 0) < total_count)
            """)
    Mono<Integer> claimAvailableUse(String code);
}

package group.flyfish.dev.shop.repository;

import group.flyfish.dev.common.repository.DefaultReactiveRepository;
import group.flyfish.dev.shop.domain.po.ShopCoupon;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.relational.core.query.Criteria;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 店铺优惠券仓库。
 */
public interface ShopCouponRepository extends DefaultReactiveRepository<ShopCoupon> {

    default Mono<ShopCoupon> findByCode(String code) {
        return findOneBy("code", code);
    }

    default Flux<ShopCoupon> findAllOrderByCreateTimeDesc() {
        return findAllBy(Criteria.where("id").greaterThan(0), Sort.by(Sort.Order.desc("create_time")));
    }

    @Modifying
    @Query("UPDATE shop_coupon SET used_count = COALESCE(used_count, 0) + 1, " +
            "update_time = CURRENT_TIMESTAMP WHERE code = :code AND is_delete = false")
    Mono<Integer> increaseUsedCount(String code);
}

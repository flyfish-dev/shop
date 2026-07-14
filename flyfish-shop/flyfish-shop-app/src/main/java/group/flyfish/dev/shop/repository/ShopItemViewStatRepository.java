package group.flyfish.dev.shop.repository;

import group.flyfish.dev.common.repository.DefaultReactiveRepository;
import group.flyfish.dev.shop.domain.po.ShopItemViewStat;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import reactor.core.publisher.Mono;

/**
 * 商品查看统计仓库。
 */
public interface ShopItemViewStatRepository extends DefaultReactiveRepository<ShopItemViewStat> {

    @Query("""
            SELECT view_count
            FROM shop_item_view_stat
            WHERE item_id = :itemId
              AND is_delete = false
            LIMIT 1
            """)
    Mono<Long> findViewCountByItemId(Long itemId);

    /**
     * 批量刷盘时只更新统计表，避免高频触达商品主表。
     */
    @Modifying
    @Query("""
            UPDATE shop_item_view_stat
            SET view_count = COALESCE(view_count, 0) + :delta,
                last_view_time = CURRENT_TIMESTAMP,
                update_time = CURRENT_TIMESTAMP
            WHERE item_id = :itemId
              AND is_delete = false
            """)
    Mono<Integer> increaseViewCount(Long itemId, Long delta);

    @Modifying
    @Query("""
            INSERT INTO shop_item_view_stat
                (item_id, view_count, last_view_time, create_by, update_by, create_time, update_time, is_delete)
            VALUES
                (:itemId, :delta, CURRENT_TIMESTAMP, 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, false)
            """)
    Mono<Integer> insertInitialViewCount(Long itemId, Long delta);
}

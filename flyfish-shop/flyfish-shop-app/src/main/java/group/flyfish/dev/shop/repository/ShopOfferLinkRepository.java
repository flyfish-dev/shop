package group.flyfish.dev.shop.repository;

import group.flyfish.dev.common.repository.DefaultReactiveRepository;
import group.flyfish.dev.shop.domain.po.ShopOfferLink;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * 专享购买链接仓库。状态变更全部使用条件更新，避免并发下重复预约或核销。
 */
public interface ShopOfferLinkRepository extends DefaultReactiveRepository<ShopOfferLink> {

    @Query("SELECT * FROM shop_offer_link WHERE token_hash = :tokenHash AND is_delete = false LIMIT 1")
    Mono<ShopOfferLink> findByTokenHash(String tokenHash);

    @Query("""
            SELECT * FROM shop_offer_link
            WHERE item_id = :itemId AND is_delete = false
            ORDER BY create_time DESC, id DESC
            """)
    Flux<ShopOfferLink> findAllByItemId(Long itemId);

    @Modifying
    @Query("""
            UPDATE shop_offer_link
            SET status = 'RESERVED',
                reserved_order_no = :orderNo,
                reserved_by = :buyerId,
                reserved_time = :now,
                update_time = :now
            WHERE id = :id
              AND is_delete = false
              AND status = 'ACTIVE'
              AND expires_at >= :now
            """)
    Mono<Integer> reserve(Long id, String orderNo, Long buyerId, LocalDateTime now);

    @Modifying
    @Query("""
            UPDATE shop_offer_link
            SET status = 'USED',
                used_order_no = :orderNo,
                used_by = :buyerId,
                used_time = :now,
                update_time = :now
            WHERE id = :id
              AND is_delete = false
              AND status = 'RESERVED'
              AND reserved_order_no = :orderNo
              AND reserved_by = :buyerId
            """)
    Mono<Integer> consume(Long id, String orderNo, Long buyerId, LocalDateTime now);

    @Modifying
    @Query("""
            UPDATE shop_offer_link
            SET status = 'ACTIVE',
                reserved_order_no = NULL,
                reserved_by = NULL,
                reserved_time = NULL,
                update_time = CURRENT_TIMESTAMP
            WHERE is_delete = false
              AND status = 'RESERVED'
              AND reserved_order_no = :orderNo
            """)
    Mono<Integer> releaseByOrderNo(String orderNo);

    @Modifying
    @Query("""
            UPDATE shop_offer_link
            SET status = 'ACTIVE',
                reserved_order_no = NULL,
                reserved_by = NULL,
                reserved_time = NULL,
                update_time = CURRENT_TIMESTAMP
            WHERE is_delete = false
              AND status = 'RESERVED'
              AND reserved_order_no IN (
                  SELECT order_no FROM shop_order
                  WHERE is_delete = false
                    AND paid_time IS NULL
                    AND status IN ('CLOSED', 'FAILED')
              )
            """)
    Mono<Integer> releaseClosedOrderReservations();

    @Modifying
    @Query("""
            UPDATE shop_offer_link
            SET status = 'REVOKED',
                revoked_time = :now,
                update_time = :now
            WHERE id = :id
              AND is_delete = false
              AND status = 'ACTIVE'
            """)
    Mono<Integer> revoke(Long id, LocalDateTime now);
}

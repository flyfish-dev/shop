package group.flyfish.dev.shop.donation;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface DonationThankYouRepository extends ReactiveCrudRepository<DonationThankYou, Long> {

    default Mono<Integer> enqueue(String orderNo) {
        // 与轮询使用同一时钟，并避免 DATETIME 秒精度向上取整推迟首次发送。
        return insertPending(orderNo, LocalDateTime.now().withNano(0));
    }

    @Modifying
    @Query("""
            INSERT INTO shop_donation_thank_you (order_no, state, attempts, next_attempt_time, create_by, update_by)
            VALUES (:orderNo, 'PENDING', 0, :now, 'donation-thanks', 'donation-thanks')
            ON DUPLICATE KEY UPDATE order_no = order_no
            """)
    Mono<Integer> insertPending(String orderNo, LocalDateTime now);

    @Query("""
            SELECT * FROM shop_donation_thank_you
            WHERE state IN ('PENDING', 'PROCESSING') AND next_attempt_time <= :now
            ORDER BY next_attempt_time, id LIMIT 20
            """)
    Flux<DonationThankYou> findDue(LocalDateTime now);

    @Modifying
    @Query("""
            UPDATE shop_donation_thank_you
            SET state = 'PROCESSING', claim_token = :token, attempts = attempts + 1,
                next_attempt_time = :leaseEnd, update_time = :now
            WHERE id = :id AND state IN ('PENDING', 'PROCESSING') AND next_attempt_time <= :now
            """)
    Mono<Integer> claim(Long id, String token, LocalDateTime now, LocalDateTime leaseEnd);

    @Modifying
    @Query("""
            UPDATE shop_donation_thank_you
            SET state = :state, ticket_no = :ticketNo, completed_time = :now,
                claim_token = NULL, last_error = NULL, update_time = :now
            WHERE id = :id AND state = 'PROCESSING' AND claim_token = :token
            """)
    Mono<Integer> complete(Long id, String token, String state, String ticketNo, LocalDateTime now);

    @Modifying
    @Query("""
            UPDATE shop_donation_thank_you
            SET state = 'PENDING', claim_token = NULL, last_error = :error,
                next_attempt_time = :nextAttempt, update_time = :now
            WHERE id = :id AND state = 'PROCESSING' AND claim_token = :token
            """)
    Mono<Integer> retry(Long id, String token, String error, LocalDateTime nextAttempt, LocalDateTime now);
}

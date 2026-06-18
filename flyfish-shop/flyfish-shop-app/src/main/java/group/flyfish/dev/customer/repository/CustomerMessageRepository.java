package group.flyfish.dev.customer.repository;

import group.flyfish.dev.common.repository.DefaultReactiveRepository;
import group.flyfish.dev.customer.domain.po.CustomerMessage;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.relational.core.query.Criteria;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CustomerMessageRepository extends DefaultReactiveRepository<CustomerMessage> {

    default Flux<CustomerMessage> findAllByConversationId(Long conversationId) {
        return findAllBy(Criteria.where("conversation_id").is(conversationId),
                Sort.by(Sort.Order.asc("createTime"), Sort.Order.asc("id")));
    }

    @Modifying
    @Query("""
            UPDATE customer_message
            SET read_by_admin = true,
                update_time = CURRENT_TIMESTAMP
            WHERE is_delete = false
              AND conversation_id = :conversationId
              AND direction = 'INBOUND'
            """)
    Mono<Integer> markAdminRead(Long conversationId);

    @Modifying
    @Query("""
            UPDATE customer_message
            SET read_by_user = true,
                update_time = CURRENT_TIMESTAMP
            WHERE is_delete = false
              AND conversation_id = :conversationId
              AND direction = 'OUTBOUND'
            """)
    Mono<Integer> markUserRead(Long conversationId);
}

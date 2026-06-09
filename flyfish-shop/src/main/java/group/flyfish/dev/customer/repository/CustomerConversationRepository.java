package group.flyfish.dev.customer.repository;

import group.flyfish.dev.common.repository.DefaultReactiveRepository;
import group.flyfish.dev.customer.domain.po.CustomerConversation;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.relational.core.query.Criteria;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CustomerConversationRepository extends DefaultReactiveRepository<CustomerConversation> {

    default Mono<CustomerConversation> findByWechatOpenid(String openid) {
        return findOneBy("wechat_openid", openid);
    }

    @Query("""
            SELECT *
            FROM customer_conversation
            WHERE is_delete = false
              AND user_id = :userId
            ORDER BY COALESCE(last_message_time, update_time) DESC,
                     update_time DESC,
                     id DESC
            """)
    Flux<CustomerConversation> findAllByUserId(Long userId);

    default Flux<CustomerConversation> findAllForManagement(String keyword) {
        return findAllBy(Criteria.where("id").greaterThan(0), newestSort());
    }

    private Sort newestSort() {
        return Sort.by(Sort.Order.desc("lastMessageTime"), Sort.Order.desc("updateTime"), Sort.Order.desc("id"));
    }
}

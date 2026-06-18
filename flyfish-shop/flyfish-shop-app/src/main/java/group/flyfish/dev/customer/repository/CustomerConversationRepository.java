package group.flyfish.dev.customer.repository;

import group.flyfish.dev.common.repository.DefaultReactiveRepository;
import group.flyfish.dev.customer.domain.po.CustomerConversation;
import org.springframework.data.r2dbc.repository.Query;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CustomerConversationRepository extends DefaultReactiveRepository<CustomerConversation> {

    default Mono<CustomerConversation> findByWechatOpenid(String openid) {
        return findOneBy("wechat_openid", openid);
    }

    @Query("""
            SELECT *
            FROM customer_conversation
            WHERE wechat_openid = :openid
            ORDER BY is_delete ASC, update_time DESC, id DESC
            LIMIT 1
            """)
    Mono<CustomerConversation> findByWechatOpenidIncludingDeleted(String openid);

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

    @Query("""
            SELECT conversation.*
            FROM customer_conversation conversation
            WHERE conversation.is_delete = false
              AND EXISTS (
                SELECT 1
                FROM customer_message message
                WHERE message.is_delete = false
                  AND message.conversation_id = conversation.id
                  AND message.channel = 'WEB'
              )
            ORDER BY COALESCE(conversation.last_message_time, conversation.update_time) DESC,
                     conversation.update_time DESC,
                     conversation.id DESC
            """)
    Flux<CustomerConversation> findAllForManagement();
}

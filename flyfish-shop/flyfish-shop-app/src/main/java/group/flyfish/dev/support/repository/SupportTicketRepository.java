package group.flyfish.dev.support.repository;

import group.flyfish.dev.common.repository.DefaultReactiveRepository;
import group.flyfish.dev.support.domain.po.SupportTicket;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.relational.core.query.Criteria;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SupportTicketRepository extends DefaultReactiveRepository<SupportTicket> {

    default Mono<SupportTicket> findByTicketNo(String ticketNo) {
        return findOneBy("ticket_no", ticketNo);
    }

    default Flux<SupportTicket> findAllByCreatorId(Long creatorId, String status) {
        Criteria criteria = Criteria.where("creator_id").is(creatorId);
        if (status != null) {
            criteria = criteria.and("status").is(status);
        }
        return findAllBy(criteria, orderByUpdateTimeDesc());
    }

    default Flux<SupportTicket> findAllForManagement(String status) {
        Criteria criteria = Criteria.where("id").greaterThan(0);
        if (status != null) {
            criteria = criteria.and("status").is(status);
        }
        return findAllBy(criteria, orderByUpdateTimeDesc());
    }

    default Flux<SupportTicket> findAllUnreadByCreatorId(Long creatorId) {
        return findAllBy(Criteria.where("creator_id").is(creatorId)
                .and("user_unread_count").greaterThan(0), orderByUpdateTimeDesc());
    }

    default Flux<SupportTicket> findAllUnreadForManagement() {
        return findAllBy(Criteria.where("admin_unread_count").greaterThan(0), orderByUpdateTimeDesc());
    }

    @Modifying
    @Query("UPDATE support_ticket SET user_unread_count = 0 WHERE id = :ticketId")
    Mono<Integer> clearUserUnread(Long ticketId);

    @Modifying
    @Query("UPDATE support_ticket SET admin_unread_count = 0 WHERE id = :ticketId")
    Mono<Integer> clearAdminUnread(Long ticketId);

    private Sort orderByUpdateTimeDesc() {
        return Sort.by(Sort.Order.desc("updateTime"), Sort.Order.desc("createTime"), Sort.Order.desc("id"));
    }
}

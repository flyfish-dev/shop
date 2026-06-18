package group.flyfish.dev.support.repository;

import group.flyfish.dev.common.repository.DefaultReactiveRepository;
import group.flyfish.dev.support.domain.po.SupportTicketMessage;
import org.springframework.data.domain.Sort;
import org.springframework.data.relational.core.query.Criteria;
import reactor.core.publisher.Flux;

public interface SupportTicketMessageRepository extends DefaultReactiveRepository<SupportTicketMessage> {

    default Flux<SupportTicketMessage> findAllByTicketIdOrderByCreateTimeAsc(Long ticketId) {
        return findAllBy(Criteria.where("ticket_id").is(ticketId),
                Sort.by(Sort.Order.asc("createTime"), Sort.Order.asc("id")));
    }
}

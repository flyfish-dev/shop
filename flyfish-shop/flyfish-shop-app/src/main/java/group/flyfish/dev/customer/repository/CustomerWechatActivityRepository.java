package group.flyfish.dev.customer.repository;

import group.flyfish.dev.common.repository.DefaultReactiveRepository;
import group.flyfish.dev.customer.domain.po.CustomerWechatActivity;
import org.springframework.data.domain.Sort;
import org.springframework.data.relational.core.query.Criteria;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CustomerWechatActivityRepository extends DefaultReactiveRepository<CustomerWechatActivity> {

    default Mono<CustomerWechatActivity> findByWechatMsgId(String msgId) {
        return findOneBy("wechat_msg_id", msgId);
    }

    default Flux<CustomerWechatActivity> findLatest(Criteria criteria, int limit) {
        return findAllBy(criteria, Sort.by(Sort.Order.desc("create_time"), Sort.Order.desc("id")))
                .take(Math.max(1, Math.min(limit, 200)));
    }
}

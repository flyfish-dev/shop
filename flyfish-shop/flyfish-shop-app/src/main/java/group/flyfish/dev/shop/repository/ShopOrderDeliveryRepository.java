package group.flyfish.dev.shop.repository;

import group.flyfish.dev.common.repository.DefaultReactiveRepository;
import group.flyfish.dev.shop.domain.po.ShopOrderDelivery;
import org.springframework.data.domain.Sort;
import org.springframework.data.relational.core.query.Criteria;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ShopOrderDeliveryRepository extends DefaultReactiveRepository<ShopOrderDelivery> {

    default Mono<ShopOrderDelivery> findByOrderNo(String orderNo) {
        return findAllByOrderNoOrderByCreateTimeAsc(orderNo).next();
    }

    default Mono<ShopOrderDelivery> findByOrderNoAndDeliveryType(String orderNo, String deliveryType) {
        return findAllBy(Criteria.where("order_no").is(orderNo).and("delivery_type").is(deliveryType),
                orderByCreateTimeAsc()).next();
    }

    default Flux<ShopOrderDelivery> findAllByOrderNoOrderByCreateTimeAsc(String orderNo) {
        return findAllBy(Criteria.where("order_no").is(orderNo), orderByCreateTimeAsc());
    }

    private Sort orderByCreateTimeAsc() {
        return Sort.by(Sort.Order.asc("createTime"), Sort.Order.asc("id"));
    }
}

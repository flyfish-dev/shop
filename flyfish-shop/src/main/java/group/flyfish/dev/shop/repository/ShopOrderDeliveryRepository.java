package group.flyfish.dev.shop.repository;

import group.flyfish.dev.common.repository.DefaultReactiveRepository;
import group.flyfish.dev.shop.domain.po.ShopOrderDelivery;
import reactor.core.publisher.Mono;

public interface ShopOrderDeliveryRepository extends DefaultReactiveRepository<ShopOrderDelivery> {

    default Mono<ShopOrderDelivery> findByOrderNo(String orderNo) {
        return findOneBy("order_no", orderNo);
    }
}

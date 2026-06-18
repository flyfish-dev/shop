package group.flyfish.dev.shop.repository;

import group.flyfish.dev.common.repository.DefaultReactiveRepository;
import group.flyfish.dev.shop.domain.po.ShopLicenseKeyPair;
import reactor.core.publisher.Mono;

public interface ShopLicenseKeyPairRepository extends DefaultReactiveRepository<ShopLicenseKeyPair> {

    default Mono<ShopLicenseKeyPair> findByOrderNo(String orderNo) {
        return findOneBy("order_no", orderNo);
    }
}

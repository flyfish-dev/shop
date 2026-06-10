package group.flyfish.dev.shop.repository;

import group.flyfish.dev.common.repository.DefaultReactiveRepository;
import group.flyfish.dev.shop.domain.po.ShopLicenseRoot;
import reactor.core.publisher.Mono;

public interface ShopLicenseRootRepository extends DefaultReactiveRepository<ShopLicenseRoot> {

    default Mono<ShopLicenseRoot> findByName(String name) {
        return findOneBy("name", name);
    }
}

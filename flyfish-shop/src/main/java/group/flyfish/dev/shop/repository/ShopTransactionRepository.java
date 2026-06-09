package group.flyfish.dev.shop.repository;

import group.flyfish.dev.common.repository.DefaultReactiveRepository;
import group.flyfish.dev.shop.domain.po.ShopTransaction;
import reactor.core.publisher.Mono;

/**
 * 店铺流水仓库
 *
 * @author wangyu
 */
public interface ShopTransactionRepository extends DefaultReactiveRepository<ShopTransaction> {

    default Mono<ShopTransaction> findByCode(String code) {
        return findOneBy("code", code);
    }
}

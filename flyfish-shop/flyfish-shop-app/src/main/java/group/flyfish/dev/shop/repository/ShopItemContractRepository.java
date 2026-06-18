package group.flyfish.dev.shop.repository;

import group.flyfish.dev.common.repository.DefaultReactiveRepository;
import group.flyfish.dev.shop.domain.po.ShopItemContract;
import org.springframework.data.domain.Sort;
import org.springframework.data.relational.core.query.Criteria;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 商品合同绑定仓库。
 */
public interface ShopItemContractRepository extends DefaultReactiveRepository<ShopItemContract> {

    default Flux<ShopItemContract> findEnabledByItemId(Long itemId) {
        return findAllBy(Criteria.where("item_id").is(itemId).and("enabled").is(true), orderBySort());
    }

    default Mono<Void> deleteByItemId(Long itemId) {
        return findAllBy(Criteria.where("item_id").is(itemId), orderBySort())
                .flatMap(this::delete)
                .then();
    }

    private Sort orderBySort() {
        return Sort.by(Sort.Order.asc("sort"), Sort.Order.asc("id"));
    }
}

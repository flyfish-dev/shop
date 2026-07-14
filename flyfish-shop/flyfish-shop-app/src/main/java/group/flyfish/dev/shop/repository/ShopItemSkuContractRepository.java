package group.flyfish.dev.shop.repository;

import group.flyfish.dev.common.repository.DefaultReactiveRepository;
import group.flyfish.dev.shop.domain.po.ShopItemSkuContract;
import org.springframework.data.domain.Sort;
import org.springframework.data.relational.core.query.Criteria;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * SKU 合同绑定仓库。
 */
public interface ShopItemSkuContractRepository extends DefaultReactiveRepository<ShopItemSkuContract> {

    default Flux<ShopItemSkuContract> findEnabledBySkuId(Long skuId) {
        return findAllBy(Criteria.where("sku_id").is(skuId).and("enabled").is(true), orderBySort());
    }

    default Mono<Void> deleteBySkuId(Long skuId) {
        return findAllBy(Criteria.where("sku_id").is(skuId), orderBySort())
                .flatMap(this::delete)
                .then();
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

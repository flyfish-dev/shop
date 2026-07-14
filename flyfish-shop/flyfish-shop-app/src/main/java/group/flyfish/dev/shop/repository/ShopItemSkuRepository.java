package group.flyfish.dev.shop.repository;

import group.flyfish.dev.common.repository.DefaultReactiveRepository;
import group.flyfish.dev.shop.domain.po.ShopItemSku;
import org.springframework.data.domain.Sort;
import org.springframework.data.relational.core.query.Criteria;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 商品 SKU 仓库。
 */
public interface ShopItemSkuRepository extends DefaultReactiveRepository<ShopItemSku> {

    default Flux<ShopItemSku> findAllByItemIdOrderBySort(Long itemId) {
        return findAllBy(Criteria.where("item_id").is(itemId), orderBySort());
    }

    default Flux<ShopItemSku> findEnabledByItemIdOrderBySort(Long itemId) {
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

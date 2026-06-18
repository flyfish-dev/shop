package group.flyfish.dev.shop.repository;

import group.flyfish.dev.common.repository.DefaultReactiveRepository;
import group.flyfish.dev.shop.domain.po.ShopContract;
import org.springframework.data.domain.Sort;
import org.springframework.data.relational.core.query.Criteria;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 合同仓库。
 */
public interface ShopContractRepository extends DefaultReactiveRepository<ShopContract> {

    default Flux<ShopContract> findAllOrderBySort() {
        return findAllBy(Criteria.where("id").greaterThan(0), orderBySort());
    }

    default Flux<ShopContract> findEnabledByIds(List<Long> ids) {
        return findAllByValues("id", ids)
                .filter(contract -> Boolean.TRUE.equals(contract.getEnabled()))
                .sort((left, right) -> {
                    int sort = Integer.compare(nullToZero(left.getSort()), nullToZero(right.getSort()));
                    return sort != 0 ? sort : Long.compare(left.getId(), right.getId());
                });
    }

    private Sort orderBySort() {
        return Sort.by(Sort.Order.asc("sort"), Sort.Order.desc("id"));
    }

    private static int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }
}

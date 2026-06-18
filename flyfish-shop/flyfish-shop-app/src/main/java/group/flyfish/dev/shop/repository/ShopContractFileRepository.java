package group.flyfish.dev.shop.repository;

import group.flyfish.dev.common.repository.DefaultReactiveRepository;
import group.flyfish.dev.shop.domain.po.ShopContractFile;
import org.springframework.data.domain.Sort;
import org.springframework.data.relational.core.query.Criteria;
import reactor.core.publisher.Flux;

/**
 * 合同文件仓库。
 */
public interface ShopContractFileRepository extends DefaultReactiveRepository<ShopContractFile> {

    default Flux<ShopContractFile> findAllByContractId(Long contractId) {
        return findAllBy(Criteria.where("contract_id").is(contractId), orderBySort());
    }

    default Flux<ShopContractFile> findEnabledByContractId(Long contractId) {
        return findAllBy(Criteria.where("contract_id").is(contractId).and("enabled").is(true), orderBySort());
    }

    private Sort orderBySort() {
        return Sort.by(Sort.Order.asc("sort"), Sort.Order.asc("id"));
    }
}

package group.flyfish.dev.shop.repository;

import group.flyfish.dev.common.repository.DefaultReactiveRepository;
import group.flyfish.dev.shop.domain.po.ShopContractSignature;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.relational.core.query.Criteria;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 合同签署留痕仓库。
 */
public interface ShopContractSignatureRepository extends DefaultReactiveRepository<ShopContractSignature> {

    default Flux<ShopContractSignature> findAllOrderByCreateTimeDesc() {
        return findAllBy(Criteria.where("id").greaterThan(0), orderByCreateTimeDesc());
    }

    @Query("""
            SELECT *
            FROM shop_contract_signature
            WHERE is_delete = false
              AND sign_token = :signToken
              AND buyer_id = :buyerId
              AND item_id = :itemId
              AND contract_file_id = :contractFileId
            ORDER BY id DESC
            LIMIT 1
            """)
    Mono<ShopContractSignature> findSignedFile(String signToken, Long buyerId, Long itemId, Long contractFileId);

    @Query("""
            SELECT *
            FROM shop_contract_signature
            WHERE is_delete = false
              AND sign_token = :signToken
              AND buyer_id = :buyerId
              AND item_id = :itemId
              AND status IN ('AGREED', 'BOUND')
            ORDER BY create_time DESC, id DESC
            """)
    Flux<ShopContractSignature> findAgreedByToken(String signToken, Long buyerId, Long itemId);

    @Modifying
    @Query("""
            UPDATE shop_contract_signature
            SET order_no = :orderNo,
                status = 'BOUND',
                update_time = CURRENT_TIMESTAMP
            WHERE is_delete = false
              AND sign_token = :signToken
              AND buyer_id = :buyerId
              AND item_id = :itemId
              AND order_no IS NULL
            """)
    Mono<Integer> bindOrder(String signToken, Long buyerId, Long itemId, String orderNo);

    private Sort orderByCreateTimeDesc() {
        return Sort.by(Sort.Order.desc("createTime"), Sort.Order.desc("id"));
    }
}

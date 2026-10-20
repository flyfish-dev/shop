package group.flyfish.dev.shop.repository;

import group.flyfish.dev.common.repository.DefaultReactiveRepository;
import group.flyfish.dev.shop.domain.po.ShopLicenseKeyPair;
import org.springframework.data.r2dbc.repository.Query;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ShopLicenseKeyPairRepository extends DefaultReactiveRepository<ShopLicenseKeyPair> {

    default Mono<ShopLicenseKeyPair> findByOrderNo(String orderNo) {
        return findOneBy("order_no", orderNo);
    }

    @Query("""
            SELECT *
            FROM shop_license_key_pair
            WHERE is_delete = false
              AND order_no = :orderNo
              AND component_key = :componentKey
            LIMIT 1
            """)
    Mono<ShopLicenseKeyPair> findByOrderNoAndComponentKey(String orderNo, String componentKey);

    default Mono<ShopLicenseKeyPair> findByLicenseNo(String licenseNo) {
        return findOneBy("license_no", licenseNo);
    }

    @Query("""
            SELECT *
            FROM shop_license_key_pair
            WHERE is_delete = false
              AND order_no = :orderNo
            ORDER BY create_time ASC, id ASC
            """)
    Flux<ShopLicenseKeyPair> findAllByOrderNo(String orderNo);

    @Query("""
            SELECT *
            FROM shop_license_key_pair
            WHERE is_delete = false
              AND buyer_id = :buyerId
              AND item_id = :itemId
            ORDER BY create_time DESC, id DESC
            """)
    Flux<ShopLicenseKeyPair> findAllByBuyerIdAndItemId(Long buyerId, Long itemId);

    @Query("""
            SELECT *
            FROM shop_license_key_pair
            WHERE is_delete = false
              AND buyer_id = :buyerId
              AND item_id = :itemId
              AND license_no = :licenseNo
            LIMIT 1
            """)
    Mono<ShopLicenseKeyPair> findByBuyerIdAndItemIdAndLicenseNo(Long buyerId, Long itemId, String licenseNo);
}

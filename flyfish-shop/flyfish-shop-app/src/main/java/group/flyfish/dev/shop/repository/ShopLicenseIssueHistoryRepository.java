package group.flyfish.dev.shop.repository;

import group.flyfish.dev.common.repository.DefaultReactiveRepository;
import group.flyfish.dev.shop.domain.po.ShopLicenseIssueHistory;
import org.springframework.data.r2dbc.repository.Query;
import reactor.core.publisher.Mono;

public interface ShopLicenseIssueHistoryRepository extends DefaultReactiveRepository<ShopLicenseIssueHistory> {

    @Query("""
            SELECT *
            FROM shop_license_issue_history
            WHERE is_delete = false
              AND order_no = :orderNo
              AND license_no = :licenseNo
            LIMIT 1
            """)
    Mono<ShopLicenseIssueHistory> findByOrderNoAndLicenseNo(String orderNo, String licenseNo);
}

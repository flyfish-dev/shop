package group.flyfish.dev.shop.service.impl;

import group.flyfish.dev.auth.api.client.AuthUserGateway;
import group.flyfish.dev.shop.domain.po.Shop;
import group.flyfish.dev.shop.domain.vo.ShopManageWorkbenchSummaryVo;
import group.flyfish.dev.shop.service.ShopManageWorkbenchService;
import group.flyfish.dev.shop.service.ShopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 小铺管理工作台统计服务实现。
 *
 * <p>工作台只需要统计数字，因此这里直接使用聚合 SQL 读取 COUNT/SUM，
 * 不再调用列表接口后在内存里计算，避免商品、订单、用户等数据量增大后拖慢首屏。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ShopManageWorkbenchServiceImpl implements ShopManageWorkbenchService {

    private static final String NOT_DELETED = "is_delete = false";

    private final DatabaseClient databaseClient;
    private final ShopService shopService;
    private final AuthUserGateway authUserGateway;

    @Override
    public Mono<ShopManageWorkbenchSummaryVo> overview() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime tomorrowStart = todayStart.plusDays(1);

        return Mono.zip(values -> {
                    ShopManageWorkbenchSummaryVo summary = new ShopManageWorkbenchSummaryVo();
                    summary.setShopName((String) values[0]);
                    applyItemStats(summary, (ItemStats) values[1]);
                    applyOrderStats(summary, (OrderStats) values[2]);
                    applyTicketStats(summary, (TicketStats) values[3]);
                    return summary;
                },
                safe("店铺名称", shopName(), "飞鱼小铺"),
                safe("商品统计", itemStats(), new ItemStats(0, 0, 0, 0)),
                safe("订单统计", orderStats(todayStart, tomorrowStart),
                        new OrderStats(0, 0, 0, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO)),
                safe("工单统计", ticketStats(), new TicketStats(0, 0)));
    }

    @Override
    public Mono<ShopManageWorkbenchSummaryVo> insights() {
        LocalDateTime now = LocalDateTime.now();
        return Mono.zip(values -> {
                    ShopManageWorkbenchSummaryVo summary = new ShopManageWorkbenchSummaryVo();
                    summary.setGroupCount((Long) values[0]);
                    summary.setUserCount((Long) values[1]);
                    applyCouponStats(summary, (CouponStats) values[2]);
                    applyRepositoryStats(summary, (RepositoryStats) values[3]);
                    applyTokenStats(summary, (TokenStats) values[4]);
                    return summary;
                },
                safe("商品分组统计", count("SELECT COUNT(*) FROM shop_item_group WHERE " + NOT_DELETED), 0L),
                safe("用户统计", authUserGateway.countUsers(), 0L),
                safe("优惠券统计", couponStats(), new CouponStats(0, 0)),
                safe("代码仓库统计", repositoryStats(), new RepositoryStats(0, 0)),
                safe("API Token 统计", tokenStats(now), new TokenStats(0, 0)));
    }

    @Override
    public Mono<ShopManageWorkbenchSummaryVo> summary() {
        return Mono.zip(overview(), insights())
                .map(tuple -> merge(tuple.getT1(), tuple.getT2()));
    }

    private Mono<String> shopName() {
        return shopService.getCurrentShop()
                .map(Shop::getName)
                .filter(StringUtils::isNotBlank)
                .defaultIfEmpty("飞鱼小铺");
    }

    private Mono<ItemStats> itemStats() {
        return databaseClient.sql("""
                        SELECT COUNT(*) AS item_total,
                               COALESCE(SUM(CASE WHEN enabled = true OR enabled IS NULL THEN 1 ELSE 0 END), 0) AS enabled_item_count,
                               COALESCE(SUM(CASE WHEN pinned = true THEN 1 ELSE 0 END), 0) AS pinned_item_count,
                               COALESCE(SUM(CASE WHEN recommended = true THEN 1 ELSE 0 END), 0) AS recommended_item_count
                        FROM shop_item
                        WHERE is_delete = false
                        """)
                .map((row, metadata) -> new ItemStats(
                        toLong(row.get(0)),
                        toLong(row.get(1)),
                        toLong(row.get(2)),
                        toLong(row.get(3))))
                .one()
                .defaultIfEmpty(new ItemStats(0, 0, 0, 0));
    }

    private Mono<OrderStats> orderStats(LocalDateTime startTime, LocalDateTime endTime) {
        return databaseClient.sql("""
                        SELECT COUNT(*) AS order_total,
                               COALESCE(SUM(CASE WHEN (status IN ('PAID', 'DELIVERED') OR paid_time IS NOT NULL) THEN 1 ELSE 0 END), 0) AS paid_order_count,
                               COALESCE(SUM(CASE WHEN status IN ('PENDING', 'PAYING') THEN 1 ELSE 0 END), 0) AS pending_payment_count,
                               COALESCE(SUM(CASE WHEN status IN ('PAID', 'DELIVERED') AND delivery_status IN ('WAITING', 'PROCESSING') THEN 1 ELSE 0 END), 0) AS waiting_delivery_count,
                               COALESCE(SUM(CASE WHEN (status IN ('PAID', 'DELIVERED') OR paid_time IS NOT NULL) THEN amount ELSE 0 END), 0) AS revenue_amount,
                               COALESCE(SUM(CASE WHEN (status IN ('PAID', 'DELIVERED') OR paid_time IS NOT NULL)
                                                  AND COALESCE(paid_time, update_time, create_time) >= :startTime
                                                  AND COALESCE(paid_time, update_time, create_time) < :endTime
                                             THEN 1 ELSE 0 END), 0) AS today_order_count,
                               COALESCE(SUM(CASE WHEN (status IN ('PAID', 'DELIVERED') OR paid_time IS NOT NULL)
                                                  AND COALESCE(paid_time, update_time, create_time) >= :startTime
                                                  AND COALESCE(paid_time, update_time, create_time) < :endTime
                                             THEN amount ELSE 0 END), 0) AS today_revenue_amount
                        FROM shop_order
                        WHERE is_delete = false
                        """)
                .bind("startTime", startTime)
                .bind("endTime", endTime)
                .map((row, metadata) -> new OrderStats(
                        toLong(row.get(0)),
                        toLong(row.get(1)),
                        toLong(row.get(2)),
                        toLong(row.get(3)),
                        toMoney(row.get(4)),
                        toLong(row.get(5)),
                        toMoney(row.get(6))))
                .one()
                .defaultIfEmpty(new OrderStats(0, 0, 0, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO));
    }

    private Mono<TicketStats> ticketStats() {
        return databaseClient.sql("""
                        SELECT COUNT(*) AS ticket_total,
                               COALESCE(SUM(CASE WHEN status IS NULL OR status NOT IN ('RESOLVED', 'CLOSED') THEN 1 ELSE 0 END), 0) AS active_ticket_count
                        FROM support_ticket
                        WHERE is_delete = false
                        """)
                .map((row, metadata) -> new TicketStats(
                        toLong(row.get(0)),
                        toLong(row.get(1))))
                .one()
                .defaultIfEmpty(new TicketStats(0, 0));
    }

    private Mono<CouponStats> couponStats() {
        return databaseClient.sql("""
                        SELECT COUNT(*) AS coupon_count,
                               COALESCE(SUM(CASE WHEN enabled = true OR enabled IS NULL THEN 1 ELSE 0 END), 0) AS enabled_coupon_count
                        FROM shop_coupon
                        WHERE is_delete = false
                        """)
                .map((row, metadata) -> new CouponStats(
                        toLong(row.get(0)),
                        toLong(row.get(1))))
                .one()
                .defaultIfEmpty(new CouponStats(0, 0));
    }

    private Mono<RepositoryStats> repositoryStats() {
        return databaseClient.sql("""
                        SELECT COUNT(*) AS repository_count,
                               COALESCE(SUM(CASE WHEN enabled = true OR enabled IS NULL THEN 1 ELSE 0 END), 0) AS enabled_repository_count
                        FROM git_repository
                        WHERE is_delete = false
                        """)
                .map((row, metadata) -> new RepositoryStats(
                        toLong(row.get(0)),
                        toLong(row.get(1))))
                .one()
                .defaultIfEmpty(new RepositoryStats(0, 0));
    }

    private Mono<TokenStats> tokenStats(LocalDateTime now) {
        return databaseClient.sql("""
                        SELECT COUNT(*) AS token_count,
                               COALESCE(SUM(CASE WHEN (enabled = true OR enabled IS NULL)
                                                  AND (expire_time IS NULL OR expire_time > :now)
                                             THEN 1 ELSE 0 END), 0) AS enabled_token_count
                        FROM git_api_token
                        WHERE is_delete = false
                        """)
                .bind("now", now)
                .map((row, metadata) -> new TokenStats(
                        toLong(row.get(0)),
                        toLong(row.get(1))))
                .one()
                .defaultIfEmpty(new TokenStats(0, 0));
    }

    private Mono<Long> count(String sql) {
        return databaseClient.sql(sql)
                .map((row, metadata) -> toLong(row.get(0)))
                .one()
                .defaultIfEmpty(0L);
    }

    private <T> Mono<T> safe(String name, Mono<T> source, T fallback) {
        return source.onErrorResume(ex -> {
            log.warn("小铺工作台{}加载失败，使用默认值兜底", name, ex);
            return Mono.just(fallback);
        });
    }

    private ShopManageWorkbenchSummaryVo merge(ShopManageWorkbenchSummaryVo overview,
                                               ShopManageWorkbenchSummaryVo insights) {
        overview.setGroupCount(insights.getGroupCount());
        overview.setUserCount(insights.getUserCount());
        overview.setCouponCount(insights.getCouponCount());
        overview.setEnabledCouponCount(insights.getEnabledCouponCount());
        overview.setRepositoryCount(insights.getRepositoryCount());
        overview.setEnabledRepositoryCount(insights.getEnabledRepositoryCount());
        overview.setTokenCount(insights.getTokenCount());
        overview.setEnabledTokenCount(insights.getEnabledTokenCount());
        return overview;
    }

    private void applyItemStats(ShopManageWorkbenchSummaryVo summary, ItemStats stats) {
        summary.setItemTotal(stats.itemTotal());
        summary.setEnabledItemCount(stats.enabledItemCount());
        summary.setPinnedItemCount(stats.pinnedItemCount());
        summary.setRecommendedItemCount(stats.recommendedItemCount());
    }

    private void applyOrderStats(ShopManageWorkbenchSummaryVo summary, OrderStats stats) {
        summary.setOrderTotal(stats.orderTotal());
        summary.setPaidOrderCount(stats.paidOrderCount());
        summary.setPendingPaymentCount(stats.pendingPaymentCount());
        summary.setWaitingDeliveryCount(stats.waitingDeliveryCount());
        summary.setRevenueAmount(stats.revenueAmount());
        summary.setTodayOrderCount(stats.todayOrderCount());
        summary.setTodayRevenueAmount(stats.todayRevenueAmount());
    }

    private void applyTicketStats(ShopManageWorkbenchSummaryVo summary, TicketStats stats) {
        summary.setTicketTotal(stats.ticketTotal());
        summary.setActiveTicketCount(stats.activeTicketCount());
    }

    private void applyCouponStats(ShopManageWorkbenchSummaryVo summary, CouponStats stats) {
        summary.setCouponCount(stats.couponCount());
        summary.setEnabledCouponCount(stats.enabledCouponCount());
    }

    private void applyRepositoryStats(ShopManageWorkbenchSummaryVo summary, RepositoryStats stats) {
        summary.setRepositoryCount(stats.repositoryCount());
        summary.setEnabledRepositoryCount(stats.enabledRepositoryCount());
    }

    private void applyTokenStats(ShopManageWorkbenchSummaryVo summary, TokenStats stats) {
        summary.setTokenCount(stats.tokenCount());
        summary.setEnabledTokenCount(stats.enabledTokenCount());
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        return Long.parseLong(value.toString());
    }

    private BigDecimal toMoney(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.toString());
    }

    private record ItemStats(long itemTotal, long enabledItemCount, long pinnedItemCount, long recommendedItemCount) {
    }

    private record OrderStats(long orderTotal, long paidOrderCount, long pendingPaymentCount, long waitingDeliveryCount,
                              BigDecimal revenueAmount, long todayOrderCount, BigDecimal todayRevenueAmount) {
    }

    private record TicketStats(long ticketTotal, long activeTicketCount) {
    }

    private record CouponStats(long couponCount, long enabledCouponCount) {
    }

    private record RepositoryStats(long repositoryCount, long enabledRepositoryCount) {
    }

    private record TokenStats(long tokenCount, long enabledTokenCount) {
    }
}

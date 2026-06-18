package group.flyfish.dev.shop.domain.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 小铺管理工作台统计摘要。
 *
 * <p>这里只承载数字和店铺名称，不返回商品、订单、用户等明细列表，避免工作台首屏把业务表全量拉满。</p>
 */
@Data
public class ShopManageWorkbenchSummaryVo {

    /**
     * 当前店铺名称。
     */
    private String shopName = "飞鱼小铺";

    /**
     * 商品总数。
     */
    private long itemTotal;

    /**
     * 已上架商品数。
     */
    private long enabledItemCount;

    /**
     * 首页置顶商品数。
     */
    private long pinnedItemCount;

    /**
     * 推荐商品数。
     */
    private long recommendedItemCount;

    /**
     * 商品分组数。
     */
    private long groupCount;

    /**
     * 订单总数。
     */
    private long orderTotal;

    /**
     * 已支付或已交付订单数。
     */
    private long paidOrderCount;

    /**
     * 待支付订单数。
     */
    private long pendingPaymentCount;

    /**
     * 待交付订单数。
     */
    private long waitingDeliveryCount;

    /**
     * 累计成交金额。
     */
    private BigDecimal revenueAmount = BigDecimal.ZERO;

    /**
     * 今日成交订单数。
     */
    private long todayOrderCount;

    /**
     * 今日成交金额。
     */
    private BigDecimal todayRevenueAmount = BigDecimal.ZERO;

    /**
     * 工单总数。
     */
    private long ticketTotal;

    /**
     * 未完结工单数。
     */
    private long activeTicketCount;

    /**
     * 注册用户数。
     */
    private long userCount;

    /**
     * 优惠券总数。
     */
    private long couponCount;

    /**
     * 启用中的优惠券数。
     */
    private long enabledCouponCount;

    /**
     * 系统仓库总数。
     */
    private long repositoryCount;

    /**
     * 启用中的系统仓库数。
     */
    private long enabledRepositoryCount;

    /**
     * API Token 总数。
     */
    private long tokenCount;

    /**
     * 启用且未过期的 API Token 数。
     */
    private long enabledTokenCount;
}

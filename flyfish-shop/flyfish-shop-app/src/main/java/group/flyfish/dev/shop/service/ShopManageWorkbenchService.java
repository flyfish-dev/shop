package group.flyfish.dev.shop.service;

import group.flyfish.dev.shop.domain.vo.ShopManageWorkbenchSummaryVo;
import reactor.core.publisher.Mono;

/**
 * 小铺管理工作台统计服务。
 */
public interface ShopManageWorkbenchService {

    /**
     * 查询工作台主指标。
     *
     * @return 店铺名称、商品、订单、待处理事项等首屏核心数字
     */
    Mono<ShopManageWorkbenchSummaryVo> overview();

    /**
     * 查询工作台运营洞察指标。
     *
     * @return 分组、优惠券、代码仓库、API Token、用户等辅助数字
     */
    Mono<ShopManageWorkbenchSummaryVo> insights();

    /**
     * 查询工作台统计摘要。
     *
     * @return 只包含统计数字的轻量摘要
     */
    Mono<ShopManageWorkbenchSummaryVo> summary();
}

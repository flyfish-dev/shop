package group.flyfish.dev.shop.service;

import group.flyfish.dev.shop.domain.dto.ShopOrderDto;
import group.flyfish.dev.shop.domain.po.ShopItem;
import group.flyfish.dev.shop.domain.po.ShopOrder;
import group.flyfish.dev.shop.domain.vo.ShopOrderPaymentVo;
import reactor.core.publisher.Mono;

/**
 * 抽象支付服务
 *
 * @author wangyu
 */
public interface PayService {

    /**
     * 订单支付
     *
     * @param order 订单
     * @param item 商品
     * @param request 支付请求
     * @return 结果
     */
    Mono<ShopOrderPaymentVo> pay(ShopOrder order, ShopItem item, ShopOrderDto request);
}

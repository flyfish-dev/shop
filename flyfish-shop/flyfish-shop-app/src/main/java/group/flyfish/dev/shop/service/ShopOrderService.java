package group.flyfish.dev.shop.service;

import group.flyfish.dev.shop.domain.dto.ShopCouponApplyDto;
import group.flyfish.dev.shop.domain.dto.ShopOrderDto;
import group.flyfish.dev.shop.domain.dto.ShopOrderDeliveryDto;
import group.flyfish.dev.shop.domain.vo.ShopCouponApplyVo;
import group.flyfish.dev.shop.domain.vo.ShopOrderCreateVo;
import group.flyfish.dev.shop.domain.vo.ShopOrderDeliveryExtractVo;
import group.flyfish.dev.shop.domain.vo.ShopOrderVo;
import group.flyfish.dev.shop.domain.vo.ShopPurchaseAvailabilityVo;
import group.flyfish.dev.shop.service.support.h5zhifu.bean.H5ZhiFuNotifyDto;
import group.flyfish.dev.auth.api.user.PortalUserVo;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ShopOrderService {

    Mono<ShopOrderCreateVo> createOrder(ShopOrderDto dto, PortalUserVo buyer);

    Mono<ShopCouponApplyVo> applyCoupon(ShopCouponApplyDto dto, PortalUserVo buyer);

    Mono<ShopPurchaseAvailabilityVo> checkPurchaseAvailability(Long itemId, PortalUserVo buyer);

    Flux<ShopOrderVo> getOrders(PortalUserVo buyer, Long itemId);

    Flux<ShopOrderVo> getMyOrders(PortalUserVo buyer);

    Mono<ShopOrderVo> getOrder(PortalUserVo buyer, String orderNo);

    Mono<ShopOrderDeliveryExtractVo> extractDelivery(PortalUserVo buyer, String orderNo);

    Mono<ShopOrderVo> updateDelivery(String orderNo, ShopOrderDeliveryDto dto);

    Mono<Void> handlePaymentNotify(H5ZhiFuNotifyDto dto);

    Mono<Integer> closeExpiredUnpaidOrders();
}

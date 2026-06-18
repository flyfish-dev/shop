package group.flyfish.dev.shop.scheduler;

import group.flyfish.dev.shop.service.ShopOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 关闭超时未支付订单。
 * 这里采用固定频率的轻量扫描，足够覆盖当前单体部署；未来订单量变大时可替换为延迟队列或任务调度平台。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShopOrderExpirationScheduler {

    private final ShopOrderService shopOrderService;

    @Scheduled(fixedDelayString = "${flyfish.shop.order-expiration-scan-delay:60000}")
    public void closeExpiredOrders() {
        shopOrderService.closeExpiredUnpaidOrders()
                .doOnNext(count -> {
                    if (count > 0) {
                        log.info("自动关闭超时未支付订单 {} 笔", count);
                    }
                })
                .doOnError(error -> log.warn("自动关闭超时未支付订单失败：{}", error.getMessage(), error))
                .subscribe();
    }
}

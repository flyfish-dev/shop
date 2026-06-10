package group.flyfish.dev.shop.support;

import group.flyfish.dev.portal.api.PortalWorkbenchAction;
import group.flyfish.dev.portal.api.PortalWorkbenchSummary;
import group.flyfish.dev.portal.api.PortalWorkbenchSummaryProvider;
import group.flyfish.dev.shop.domain.po.ShopOrder;
import group.flyfish.dev.shop.domain.qo.ShopItemListQo;
import group.flyfish.dev.shop.repository.ShopItemRepository;
import group.flyfish.dev.shop.repository.ShopOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ShopWorkbenchSummaryProvider implements PortalWorkbenchSummaryProvider {

    private final ShopItemRepository shopItemRepository;
    private final ShopOrderRepository shopOrderRepository;

    @Override
    public String capability() {
        return "shop";
    }

    @Override
    public String actionName() {
        return "飞鱼小铺";
    }

    @Override
    public String actionPath() {
        return "/shop/item-list";
    }

    @Override
    public List<PortalWorkbenchAction> actions() {
        return List.of(
                new PortalWorkbenchAction(actionName(), actionPath(), actionStatus()),
                new PortalWorkbenchAction("商品管理", "/shop/manage/items", actionStatus())
        );
    }

    @Override
    public String metricLabel(String name) {
        return switch (name) {
            case "items" -> "商品";
            case "orders" -> "订单";
            case "pendingOrders" -> "待处理订单";
            default -> PortalWorkbenchSummaryProvider.super.metricLabel(name);
        };
    }

    @Override
    public Mono<PortalWorkbenchSummary> getSummary(Long userId) {
        Mono<Long> itemCount = shopItemRepository.count(new ShopItemListQo());
        Mono<List<ShopOrder>> userOrders = userId == null || userId < 0
                ? Mono.just(List.of())
                : shopOrderRepository.findAllByBuyerIdOrderByCreateTimeDesc(userId).collectList();
        return Mono.zip(itemCount, userOrders)
                .map(tuple -> new PortalWorkbenchSummary("shop", Map.of(
                        "items", tuple.getT1(),
                        "orders", (long) tuple.getT2().size(),
                        "pendingOrders", tuple.getT2().stream()
                                .filter(order -> order.getStatus() == ShopOrder.Status.PAYING
                                        || order.getStatus() == ShopOrder.Status.PAID)
                                .count()
                )));
    }
}

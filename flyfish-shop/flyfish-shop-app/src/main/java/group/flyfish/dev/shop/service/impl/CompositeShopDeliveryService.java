package group.flyfish.dev.shop.service.impl;

import group.flyfish.dev.shop.domain.po.ShopItem;
import group.flyfish.dev.shop.domain.po.ShopOrder;
import group.flyfish.dev.shop.service.DeliveryResult;
import group.flyfish.dev.shop.service.ShopDeliveryHandler;
import group.flyfish.dev.shop.service.ShopDeliveryService;
import group.flyfish.dev.auth.api.user.PortalUserVo;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Stream;

/**
 * 商品交付分发器。
 */
public class CompositeShopDeliveryService implements ShopDeliveryService {

    private final List<ShopDeliveryHandler> handlers;
    private final group.flyfish.dev.shop.xianyu.DeliveryTaskLedger ledger;

    public CompositeShopDeliveryService(List<ShopDeliveryHandler> handlers) {
        this(handlers, null);
    }

    public CompositeShopDeliveryService(List<ShopDeliveryHandler> handlers, group.flyfish.dev.shop.xianyu.DeliveryTaskLedger ledger) {
        this.handlers = handlers == null ? List.of() : List.copyOf(handlers);
        this.ledger = ledger;
    }

    @Override
    public Mono<DeliveryResult> deliver(ShopOrder order, ShopItem item, PortalUserVo buyer) {
        List<ShopDeliveryHandler> matchedHandlers = handlers.stream()
                .filter(handler -> handler.supports(item))
                .toList();
        if (matchedHandlers.isEmpty()) {
            return Mono.just(DeliveryResult.failed("该商品类型未实现自动交付"));
        }
        List<ShopDeliveryHandler> coreHandlers = matchedHandlers.stream()
                .filter(handler -> handler.stage() == ShopDeliveryHandler.Stage.CORE)
                .toList();
        if (coreHandlers.isEmpty()) {
            return Mono.just(DeliveryResult.failed("该商品未配置核心自动交付动作"));
        }
        List<ShopDeliveryHandler> afterSuccessHandlers = matchedHandlers.stream()
                .filter(handler -> handler.stage() == ShopDeliveryHandler.Stage.AFTER_SUCCESS)
                .toList();
        return execute(coreHandlers, order, item, buyer)
                .flatMap(coreResults -> {
                    DeliveryResult coreResult = mergeResults(coreResults);
                    if (!coreResult.isSuccess() || afterSuccessHandlers.isEmpty()) {
                        return Mono.just(coreResult);
                    }
                    return execute(afterSuccessHandlers, order, item, buyer)
                            .map(afterResults -> mergeResults(Stream.concat(
                                    coreResults.stream(), afterResults.stream()).toList()));
                });
    }

    private Mono<List<DeliveryResult>> execute(List<ShopDeliveryHandler> selectedHandlers,
                                               ShopOrder order, ShopItem item, PortalUserVo buyer) {
        return Flux.fromIterable(selectedHandlers)
                .concatMap(handler -> (ledger == null ? handler.deliver(order, item, buyer)
                        : ledger.run(order, "action:" + org.springframework.aop.support.AopUtils.getTargetClass(handler).getSimpleName(), handler.recoverySafe(), () -> handler.deliver(order, item, buyer)))
                        .onErrorResume(e -> Mono.just(DeliveryResult.failed(e.getMessage()))))
                .collectList();
    }

    private DeliveryResult mergeResults(List<DeliveryResult> results) {
        List<String> failures = results.stream()
                .filter(result -> !result.isSuccess())
                .map(DeliveryResult::getMessage)
                .filter(message -> message != null && !message.isBlank())
                .toList();
        if (!failures.isEmpty()) {
            return DeliveryResult.failed("自动交付失败：" + String.join("；", failures));
        }
        String message = results.stream()
                .map(DeliveryResult::getMessage)
                .filter(value -> value != null && !value.isBlank())
                .reduce((left, right) -> left + "；" + right)
                .orElse("自动交付完成");
        return DeliveryResult.ok(message);
    }
}

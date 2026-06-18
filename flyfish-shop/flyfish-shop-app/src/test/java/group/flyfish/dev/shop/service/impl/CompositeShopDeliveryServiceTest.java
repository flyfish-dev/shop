package group.flyfish.dev.shop.service.impl;

import group.flyfish.dev.auth.api.user.PortalUserVo;
import group.flyfish.dev.shop.domain.po.ShopItem;
import group.flyfish.dev.shop.domain.po.ShopOrder;
import group.flyfish.dev.shop.service.DeliveryResult;
import group.flyfish.dev.shop.service.ShopDeliveryHandler;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompositeShopDeliveryServiceTest {

    @Test
    void deliversAllMatchedActionsInOrder() {
        AtomicInteger firstCalls = new AtomicInteger();
        AtomicInteger secondCalls = new AtomicInteger();
        CompositeShopDeliveryService service = new CompositeShopDeliveryService(List.of(
                handler("仓库已开通", firstCalls),
                handler("授权已发放", secondCalls)
        ));

        StepVerifier.create(service.deliver(new ShopOrder(), new ShopItem(), new PortalUserVo()))
                .assertNext(result -> {
                    assertTrue(result.isSuccess());
                    assertTrue(result.getMessage().contains("仓库已开通"));
                    assertTrue(result.getMessage().contains("授权已发放"));
                })
                .verifyComplete();

        assertEquals(1, firstCalls.get());
        assertEquals(1, secondCalls.get());
    }

    private ShopDeliveryHandler handler(String message, AtomicInteger calls) {
        return new ShopDeliveryHandler() {
            @Override
            public boolean supports(ShopItem item) {
                return true;
            }

            @Override
            public Mono<DeliveryResult> deliver(ShopOrder order, ShopItem item, PortalUserVo buyer) {
                calls.incrementAndGet();
                return Mono.just(DeliveryResult.ok(message));
            }
        };
    }
}

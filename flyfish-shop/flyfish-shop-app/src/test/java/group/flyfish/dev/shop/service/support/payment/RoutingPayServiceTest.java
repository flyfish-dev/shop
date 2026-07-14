package group.flyfish.dev.shop.service.support.payment;

import group.flyfish.dev.shop.domain.dto.ShopOrderDto;
import group.flyfish.dev.shop.domain.po.ShopItem;
import group.flyfish.dev.shop.domain.po.ShopOrder;
import group.flyfish.dev.shop.domain.vo.ShopOrderPaymentVo;
import group.flyfish.dev.shop.service.PayService;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoutingPayServiceTest {

    @Test
    void routesExplicitStripeProviderToStripe() {
        RoutingPayService service = new RoutingPayService(List.of(provider("h5zhifu"), provider("stripe")));
        ShopOrderDto request = new ShopOrderDto();
        request.setPaymentProvider("stripe");

        StepVerifier.create(service.pay(new ShopOrder(), new ShopItem(), request))
                .assertNext(payment -> assertEquals("stripe", payment.getProvider()))
                .verifyComplete();
    }

    @Test
    void keepsLegacyRequestsOnH5ZhiFu() {
        RoutingPayService service = new RoutingPayService(List.of(provider("h5zhifu"), provider("stripe")));
        ShopOrderDto request = new ShopOrderDto();
        request.setPayType("wechat");
        request.setTradeType("native");

        StepVerifier.create(service.pay(new ShopOrder(), new ShopItem(), request))
                .assertNext(payment -> assertEquals("h5zhifu", payment.getProvider()))
                .verifyComplete();
    }

    @Test
    void treatsStripePayTypeAsStripeForBackwardCompatibility() {
        RoutingPayService service = new RoutingPayService(List.of(provider("h5zhifu"), provider("stripe")));
        ShopOrderDto request = new ShopOrderDto();
        request.setPayType("stripe");

        StepVerifier.create(service.pay(new ShopOrder(), new ShopItem(), request))
                .assertNext(payment -> assertEquals("stripe", payment.getProvider()))
                .verifyComplete();
    }

    private PayService provider(String providerCode) {
        return new PayService() {
            @Override
            public String providerCode() {
                return providerCode;
            }

            @Override
            public Mono<ShopOrderPaymentVo> pay(ShopOrder order, ShopItem item, ShopOrderDto request) {
                ShopOrderPaymentVo payment = new ShopOrderPaymentVo();
                payment.setProvider(providerCode);
                return Mono.just(payment);
            }
        };
    }
}

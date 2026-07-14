package group.flyfish.dev.shop.controller;

import group.flyfish.dev.common.bean.Result;
import group.flyfish.dev.shop.service.support.payment.PaymentMethodAvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("shops/payment-methods")
@RequiredArgsConstructor
public class ShopPaymentMethodController {

    private final PaymentMethodAvailabilityService paymentMethodAvailabilityService;

    @GetMapping
    public Mono<Result<List<String>>> getPaymentMethods() {
        return Mono.just(Result.ok(paymentMethodAvailabilityService.getAvailableMethods()));
    }
}

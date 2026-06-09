package group.flyfish.dev.shop.controller;

import group.flyfish.dev.shop.service.ShopOrderService;
import group.flyfish.dev.shop.service.ShopService;
import group.flyfish.dev.shop.service.support.h5zhifu.bean.H5ZhiFuNotifyDto;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopControllerTest {

    @Test
    void jsonH5ZhiFuNotifyKeepsOriginalPaymentFields() {
        ShopOrderService orderService = mock(ShopOrderService.class);
        when(orderService.handlePaymentNotify(any(H5ZhiFuNotifyDto.class))).thenReturn(Mono.empty());
        ShopController controller = new ShopController(mock(ShopService.class), orderService);

        StepVerifier.create(controller.h5ZhiFuNotify(Map.of(
                        "app_id", 10001,
                        "trade_no", "T100",
                        "out_trade_no", "FF100",
                        "amount", 1990,
                        "pay_time", "2026-06-08 10:00:00",
                        "sign", "SIGN"
                )))
                .expectNext("success")
                .verifyComplete();

        ArgumentCaptor<H5ZhiFuNotifyDto> captor = ArgumentCaptor.forClass(H5ZhiFuNotifyDto.class);
        verify(orderService).handlePaymentNotify(captor.capture());
        H5ZhiFuNotifyDto dto = captor.getValue();
        assertEquals(10001L, dto.getAppId());
        assertEquals("T100", dto.getTradeNo());
        assertEquals("FF100", dto.getOutTradeNo());
        assertEquals(1990, dto.getAmount());
        assertEquals("2026-06-08 10:00:00", dto.getPayTime());
        assertEquals("SIGN", dto.getSign());
        assertEquals("FF100", dto.getRawParams().get("out_trade_no"));
    }

    @Test
    void formH5ZhiFuNotifyKeepsOriginalPaymentFields() {
        ShopOrderService orderService = mock(ShopOrderService.class);
        when(orderService.handlePaymentNotify(any(H5ZhiFuNotifyDto.class))).thenReturn(Mono.empty());
        ShopController controller = new ShopController(mock(ShopService.class), orderService);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/shops/payments/h5zhifu/notify")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("app_id=10001&trade_no=T100&out_trade_no=FF100&amount=1990&pay_time=2026-06-08+10%3A00%3A00&sign=SIGN"));

        StepVerifier.create(controller.h5ZhiFuNotifyForm(exchange))
                .expectNext("success")
                .verifyComplete();

        ArgumentCaptor<H5ZhiFuNotifyDto> captor = ArgumentCaptor.forClass(H5ZhiFuNotifyDto.class);
        verify(orderService).handlePaymentNotify(captor.capture());
        H5ZhiFuNotifyDto dto = captor.getValue();
        assertEquals(10001L, dto.getAppId());
        assertEquals("T100", dto.getTradeNo());
        assertEquals("FF100", dto.getOutTradeNo());
        assertEquals(1990, dto.getAmount());
        assertEquals("2026-06-08 10:00:00", dto.getPayTime());
        assertEquals("SIGN", dto.getSign());
        assertEquals("FF100", dto.getRawParams().get("out_trade_no"));
    }
}

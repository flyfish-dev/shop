package group.flyfish.dev.shop.service.support.h5zhifu;

import group.flyfish.dev.shop.domain.dto.ShopOrderDto;
import group.flyfish.dev.shop.domain.po.ShopItem;
import group.flyfish.dev.shop.domain.po.ShopOrder;
import group.flyfish.dev.shop.service.support.h5zhifu.bean.H5ZhiFuPayDto;
import group.flyfish.dev.shop.service.support.h5zhifu.bean.H5ZhiFuPayResultDto;
import group.flyfish.dev.shop.service.support.h5zhifu.bean.H5ZhiFuResultDto;
import group.flyfish.dev.shop.service.support.h5zhifu.config.H5ZhiFuProperties;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class H5ZhiFuPayServiceTest {

    @Test
    void routesMobileH5PaymentThroughHttpInterface() {
        StubH5ZhiFuService client = new StubH5ZhiFuService();
        H5ZhiFuPayService service = new H5ZhiFuPayService(client, properties());

        ShopOrderDto request = new ShopOrderDto();
        request.setTradeType("h5");
        request.setPayType("alipay");

        StepVerifier.create(service.pay(order(), item(), request))
                .assertNext(payment -> {
                    assertEquals("h5", payment.getTradeType());
                    assertEquals("alipay", payment.getPayType());
                    assertEquals("https://pay.example/h5", payment.getJumpUrl());
                })
                .verifyComplete();

        assertEquals("h5", client.invokedRef.get());
        assertEquals(H5ZhiFuPayDto.PayType.alipay, client.dtoRef.get().getPayType());
        assertEquals(1990, client.dtoRef.get().getAmount());
    }

    @Test
    void forcesWechatWhenUsingJsapiPayment() {
        StubH5ZhiFuService client = new StubH5ZhiFuService();
        H5ZhiFuPayService service = new H5ZhiFuPayService(client, properties());

        ShopOrderDto request = new ShopOrderDto();
        request.setTradeType("jsapi");
        request.setPayType("alipay");

        StepVerifier.create(service.pay(order(), item(), request))
                .assertNext(payment -> {
                    assertEquals("jsapi", payment.getTradeType());
                    assertEquals("wechat", payment.getPayType());
                })
                .verifyComplete();

        assertEquals("jsapi", client.invokedRef.get());
        assertEquals(H5ZhiFuPayDto.PayType.wechat, client.dtoRef.get().getPayType());
    }

    private H5ZhiFuProperties properties() {
        H5ZhiFuProperties properties = new H5ZhiFuProperties();
        properties.setAppId(0L);
        properties.setKey("test-secret");
        properties.setNotifyUrl("https://api.example.com/shops/payments/h5zhifu/notify");
        return properties;
    }

    private ShopOrder order() {
        ShopOrder order = new ShopOrder();
        order.setOrderNo("ORDER-1");
        order.setAmount(new BigDecimal("19.90"));
        return order;
    }

    private ShopItem item() {
        ShopItem item = new ShopItem();
        item.setName("H5支付测试商品");
        return item;
    }

    private H5ZhiFuResultDto<H5ZhiFuPayResultDto> success(String jumpUrl, String codeUrl) {
        H5ZhiFuPayResultDto data = new H5ZhiFuPayResultDto();
        data.setTradeNo("T202605070001");
        data.setJumpUrl(jumpUrl);
        data.setCodeUrl(codeUrl);

        H5ZhiFuResultDto<H5ZhiFuPayResultDto> result = new H5ZhiFuResultDto<>();
        result.setCode(H5ZhiFuResultDto.ResultCode.SUCCESS);
        result.setMsg("success");
        result.setData(data);
        return result;
    }

    private class StubH5ZhiFuService implements H5ZhiFuService {

        private final AtomicReference<String> invokedRef = new AtomicReference<>();
        private final AtomicReference<H5ZhiFuPayDto> dtoRef = new AtomicReference<>();

        @Override
        public Mono<H5ZhiFuResultDto<H5ZhiFuPayResultDto>> nativePay(H5ZhiFuPayDto dto) {
            invokedRef.set("native");
            dtoRef.set(dto);
            return Mono.just(success(null, "weixin://native"));
        }

        @Override
        public Mono<H5ZhiFuResultDto<H5ZhiFuPayResultDto>> h5Pay(H5ZhiFuPayDto dto) {
            invokedRef.set("h5");
            dtoRef.set(dto);
            return Mono.just(success("https://pay.example/h5", null));
        }

        @Override
        public Mono<H5ZhiFuResultDto<H5ZhiFuPayResultDto>> jsapiPay(H5ZhiFuPayDto dto) {
            invokedRef.set("jsapi");
            dtoRef.set(dto);
            return Mono.just(success("https://pay.example/jsapi", null));
        }
    }
}

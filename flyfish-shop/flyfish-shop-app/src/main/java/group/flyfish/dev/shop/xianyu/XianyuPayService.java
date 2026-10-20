package group.flyfish.dev.shop.xianyu;

import group.flyfish.dev.shop.domain.po.ShopItem;
import group.flyfish.dev.shop.domain.po.ShopOrder;
import group.flyfish.dev.shop.domain.dto.ShopOrderDto;
import group.flyfish.dev.shop.domain.vo.ShopOrderPaymentVo;
import group.flyfish.dev.shop.service.PayService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class XianyuPayService implements PayService {
    @Override public String providerCode() { return "xianyu"; }
    @Override public Mono<ShopOrderPaymentVo> pay(ShopOrder order, ShopItem item, ShopOrderDto dto) {
        ShopOrderPaymentVo payment = new ShopOrderPaymentVo();
        payment.setProvider("xianyu"); payment.setTradeType("miniapp"); payment.setPayType("xianyu");
        payment.setAction("XIANYU_PENDING"); payment.setTradeNo(order.getChannelOrderKey());
        payment.setExpireTime(order.getExpireTime());
        return Mono.just(payment);
    }
}

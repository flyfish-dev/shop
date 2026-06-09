package group.flyfish.dev.shop.domain.vo;

import lombok.Data;

@Data
public class ShopOrderCreateVo {

    private ShopOrderVo order;

    private ShopOrderPaymentVo payment;
}

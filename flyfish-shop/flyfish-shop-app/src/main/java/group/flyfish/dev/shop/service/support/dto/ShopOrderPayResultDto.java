package group.flyfish.dev.shop.service.support.dto;

import group.flyfish.dev.annotations.data.Property;
import lombok.Data;

/**
 * 门店订单支付结果
 *
 * @author wangyu
 */
@Data
public class ShopOrderPayResultDto {

    @Property("二维码地址")
    private String qrcode;

    @Property("付款链接")
    private String url;
}

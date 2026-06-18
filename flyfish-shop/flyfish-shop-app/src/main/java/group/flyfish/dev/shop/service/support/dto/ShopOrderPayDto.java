package group.flyfish.dev.shop.service.support.dto;

import group.flyfish.dev.annotations.data.Property;
import lombok.Data;

/**
 * 门店订单支付
 *
 * @author wangyu
 */
@Data
public class ShopOrderPayDto {

    @Property("商品id")
    private String itemId;

    @Property("商品数量")
    private Integer count;
}

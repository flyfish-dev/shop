package group.flyfish.dev.shop.domain.dto;

import group.flyfish.dev.shop.domain.po.ShopOrder;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理员处理订单交付结果。
 */
@Data
public class ShopOrderDeliveryDto {

    @NotNull(message = "交付状态不能为空")
    private ShopOrder.DeliveryStatus deliveryStatus;

    private String deliveryMessage;
}

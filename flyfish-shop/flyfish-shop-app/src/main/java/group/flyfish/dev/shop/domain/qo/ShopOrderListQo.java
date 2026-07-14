package group.flyfish.dev.shop.domain.qo;

import group.flyfish.dev.common.base.reactive.BaseQo;
import group.flyfish.dev.shop.domain.po.ShopOrder;
import lombok.Data;
import org.springframework.data.domain.Sort;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 小铺订单列表查询条件。
 */
@Data
public class ShopOrderListQo extends BaseQo<ShopOrder> {

    private Long buyerId;

    private Long itemId;

    private ShopOrder.DeliveryStatus deliveryStatus;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    @Override
    public Criteria getCriteria() {
        Criteria criteria = Criteria.empty();
        if (buyerId != null) {
            criteria = criteria.and("buyer_id").is(buyerId);
        }
        if (itemId != null) {
            criteria = criteria.and("item_id").is(itemId);
        }
        if (deliveryStatus != null) {
            criteria = criteria.and("delivery_status").is(deliveryStatus.name());
        }
        if (startTime != null) {
            criteria = criteria.and("create_time").greaterThanOrEquals(startTime);
        }
        if (endTime != null) {
            criteria = criteria.and("create_time").lessThanOrEquals(endTime);
        }
        return criteria;
    }

    @Override
    public Sort sorts() {
        return Sort.by(Sort.Order.desc("create_time"), Sort.Order.desc("id"));
    }
}

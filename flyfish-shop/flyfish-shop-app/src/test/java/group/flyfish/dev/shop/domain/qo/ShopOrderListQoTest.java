package group.flyfish.dev.shop.domain.qo;

import group.flyfish.dev.shop.domain.po.ShopOrder;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopOrderListQoTest {

    @Test
    void filtersUseDatabaseColumnsAndEnumNames() {
        ShopOrderListQo qo = new ShopOrderListQo();
        qo.setBuyerId(100L);
        qo.setItemId(1008L);
        qo.setDeliveryStatus(ShopOrder.DeliveryStatus.SUCCESS);
        qo.setStartTime(LocalDateTime.of(2026, 6, 1, 0, 0));
        qo.setEndTime(LocalDateTime.of(2026, 7, 3, 23, 59, 59));

        String criteria = qo.getCriteria().toString();

        assertTrue(criteria.contains("buyer_id"));
        assertTrue(criteria.contains("item_id"));
        assertTrue(criteria.contains("delivery_status"));
        assertTrue(criteria.contains("SUCCESS"));
        assertTrue(criteria.contains("create_time"));
    }

    @Test
    void defaultSortShowsNewestOrdersFirst() {
        ShopOrderListQo qo = new ShopOrderListQo();

        List<Sort.Order> orders = qo.sorts().stream().toList();

        assertOrder(orders.get(0), "create_time", Sort.Direction.DESC);
        assertOrder(orders.get(1), "id", Sort.Direction.DESC);
    }

    private static void assertOrder(Sort.Order order, String property, Sort.Direction direction) {
        assertEquals(property, order.getProperty());
        assertEquals(direction, order.getDirection());
    }
}

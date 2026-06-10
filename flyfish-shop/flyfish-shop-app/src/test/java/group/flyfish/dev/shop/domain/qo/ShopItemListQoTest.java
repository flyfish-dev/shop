package group.flyfish.dev.shop.domain.qo;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShopItemListQoTest {

    @Test
    void defaultSortKeepsPinnedAndManualOrderFirst() {
        ShopItemListQo qo = new ShopItemListQo();

        List<Sort.Order> orders = qo.sorts().stream().toList();

        assertOrder(orders.get(0), "pinned", Sort.Direction.DESC);
        assertOrder(orders.get(1), "sort", Sort.Direction.ASC);
        assertOrder(orders.get(2), "recommended", Sort.Direction.DESC);
        assertOrder(orders.get(3), "update_time", Sort.Direction.DESC);
        assertOrder(orders.get(4), "id", Sort.Direction.DESC);
    }

    @Test
    void explicitSortStillKeepsPinnedItemsAhead() {
        ShopItemListQo qo = new ShopItemListQo();
        qo.setOrder(ShopItemListQo.Order.PRICE_ASC);

        List<Sort.Order> orders = qo.sorts().stream().toList();

        assertOrder(orders.get(0), "pinned", Sort.Direction.DESC);
        assertOrder(orders.get(1), "price", Sort.Direction.ASC);
        assertOrder(orders.get(2), "sort", Sort.Direction.ASC);
        assertOrder(orders.get(3), "id", Sort.Direction.DESC);
    }

    private static void assertOrder(Sort.Order order, String property, Sort.Direction direction) {
        assertEquals(property, order.getProperty());
        assertEquals(direction, order.getDirection());
    }
}

package group.flyfish.dev.shop.domain.qo;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import group.flyfish.dev.shop.domain.po.ShopItem;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void keywordSearchMatchesPrimaryAndLocalizedFields() {
        ShopItemListQo qo = new ShopItemListQo();
        qo.setKeyword(" office ");

        String criteria = qo.getCriteria().toString();

        assertTrue(criteria.contains("name"));
        assertTrue(criteria.contains("tags"));
        assertTrue(criteria.contains("description"));
        assertTrue(criteria.contains("i18n"));
        assertTrue(criteria.contains("%office%"));
    }

    @Test
    void typeFilterUsesEnumNameAsQueryValue() {
        ShopItemListQo qo = new ShopItemListQo();
        qo.setType(ShopItem.Type.DONATION);

        String criteria = qo.getCriteria().toString();

        assertTrue(criteria.contains("type"));
        assertTrue(criteria.contains("DONATION"));
    }

    private static void assertOrder(Sort.Order order, String property, Sort.Direction direction) {
        assertEquals(property, order.getProperty());
        assertEquals(direction, order.getDirection());
    }
}

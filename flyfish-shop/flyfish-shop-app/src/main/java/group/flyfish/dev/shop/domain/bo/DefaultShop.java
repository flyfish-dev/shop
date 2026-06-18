package group.flyfish.dev.shop.domain.bo;

import group.flyfish.dev.shop.domain.po.Shop;

public class DefaultShop {

    public static Shop defaultShop() {
        Shop shop = new Shop();
        shop.setId(1L);
        shop.setName("飞鱼小铺");
        return shop;
    }
}

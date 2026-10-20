package group.flyfish.dev.shop.repository;

import group.flyfish.dev.common.repository.DefaultReactiveRepository;
import group.flyfish.dev.shop.domain.po.ShopItem;

/**
 * 店铺商品仓库
 *
 * @author wangyu
 */
public interface ShopItemRepository extends DefaultReactiveRepository<ShopItem> {
    @org.springframework.data.r2dbc.repository.Modifying
    @org.springframework.data.r2dbc.repository.Query("UPDATE shop_item SET buy_count=COALESCE(buy_count,0)+:count WHERE id=:id")
    reactor.core.publisher.Mono<Integer> incrementBuyCount(Long id, int count);
}

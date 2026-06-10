package group.flyfish.dev.shop.service;

import group.flyfish.dev.shop.domain.dto.*;
import group.flyfish.dev.shop.domain.po.Shop;
import reactor.core.publisher.Mono;

public interface ShopManageService {

    Mono<Shop> createShop(ShopCreateDto dto);

    Mono<Shop> updateShop(Long id, ShopUpdateDto dto);

    Mono<Void> deleteShop(Long id);

    Mono<Void> createItemGroup(ShopItemGroupCreateDto dto);

    Mono<Void> updateItemGroup(Long id, ShopItemGroupUpdateDto dto);

    Mono<Void> deleteItemGroup(Long id);

    Mono<Void> createItem(ShopItemCreateDto dto);

    Mono<Void> updateItem(Long id, ShopItemUpdateDto dto);

    Mono<Void> deleteItem(Long id);
}

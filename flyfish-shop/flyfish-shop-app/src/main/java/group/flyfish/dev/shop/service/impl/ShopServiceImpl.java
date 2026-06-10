package group.flyfish.dev.shop.service.impl;

import group.flyfish.dev.shop.convert.ShopConvert;
import group.flyfish.dev.shop.domain.bo.DefaultShop;
import group.flyfish.dev.shop.domain.po.Shop;
import group.flyfish.dev.shop.domain.qo.ShopItemGroupListQo;
import group.flyfish.dev.shop.domain.qo.ShopItemListQo;
import group.flyfish.dev.shop.domain.vo.ShopItemDetailVo;
import group.flyfish.dev.shop.domain.vo.ShopItemGroupListVo;
import group.flyfish.dev.shop.domain.vo.ShopItemListVo;
import group.flyfish.dev.shop.repository.ShopItemGroupRepository;
import group.flyfish.dev.shop.repository.ShopItemRepository;
import group.flyfish.dev.shop.repository.ShopRepository;
import group.flyfish.dev.shop.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 店铺服务
 *
 * @author wangyu
 */
@Service
@RequiredArgsConstructor
public class ShopServiceImpl implements ShopService {

    private final ShopRepository shopRepository;
    private final ShopItemGroupRepository shopItemGroupRepository;
    private final ShopItemRepository shopItemRepository;
    private final ShopConvert shopConvert;

    /**
     * 获取当前店铺，如果没有店铺，创建飞鱼小铺
     *
     * @return 结果
     */
    @Override
    public Mono<Shop> getCurrentShop() {
        return shopRepository.findAll().next().defaultIfEmpty(DefaultShop.defaultShop());
    }

    /**
     * 获取商品分组列表
     *
     * @param shopId 店铺id
     * @return 结果
     */
    @Override
    public Flux<ShopItemGroupListVo> getItemGroupList(ShopItemGroupListQo qo) {
        return Mono.justOrEmpty(qo)
                .switchIfEmpty(Mono.defer(() -> getCurrentShop().map(shop -> {
                    ShopItemGroupListQo alternative = new ShopItemGroupListQo();
                    alternative.setShopId(shop.getId());
                    return alternative;
                })))
                .flatMapMany(shopItemGroupRepository::findAll)
                .map(shopConvert::convert);
    }

    /**
     * 获取商品列表
     *
     * @param qo 查询
     * @return 结果
     */
    @Override
    public Mono<Page<ShopItemListVo>> getItemList(ShopItemListQo qo) {
        return shopItemRepository.findAll(qo, qo.getPageable())
                .map(page -> page.map(shopConvert::toItemList));
    }

    /**
     * 查询商品详情
     *
     * @param id 商品id
     * @return 结果
     */
    @Override
    public Mono<ShopItemDetailVo> getItemDetail(Long id) {
        return shopItemRepository.findById(id).map(shopConvert::toItemDetail);
    }
}

package group.flyfish.dev.shop.service;

import group.flyfish.dev.shop.domain.po.Shop;
import group.flyfish.dev.shop.domain.qo.ShopItemGroupListQo;
import group.flyfish.dev.shop.domain.qo.ShopItemListQo;
import group.flyfish.dev.shop.domain.vo.ShopItemDetailVo;
import group.flyfish.dev.shop.domain.vo.ShopItemGroupListVo;
import group.flyfish.dev.shop.domain.vo.ShopItemListVo;
import org.springframework.data.domain.Page;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 商铺服务
 *
 * @author wangyu
 * 封装了和支付系统的交互
 */
public interface ShopService {

    /**
     * 获取当前店铺，如果没有店铺，创建飞鱼小铺
     *
     * @return 结果
     */
    Mono<Shop> getCurrentShop();

    /**
     * 获取商品分组列表
     *
     * @param shopId 店铺id
     * @return 结果
     */
    Flux<ShopItemGroupListVo> getItemGroupList(ShopItemGroupListQo qo);

    /**
     * 获取商品列表
     *
     * @param qo 查询
     * @return 结果
     */
    Mono<Page<ShopItemListVo>> getItemList(ShopItemListQo qo);

    /**
     * 查询商品详情
     *
     * @param id 商品id
     * @return 结果
     */
    Mono<ShopItemDetailVo> getItemDetail(Long id);
}

package group.flyfish.dev.shop.service;

import reactor.core.publisher.Mono;

/**
 * 商品查看统计服务。
 */
public interface ShopItemViewStatService {

    /**
     * 记录一次商品查看并返回当前可见查看数。
     *
     * @param itemId 商品id
     * @return 数据库累计值与内存待刷盘值合并后的查看数
     */
    Mono<Long> recordAndGetViewCount(Long itemId);

    /**
     * 返回当前可见查看数。
     *
     * @param itemId 商品id
     * @return 数据库累计值与内存待刷盘值合并后的查看数
     */
    Mono<Long> getViewCount(Long itemId);

    /**
     * 将内存中的阶段性查看量刷入数据库。
     *
     * @return 完成信号
     */
    Mono<Void> flushPendingViews();
}

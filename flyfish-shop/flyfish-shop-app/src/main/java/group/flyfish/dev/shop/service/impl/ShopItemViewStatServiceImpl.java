package group.flyfish.dev.shop.service.impl;

import group.flyfish.dev.shop.repository.ShopItemViewStatRepository;
import group.flyfish.dev.shop.service.ShopItemViewStatService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 商品查看统计服务实现。
 *
 * <p>详情页每次打开只写入内存计数器，达到阈值或定时任务触发时再合并刷入
 * {@code shop_item_view_stat}。这样突发访问只会增加内存原子计数，不会把数据库压成高频更新。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopItemViewStatServiceImpl implements ShopItemViewStatService {

    private static final Duration SHUTDOWN_FLUSH_TIMEOUT = Duration.ofSeconds(5);

    private final ShopItemViewStatRepository shopItemViewStatRepository;
    private final ConcurrentHashMap<Long, AtomicLong> pendingViews = new ConcurrentHashMap<>();
    private final AtomicLong pendingTotal = new AtomicLong();
    private final AtomicBoolean flushing = new AtomicBoolean(false);

    @Value("${flyfish.shop.item-view.flush-threshold:1000}")
    private long flushThreshold = 1000;

    @Override
    public Mono<Long> recordAndGetViewCount(Long itemId) {
        if (itemId == null) {
            return Mono.just(0L);
        }
        long total = pendingViews.computeIfAbsent(itemId, key -> new AtomicLong()).incrementAndGet();
        long pending = pendingTotal.incrementAndGet();
        return getViewCount(itemId)
                .doFinally(signal -> {
                    if (pending >= flushThreshold || total >= flushThreshold) {
                        triggerFlush();
                    }
                });
    }

    @Override
    public Mono<Long> getViewCount(Long itemId) {
        if (itemId == null) {
            return Mono.just(0L);
        }
        return shopItemViewStatRepository.findViewCountByItemId(itemId)
                .defaultIfEmpty(0L)
                .map(viewCount -> viewCount + pendingCount(itemId));
    }

    @Override
    public Mono<Void> flushPendingViews() {
        Map<Long, Long> drained = drainPendingViews();
        if (drained.isEmpty()) {
            return Mono.empty();
        }
        return Flux.fromIterable(drained.entrySet())
                .concatMap(entry -> flushItemViewCount(entry.getKey(), entry.getValue()))
                .then()
                .doOnSuccess(ignored -> log.debug("商品查看统计已刷盘：{} 个商品，{} 次查看",
                        drained.size(), drained.values().stream().mapToLong(Long::longValue).sum()))
                .doOnError(error -> {
                    restorePendingViews(drained);
                    log.warn("商品查看统计刷盘失败，已回滚到内存等待下次重试：{}", error.getMessage(), error);
                });
    }

    @Scheduled(
            fixedDelayString = "${flyfish.shop.item-view.flush-delay:10000}",
            initialDelayString = "${flyfish.shop.item-view.flush-initial-delay:10000}"
    )
    public void flushBySchedule() {
        triggerFlush();
    }

    @PreDestroy
    public void flushBeforeShutdown() {
        if (pendingTotal.get() <= 0) {
            return;
        }
        try {
            flushPendingViews().block(SHUTDOWN_FLUSH_TIMEOUT);
        } catch (RuntimeException e) {
            log.warn("应用关闭前刷盘商品查看统计失败，可能会丢失本轮未落库查看量：{}", e.getMessage(), e);
        }
    }

    private Mono<Integer> flushItemViewCount(Long itemId, Long delta) {
        return shopItemViewStatRepository.increaseViewCount(itemId, delta)
                .flatMap(updated -> updated > 0
                        ? Mono.just(updated)
                        : shopItemViewStatRepository.insertInitialViewCount(itemId, delta)
                        .onErrorResume(DuplicateKeyException.class,
                                error -> shopItemViewStatRepository.increaseViewCount(itemId, delta)));
    }

    private void triggerFlush() {
        if (pendingTotal.get() <= 0 || !flushing.compareAndSet(false, true)) {
            return;
        }
        flushPendingViews()
                .doFinally(signal -> flushing.set(false))
                .subscribe(
                        ignored -> {
                        },
                        error -> log.warn("商品查看统计异步刷盘失败：{}", error.getMessage(), error)
                );
    }

    private long pendingCount(Long itemId) {
        AtomicLong counter = pendingViews.get(itemId);
        return counter == null ? 0 : counter.get();
    }

    private Map<Long, Long> drainPendingViews() {
        Map<Long, Long> drained = new LinkedHashMap<>();
        pendingViews.forEach((itemId, counter) -> {
            long delta = counter.getAndSet(0);
            if (delta > 0) {
                drained.put(itemId, delta);
            }
            if (counter.get() == 0) {
                pendingViews.remove(itemId, counter);
            }
        });
        long total = drained.values().stream().mapToLong(Long::longValue).sum();
        if (total > 0) {
            pendingTotal.addAndGet(-total);
        }
        return drained;
    }

    private void restorePendingViews(Map<Long, Long> drained) {
        drained.forEach((itemId, delta) -> pendingViews
                .computeIfAbsent(itemId, key -> new AtomicLong())
                .addAndGet(delta));
        long total = drained.values().stream().mapToLong(Long::longValue).sum();
        if (total > 0) {
            pendingTotal.addAndGet(total);
        }
    }
}

package group.flyfish.dev.customer.service;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class CustomerPresenceRegistry {

    private final ConcurrentMap<Long, AtomicInteger> onlineSessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, LocalDateTime> lastActiveTimes = new ConcurrentHashMap<>();
    private final AtomicInteger managerOnlineSessions = new AtomicInteger();

    public Runnable registerCustomer(Long userId) {
        if (userId == null || userId <= 0) {
            return () -> {
            };
        }
        AtomicBoolean released = new AtomicBoolean(false);
        touchCustomer(userId);
        onlineSessions.compute(userId, (ignored, counter) -> {
            AtomicInteger value = counter == null ? new AtomicInteger(0) : counter;
            value.incrementAndGet();
            return value;
        });
        return () -> {
            if (!released.compareAndSet(false, true)) {
                return;
            }
            AtomicInteger counter = onlineSessions.get(userId);
            if (counter != null && counter.decrementAndGet() <= 0) {
                onlineSessions.remove(userId, counter);
            }
            touchCustomer(userId);
        };
    }

    public void touchCustomer(Long userId) {
        if (userId != null && userId > 0) {
            lastActiveTimes.put(userId, LocalDateTime.now());
        }
    }

    public boolean isCustomerOnline(Long userId) {
        AtomicInteger counter = userId == null ? null : onlineSessions.get(userId);
        return counter != null && counter.get() > 0;
    }

    public Optional<LocalDateTime> lastActiveTime(Long userId) {
        return Optional.ofNullable(userId == null ? null : lastActiveTimes.get(userId));
    }

    /**
     * 登记一个管理员客服 WebSocket 会话，并返回幂等的释放动作。
     *
     * <p>管理员可能同时打开多个标签页，因此这里按连接数计数；只有最后一个连接释放后，
     * 才会把客服判定为离线。</p>
     */
    public Runnable registerManager() {
        AtomicBoolean released = new AtomicBoolean(false);
        managerOnlineSessions.incrementAndGet();
        return () -> {
            if (released.compareAndSet(false, true)) {
                managerOnlineSessions.updateAndGet(value -> Math.max(0, value - 1));
            }
        };
    }

    public boolean isManagerOnline() {
        return managerOnlineSessions.get() > 0;
    }
}

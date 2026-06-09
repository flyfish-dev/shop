package group.flyfish.dev.common.reactive;

/**
 * Reactor 虚拟线程调度器支持。
 *
 * @author wangyu
 */
public final class ReactorVirtualThreadSupport {

    private static final String REACTOR_BOUNDED_ELASTIC_VIRTUAL_THREADS =
            "reactor.schedulers.defaultBoundedElasticOnVirtualThreads";

    private ReactorVirtualThreadSupport() {
    }

    public static void enableByDefault() {
        // Spring Boot 的 spring.threads.virtual.enabled 负责 Boot 托管的执行器；
        // Reactor 的 boundedElastic 是独立全局调度器，需要在首次初始化前设置系统属性。
        // 只在用户没有显式 -D 覆盖时默认开启，便于生产环境按需回退。
        System.setProperty(REACTOR_BOUNDED_ELASTIC_VIRTUAL_THREADS,
                System.getProperty(REACTOR_BOUNDED_ELASTIC_VIRTUAL_THREADS, "true"));
    }
}

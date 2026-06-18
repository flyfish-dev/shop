package group.flyfish.dev.common.config;

import group.flyfish.dev.common.http.ApiNoStorePathProvider;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 动态接口缓存控制。
 *
 * <p>登录态、订单、工单、客服消息等动态接口会随着 Authorization、会话和业务状态即时变化。浏览器
 * 或中间代理一旦复用旧响应，就可能出现“已经登录但仍显示未登录”等问题。因此各模块通过
 * {@link ApiNoStorePathProvider} 声明自己的动态接口前缀，由这个公共过滤器统一写入 no-store 响应头。</p>
 */
@Configuration(proxyBeanMethods = false)
public class ApiCacheControlConfig {

    private static final String NO_STORE = "no-store, no-cache, must-revalidate, private";

    @Bean
    public WebFilter apiNoStoreCacheControlFilter(ObjectProvider<ApiNoStorePathProvider> pathProviders) {
        List<String> prefixes = pathProviders.orderedStream()
                .flatMap(provider -> provider.prefixes().stream())
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
        return (exchange, chain) -> {
            if (isWebSocketUpgrade(exchange.getRequest())) {
                return chain.filter(exchange);
            }
            String path = exchange.getRequest().getPath().pathWithinApplication().value();
            if (!isDynamicApi(path, prefixes)) {
                return chain.filter(exchange);
            }
            exchange.getResponse().beforeCommit(() -> {
                HttpHeaders headers = exchange.getResponse().getHeaders();
                headers.set(HttpHeaders.CACHE_CONTROL, NO_STORE);
                headers.set(HttpHeaders.PRAGMA, "no-cache");
                headers.set(HttpHeaders.EXPIRES, "0");
                return Mono.empty();
            });
            return chain.filter(exchange);
        };
    }

    private boolean isDynamicApi(String path, List<String> prefixes) {
        return prefixes.stream().anyMatch(path::startsWith);
    }

    private boolean isWebSocketUpgrade(ServerHttpRequest request) {
        HttpHeaders headers = request.getHeaders();
        if ("websocket".equalsIgnoreCase(headers.getUpgrade())) {
            return true;
        }
        return headers.getConnection().stream()
                .anyMatch(value -> "upgrade".equalsIgnoreCase(value));
    }
}

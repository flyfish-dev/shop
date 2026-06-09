package group.flyfish.dev.portal.config;

import group.flyfish.dev.common.http.ApiNoStorePathProvider;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 动态接口缓存控制。
 *
 * <p>登录态相关接口的响应会随着 Authorization、会话和第三方 OAuth 回调即时变化。浏览器一旦缓存了
 * 未登录时的“游客”响应，OAuth 回调写入新 token 后仍可能命中旧缓存，前端就会误判为未登录并清掉 token。
 * 因此动态 API 都统一返回 no-store，明确告诉浏览器和中间代理不要复用这些响应。</p>
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
}

package group.flyfish.dev.user.config;

import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.util.AntPathMatcher;

import java.util.List;
import java.util.Map;

@Data
public class JwtProperties {

    // 匹配器
    private final AntPathMatcher matcher = new AntPathMatcher();

    // 是否启用
    private boolean enable = false;
    // 记住我
    private boolean remember = false;
    // 头部
    private String header = "Authorization";
    // 密钥，用于加解密
    private String secret = "change-me-flyfish-dev-jwt-secret";
    // token is valid 24 hours
    private long tokenValidityInSeconds = 24 * 60 * 60 * 1000;
    // valid 30 hours
    private long tokenValidityInSecondsForRememberMe = 30 * 60 * 60 * 1000;
    // route
    private Map<String, Object> route;
    // 允许的路径
    private List<String> authorizedUris;
    // 安全路径，要求加密
    private List<String> safeUris;

    public boolean permit(String uri) {
        return CollectionUtils.isEmpty(authorizedUris) ||
                authorizedUris.stream().noneMatch(item -> matcher.match(item, uri));
    }

    public boolean isSafe(String uri) {
        return CollectionUtils.isNotEmpty(safeUris) && safeUris.stream().anyMatch(item -> matcher.match(item, uri));
    }
}

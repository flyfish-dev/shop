package group.flyfish.dev.shop.license;

import group.flyfish.dev.common.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Office Preview 运行授权域名策略。
 *
 * <p>商品配置、下单表单和许可证签发必须复用本类，避免各层对商业版域名数量、
 * SaaS 泛域名格式产生不同理解。商业版支持两种互斥模式：</p>
 * <ul>
 *     <li>具体域名模式：最多五个以 {@code http://} 或 {@code https://} 开头的 origin，可使用域名或 IP；</li>
 *     <li>SaaS 模式：只能填写一个 {@code https://*.example.com} 形式的泛域名。</li>
 * </ul>
 */
public final class LicenseOriginPolicy {

    public static final String MODE_SPECIFIC = "specific";
    public static final String MODE_SAAS = "saas";
    public static final int COMMERCIAL_MAX_SPECIFIC_ORIGINS = 5;

    private static final Pattern SAAS_ORIGIN = Pattern.compile(
            "^https://\\*\\.([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+)(?::([0-9]{1,5}))?$",
            Pattern.CASE_INSENSITIVE);

    private LicenseOriginPolicy() {
    }

    /**
     * 规范化并校验运行授权来源。
     *
     * @param edition       授权版本
     * @param requestedMode 显式选择的部署模式；为空时根据是否包含泛域名推断
     * @param values        origin 列表
     * @return 标准部署模式、标准 origin 列表及最大部署数
     */
    public static Result normalize(String edition, String requestedMode, Collection<String> values) {
        String normalizedEdition = StringUtils.lowerCase(StringUtils.trimToEmpty(edition), Locale.ROOT);
        List<String> rawOrigins = compact(values);
        if ("commercial".equals(normalizedEdition)) {
            return normalizeCommercial(requestedMode, rawOrigins);
        }
        if ("personal".equals(normalizedEdition) || "demo".equals(normalizedEdition)) {
            if (rawOrigins.size() != 1 || containsWildcard(rawOrigins)) {
                throw new BusinessException("LICENSE_ORIGIN_POLICY_INVALID", "个人版只能填写一个具体部署域名");
            }
            return new Result(MODE_SPECIFIC, List.of(normalizeSpecificOrigin(rawOrigins.get(0))), 1);
        }
        return normalizeEnterprise(rawOrigins);
    }

    /**
     * 只负责标准化单个表单 origin，最终数量和模式仍需由 {@link #normalize} 统一校验。
     */
    public static String normalizeFormOrigin(String value, String label) {
        String text = StringUtils.lowerCase(StringUtils.trimToEmpty(value), Locale.ROOT);
        if (text.contains("*")) {
            try {
                return normalizeSaasOrigin(text);
            } catch (BusinessException e) {
                throw new BusinessException("ORDER_FORM_URL_INVALID",
                        StringUtils.defaultIfBlank(label, "授权域名") + "必须为 https://*.example.com 形式");
            }
        }
        try {
            return normalizeSpecificOrigin(text);
        } catch (BusinessException e) {
            throw new BusinessException("ORDER_FORM_URL_INVALID",
                    StringUtils.defaultIfBlank(label, "授权域名") + "必须是 http 或 https 地址");
        }
    }

    private static Result normalizeCommercial(String requestedMode, List<String> rawOrigins) {
        String mode = normalizeMode(requestedMode, rawOrigins);
        if (MODE_SAAS.equals(mode)) {
            if (rawOrigins.size() != 1) {
                throw new BusinessException("LICENSE_SAAS_ORIGIN_COUNT_INVALID", "SaaS 模式只能填写一个泛域名");
            }
            return new Result(MODE_SAAS, List.of(normalizeSaasOrigin(rawOrigins.get(0))), 1);
        }
        if (rawOrigins.isEmpty()) {
            throw new BusinessException("LICENSE_ORIGIN_REQUIRED", "请填写至少一个具体部署域名或 IP");
        }
        if (rawOrigins.size() > COMMERCIAL_MAX_SPECIFIC_ORIGINS) {
            throw new BusinessException("LICENSE_ORIGIN_LIMIT_EXCEEDED",
                    "商业版具体域名模式最多填写 " + COMMERCIAL_MAX_SPECIFIC_ORIGINS + " 个域名或 IP");
        }
        if (containsWildcard(rawOrigins)) {
            throw new BusinessException("LICENSE_ORIGIN_MODE_MISMATCH", "具体域名模式不能填写泛域名");
        }
        List<String> normalized = rawOrigins.stream().map(LicenseOriginPolicy::normalizeSpecificOrigin).toList();
        return new Result(MODE_SPECIFIC, normalized, normalized.size());
    }

    private static Result normalizeEnterprise(List<String> rawOrigins) {
        if (rawOrigins.isEmpty()) {
            throw new BusinessException("LICENSE_ORIGIN_REQUIRED", "授权发放必须配置至少一个部署域名");
        }
        List<String> normalized = new ArrayList<>();
        for (String origin : rawOrigins) {
            if ("*".equals(origin)) {
                normalized.add(origin);
            } else if (origin.contains("*")) {
                normalized.add(normalizeSaasOrigin(origin));
            } else {
                normalized.add(normalizeSpecificOrigin(origin));
            }
        }
        String mode = containsWildcard(normalized) ? MODE_SAAS : MODE_SPECIFIC;
        return new Result(mode, normalized, Math.max(1, normalized.size()));
    }

    private static String normalizeMode(String value, List<String> origins) {
        String normalized = StringUtils.lowerCase(StringUtils.trimToEmpty(value), Locale.ROOT);
        if (MODE_SPECIFIC.equals(normalized) || MODE_SAAS.equals(normalized)) {
            return normalized;
        }
        return containsWildcard(origins) ? MODE_SAAS : MODE_SPECIFIC;
    }

    private static String normalizeSpecificOrigin(String value) {
        try {
            URI uri = URI.create(StringUtils.trimToEmpty(value));
            String scheme = StringUtils.lowerCase(uri.getScheme(), Locale.ROOT);
            if (!Set.of("http", "https").contains(scheme)
                    || StringUtils.isBlank(uri.getHost())
                    || uri.getUserInfo() != null) {
                throw new IllegalArgumentException();
            }
            String host = StringUtils.lowerCase(uri.getHost(), Locale.ROOT);
            if (host.contains(":") && !(host.startsWith("[") && host.endsWith("]"))) {
                host = "[" + host + "]";
            }
            StringBuilder normalized = new StringBuilder(scheme)
                    .append("://")
                    .append(host);
            if (uri.getPort() >= 0) {
                normalized.append(':').append(uri.getPort());
            }
            return normalized.toString();
        } catch (Exception e) {
            throw new BusinessException("LICENSE_ORIGIN_INVALID", "授权域名或 IP 必须是完整的 http/https origin");
        }
    }

    private static String normalizeSaasOrigin(String value) {
        String normalized = StringUtils.lowerCase(StringUtils.trimToEmpty(value), Locale.ROOT);
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        Matcher matcher = SAAS_ORIGIN.matcher(normalized);
        if (!matcher.matches()) {
            throw new BusinessException("LICENSE_SAAS_ORIGIN_INVALID",
                    "SaaS 泛域名必须为 https://*.example.com 形式");
        }
        if (matcher.group(2) != null) {
            int port = Integer.parseInt(matcher.group(2));
            if (port <= 0 || port > 65535) {
                throw new BusinessException("LICENSE_SAAS_ORIGIN_INVALID", "SaaS 泛域名端口不正确");
            }
        }
        return normalized;
    }

    private static List<String> compact(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String value : values) {
            String origin = StringUtils.lowerCase(StringUtils.trimToNull(value), Locale.ROOT);
            if (origin != null) {
                result.add(origin);
            }
        }
        return new ArrayList<>(result);
    }

    private static boolean containsWildcard(Collection<String> values) {
        return values != null && values.stream().anyMatch(value -> StringUtils.contains(value, '*'));
    }

    public record Result(String deploymentMode, List<String> allowedOrigins, int maxDeployments) {
    }
}

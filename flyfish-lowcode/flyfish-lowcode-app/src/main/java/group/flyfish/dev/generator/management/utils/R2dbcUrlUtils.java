package group.flyfish.dev.generator.management.utils;

import group.flyfish.dev.bean.DbSource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * R2DBC 连接串与 SQL 标识符工具。
 */
public final class R2dbcUrlUtils {

    private static final String TYPE_MYSQL = "mysql";

    private static final String TYPE_H2 = "h2";

    private static final Set<String> LEGACY_MYSQL_OPTIONS = Set.of(
            "autoReconnect",
            "useUnicode",
            "characterEncoding",
            "zeroDateTimeBehavior",
            "allowPublicKeyRetrieval"
    );

    private R2dbcUrlUtils() {
    }

    public static String connectionUrl(DbSource source) {
        if (source == null) {
            return null;
        }
        if (hasMysqlParts(source)) {
            return buildMysqlUrl(source);
        }
        return normalize(source.getUrl());
    }

    public static DbSource materialize(DbSource source) {
        if (source == null) {
            return null;
        }
        fillPartsFromUrl(source);
        if (StringUtils.isBlank(source.getType())) {
            source.setType(databaseType(source.getUrl()));
        }
        source.setParams(normalizeParams(source.getParams()));
        String url = connectionUrl(source);
        if (StringUtils.isNotBlank(url)) {
            source.setUrl(url);
        }
        return source;
    }

    public static boolean hasConnectionInfo(DbSource source) {
        return StringUtils.isNotBlank(connectionUrl(source));
    }

    public static String normalize(String url) {
        if (StringUtils.isBlank(url) || url.startsWith("r2dbc:")) {
            return url;
        }
        if (url.startsWith("jdbc:mysql:")) {
            return normalizeMysql(url);
        }
        if (url.startsWith("jdbc:h2:")) {
            return "r2dbc:h2:" + url.substring("jdbc:h2:".length());
        }
        return url;
    }

    public static String databaseType(DbSource source) {
        return databaseType(connectionUrl(source));
    }

    public static String databaseType(String url) {
        String normalized = StringUtils.lowerCase(normalize(url), Locale.ROOT);
        if (StringUtils.isBlank(normalized)) {
            return TYPE_MYSQL;
        }
        if (normalized.startsWith("r2dbc:h2:")) {
            return TYPE_H2;
        }
        if (normalized.startsWith("r2dbc:mysql:")) {
            return TYPE_MYSQL;
        }
        return TYPE_MYSQL;
    }

    public static String quote(String identifier) {
        if (StringUtils.isBlank(identifier)) {
            throw new IllegalArgumentException("SQL identifier must not be blank");
        }
        return StringUtils.split(identifier, '.').length > 1
                ? java.util.Arrays.stream(StringUtils.split(identifier, '.')).map(R2dbcUrlUtils::quotePart)
                .collect(Collectors.joining("."))
                : quotePart(identifier);
    }

    private static String quotePart(String identifier) {
        String cleaned = StringUtils.trim(identifier);
        if ("*".equals(cleaned)) {
            return cleaned;
        }
        return "`" + cleaned.replace("`", "``") + "`";
    }

    private static boolean hasMysqlParts(DbSource source) {
        return isMysql(source.getType())
                && StringUtils.isNotBlank(source.getHost())
                && StringUtils.isNotBlank(source.getDatabaseName());
    }

    private static boolean isMysql(String type) {
        return StringUtils.isBlank(type) || TYPE_MYSQL.equalsIgnoreCase(type);
    }

    private static String buildMysqlUrl(DbSource source) {
        StringBuilder builder = new StringBuilder("r2dbc:mysql://")
                .append(StringUtils.trim(source.getHost()))
                .append(portSegment(source.getPort()))
                .append("/")
                .append(StringUtils.trim(source.getDatabaseName()));
        String params = normalizeParams(source.getParams());
        if (StringUtils.isNotBlank(params)) {
            builder.append("?").append(params);
        }
        return builder.toString();
    }

    private static String portSegment(Integer port) {
        return port == null || port <= 0 ? "" : ":" + port;
    }

    private static String normalizeParams(String params) {
        String value = StringUtils.trim(params);
        while (StringUtils.startsWithAny(value, "?", "&")) {
            value = value.substring(1);
        }
        if (StringUtils.isBlank(value)) {
            return value;
        }
        Map<String, String> options = new LinkedHashMap<>();
        for (String part : value.split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 0 || StringUtils.isBlank(pair[0])) {
                continue;
            }
            String key = pair[0];
            String optionValue = pair.length > 1 ? pair[1] : null;
            if (isDisabledSslOption(key, optionValue)) {
                continue;
            }
            options.put(key, optionValue);
        }
        return options.entrySet().stream()
                .map(entry -> entry.getKey() + (entry.getValue() == null ? "" : "=" + entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private static boolean isDisabledSslOption(String key, String value) {
        return ("sslMode".equalsIgnoreCase(key) && "DISABLED".equalsIgnoreCase(value))
                || ("ssl".equalsIgnoreCase(key) && "false".equalsIgnoreCase(value));
    }

    private static void fillPartsFromUrl(DbSource source) {
        if (!needsUrlParts(source) || StringUtils.isBlank(source.getUrl())) {
            return;
        }
        String normalized = normalize(source.getUrl());
        if (StringUtils.isBlank(source.getType())) {
            source.setType(databaseType(normalized));
        }
        if (StringUtils.startsWithIgnoreCase(normalized, "r2dbc:mysql://")) {
            fillMysqlParts(source, normalized);
        }
    }

    private static boolean needsUrlParts(DbSource source) {
        return StringUtils.isBlank(source.getHost())
                || StringUtils.isBlank(source.getDatabaseName())
                || source.getPort() == null
                || StringUtils.isBlank(source.getParams());
    }

    private static void fillMysqlParts(DbSource source, String normalized) {
        URI uri = URI.create("mysql://" + normalized.substring("r2dbc:mysql://".length()).replace(" ", "%20"));
        if (StringUtils.isBlank(source.getHost())) {
            source.setHost(uri.getHost());
        }
        if (source.getPort() != null && source.getPort() > 0) {
            // no-op; keep explicitly supplied value
        } else if (uri.getPort() > 0) {
            source.setPort(uri.getPort());
        }
        String databaseName = StringUtils.removeStart(uri.getRawPath(), "/");
        if (StringUtils.isBlank(source.getDatabaseName()) && StringUtils.isNotBlank(databaseName)) {
            source.setDatabaseName(UriUtils.decode(databaseName, StandardCharsets.UTF_8));
        }
        if (StringUtils.isBlank(source.getParams()) && StringUtils.isNotBlank(uri.getRawQuery())) {
            source.setParams(uri.getRawQuery());
        }
    }

    private static String normalizeMysql(String legacyUrl) {
        URI uri = URI.create(("mysql:" + legacyUrl.substring("jdbc:mysql:".length())).replace(" ", "%20"));
        String query = uri.getRawQuery();
        String baseUrl = "r2dbc:mysql://" + uri.getRawAuthority() + StringUtils.defaultString(uri.getRawPath());
        if (StringUtils.isBlank(query)) {
            return baseUrl;
        }
        Map<String, String> options = new LinkedHashMap<>();
        for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 0 || StringUtils.isBlank(pair[0])) {
                continue;
            }
            String key = pair[0];
            String value = pair.length > 1 ? pair[1] : "";
            if (LEGACY_MYSQL_OPTIONS.contains(key)) {
                continue;
            }
            if ("serverTimezone".equals(key)) {
                options.put("serverZoneId", value);
            } else if ("useSSL".equals(key)) {
                if (Boolean.parseBoolean(value)) {
                    options.put("sslMode", "REQUIRED");
                }
            } else {
                options.put(key, value);
            }
        }
        if (options.isEmpty()) {
            return baseUrl;
        }
        return baseUrl + "?" + options.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
    }
}

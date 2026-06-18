package group.flyfish.dev.git.domain;

import group.flyfish.dev.common.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;

import java.util.Locale;

/**
 * 代码托管平台标准值。
 *
 * <p>数据库和前端统一保存小写 code，避免枚举参数在不同 R2DBC 驱动、
 * native 镜像中的编码差异。业务代码需要展示名称或校验权限时通过本类集中处理。</p>
 */
public enum GitProvider {

    GITEA("gitea", "Gitea"),
    GITHUB("github", "GitHub"),
    GITEE("gitee", "码云");

    private final String code;

    private final String title;

    GitProvider(String code, String title) {
        this.code = code;
        this.title = title;
    }

    public String code() {
        return code;
    }

    public String title() {
        return title;
    }

    public static GitProvider of(String value) {
        String normalized = normalizeCode(value);
        for (GitProvider provider : values()) {
            if (provider.code.equals(normalized)) {
                return provider;
            }
        }
        throw new BusinessException("GIT_PROVIDER_UNSUPPORTED", "暂不支持该 Git 平台");
    }

    public static String normalizeCode(String value) {
        String normalized = StringUtils.defaultIfBlank(value, GITEA.code).trim().toLowerCase(Locale.ROOT);
        if (normalized.contains(GITHUB.code)) {
            return GITHUB.code;
        }
        if (normalized.contains(GITEE.code)) {
            return GITEE.code;
        }
        return GITEA.code;
    }

    public static String titleOf(String value) {
        return of(value).title;
    }

    public static String normalizePermission(String provider, String value) {
        String normalizedProvider = normalizeCode(provider);
        String normalized = StringUtils.defaultIfBlank(value, defaultPermission(normalizedProvider))
                .trim()
                .toLowerCase(Locale.ROOT);
        if (GITHUB.code.equals(normalizedProvider)) {
            return switch (normalized) {
                case "read" -> "pull";
                case "write" -> "push";
                case "triage", "pull", "push", "maintain", "admin" -> normalized;
                default -> "pull";
            };
        }
        if (GITEE.code.equals(normalizedProvider)) {
            return switch (normalized) {
                case "read" -> "pull";
                case "write" -> "push";
                case "pull", "push", "admin" -> normalized;
                default -> "pull";
            };
        }
        return switch (normalized) {
            case "pull" -> "read";
            case "push" -> "write";
            case "read", "write", "admin" -> normalized;
            default -> "read";
        };
    }

    public static String defaultPermission(String provider) {
        String normalizedProvider = normalizeCode(provider);
        return GITHUB.code.equals(normalizedProvider) || GITEE.code.equals(normalizedProvider) ? "pull" : "read";
    }
}

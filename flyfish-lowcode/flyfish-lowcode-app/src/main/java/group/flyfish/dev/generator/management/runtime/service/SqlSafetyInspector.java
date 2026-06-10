package group.flyfish.dev.generator.management.runtime.service;

import group.flyfish.dev.common.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * SQL 安全检查器。
 * 该项目二期先做轻量在线开发能力，不引入独立沙箱；因此所有入口统一经过这里限制多语句和危险默认行为。
 */
@Component
public class SqlSafetyInspector {

    private static final Set<String> READ_ONLY_PREFIXES = Set.of("select", "with", "show", "desc", "describe", "explain");

    public String normalizeSingleStatement(String sql) {
        String normalized = StringUtils.trimToNull(sql);
        if (normalized == null) {
            throw new BusinessException("SQL_REQUIRED", "请输入SQL");
        }
        while (normalized.endsWith(";")) {
            normalized = StringUtils.trimToEmpty(StringUtils.removeEnd(normalized, ";"));
        }
        if (normalized.contains(";")) {
            throw new BusinessException("SQL_MULTI_STATEMENT_DENIED", "一次只允许执行一条SQL");
        }
        return normalized;
    }

    public void requireReadOnly(String sql) {
        if (!isReadOnly(sql)) {
            throw new BusinessException("SQL_READ_ONLY_REQUIRED", "当前入口仅允许查询SQL");
        }
    }

    public boolean isReadOnly(String sql) {
        String lowerCaseSql = normalizeSingleStatement(sql).stripLeading().toLowerCase(Locale.ROOT);
        return READ_ONLY_PREFIXES.stream().anyMatch(lowerCaseSql::startsWith);
    }
}

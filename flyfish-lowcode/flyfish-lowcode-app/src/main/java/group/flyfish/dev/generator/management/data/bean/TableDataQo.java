package group.flyfish.dev.generator.management.data.bean;
import lombok.Data;
import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 维表查询类封装
 *
 * @author zekun.wei
 * @data 2021/02/23
 */
@Data
public class TableDataQo {

    // 数据源
    private String datasource;

    // 页码
    private int page = 1;

    // 页大小
    private int size = 10;

    // 过滤条件
    private Map<String, List<Object>> filters;

    // 排序条件
    private Map<String, String> sorts;

    // 查询条件
    private List<QueryCriteria> search;

    // 查询列
    private List<String> columns;

    // 表名
    private String tableName;

    /**
     * 为sql格式化值
     *
     * @param value 值
     * @return 结果
     */
    private static String value(Object value) {
        if (value instanceof CharSequence) {
            return String.format("'%s'", value);
        }
        if (value instanceof Number) {
            return String.valueOf(value);
        }
        if (value instanceof Date) {
            return DateFormatUtils.format((Date) value, "yyyy-MM-dd HH:mm:ss");
        }
        return null != value ? value.toString() : "";
    }

    public boolean isHasQuery() {
        return CollectionUtils.isNotEmpty(search);
    }

    public boolean isHasFilter() {
        return MapUtils.isNotEmpty(filters) && filters.keySet().stream().anyMatch(key -> CollectionUtils.isNotEmpty(filters.get(key)));
    }

    public boolean isHasOrder() {
        return MapUtils.isNotEmpty(sorts);
    }

    public String getOrder() {
        if (isHasOrder()) {
            return sorts.keySet().stream().map(key -> String.format("`%s` %s", key, sorts.get(key).replace("end", "")))
                    .collect(Collectors.joining(","));
        }
        return null;
    }

    public String getQuery() {
        if (isHasQuery()) {
            return search.stream().map(QueryCriteria::build).filter(StringUtils::isNotBlank).collect(Collectors.joining(" "));
        }
        return null;
    }

    public String getFilter() {
        if (isHasFilter()) {
            return filters.keySet().stream().filter(key -> CollectionUtils.isNotEmpty(filters.get(key))).map(key -> {
                List<Object> values = filters.get(key);
                String mapped = values.stream().filter(ObjectUtils::isNotEmpty).map(TableDataQo::value)
                        .collect(Collectors.joining(","));
                return String.format("`%s` IN (%s)", key, mapped);
            }).collect(Collectors.joining(" and "));
        }
        return null;
    }

    public int offset() {
        return Math.max(page - 1, 0) * size;
    }

    /**
     * 查询拼接运算符
     */
    @Getter
    public enum QueryCriteriaOperation {

        EQ("=", "#field = #value"),
        NE("!=", "#field != #value"),
        LT("<", "#field < #value"),
        LTE("<=", "#field <= #value"),
        GT(">", "#field > #value"),
        GTE(">=", "#field >= #value"),
        LIKE("包含", "#field LIKE CONCAT('%', #value, '%')"),
        NOT_LIKE("不包含", "#field NOT LIKE CONCAT('%', #value, '%')"),
        LIKE_LEFT("开始以", "#field LIKE CONCAT(#value, '%')"),
        NOT_LIKE_LEFT("开始不是以", "#field NOT LIKE CONCAT(#value, '%')"),
        LIKE_RIGHT("结束以", "#field LIKE CONCAT('%', #value)"),
        NOT_LIKE_RIGHT("结束不是以", "#field NOT LIKE CONCAT('%', #value)"),
        IS_NULL("是null", "#field IS NULL"),
        NOT_NULL("不是null", "#field IS NOT NULL"),
        IS_EMPTY("是空的", "IFNULL(TRIM(#field), '') = ''"),
        NOT_EMPTY("不是空的", "IFNULL(TRIM(#field), '') != ''"),
        BETWEEN("介于", "#field BETWEEN #value AND #otherValue"),
        NOT_BETWEEN("不介于", "#field NOT BETWEEN #value AND #otherValue"),
        IN("在列表", "#field IN (#value)"),
        NOT_IN("不在列表", "#field NOT IN (#value)"),
        CUSTOM("自定义", "#value");

        private final String name;

        private final String expression;

        private final Function<QueryCriteria, String> template;

        QueryCriteriaOperation(String name, String expression) {
            this.name = name;
            this.expression = expression;
            // 单独处理自定义sql
            this.template = criteria -> expression.replace("#field", String.format("`%s`", criteria.field))
                    .replace("#value", name().equals("CUSTOM") ? (String) criteria.value : criteria.parse(criteria.value))
                    .replace("#otherValue", criteria.parse(criteria.otherValue));
        }

    }

    /**
     * 查询连接方式
     */
    public enum QueryCriteriaLink {

        AND, OR
    }

    /**
     * 查询构建元
     */
    @Data
    public static class QueryCriteria {

        // 字段
        private String field;

        // 操作符
        private QueryCriteriaOperation opt;

        // 值1
        private Object value;

        // 值2，用于between
        private Object otherValue;

        // 用于支持括号
        private List<QueryCriteria> children;

        // 连接符
        private QueryCriteriaLink link;

        // 冗余类型
        private String type;

        /**
         * 编译为sql语句
         */
        public String build() {
            StringBuilder sb = new StringBuilder();
            build(sb);
            return sb.toString();
        }

        /**
         * 解析修正值，主要为了处理列表值
         *
         * @param value 值
         * @return 结果
         */
        private String parse(Object value) {
            if ((QueryCriteriaOperation.IN == opt || QueryCriteriaOperation.NOT_IN == opt)) {
                if (value instanceof CharSequence) {
                    if ("number".equals(type)) {
                        return (String) value;
                    }
                    return Arrays.stream(StringUtils.split((String) value, ","))
                            .filter(StringUtils::isNotBlank).map(v -> String.format("'%s'", v))
                            .collect(Collectors.joining(", "));
                }
            }
            return value(value);
        }

        private void build(StringBuilder sb) {
            if (CollectionUtils.isEmpty(children) && StringUtils.isBlank(field) && null == opt) {
                return;
            }
            // 有children，代表要嵌套
            if (CollectionUtils.isNotEmpty(children)) {
                sb.append(" ( ");
                children.forEach(criteria -> criteria.build(sb));
                sb.append(" ) ");
            } else {
                sb.append(opt.template.apply(this));
            }
            if (null != link) {
                sb.append(" ").append(link.name()).append(" ");
            }
        }

    }
}

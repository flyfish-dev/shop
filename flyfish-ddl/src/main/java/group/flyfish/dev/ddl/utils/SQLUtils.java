package group.flyfish.dev.ddl.utils;

import group.flyfish.dev.bean.DbTable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * SQL 工具类。
 *
 * @author wangyu
 */
@Slf4j
public class SQLUtils {


    /**
     * 通过列元数据转换为数据库列定义类型，后续需要支持更多数据源，先兼容mysql
     *
     * @param column 列元数据
     * @return 转换后的数据库列定义类型
     */
    public static String dbType(DbTable.DbColumn column) {
        StringBuilder sb = new StringBuilder(column.getType());
        if (null != column.getLength()) {
            sb.append("(").append(column.getLength());
            if (null != column.getPrecision() && 0 != column.getPrecision()) {
                sb.append(",").append(column.getPrecision());
            }
            sb.append(")");
        }
        return sb.toString();
    }

    /**
     * 连接所有字符串
     *
     * @param elements 元素们
     * @return 结果
     */
    public static String join(String... elements) {
        return Stream.of(elements)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
    }

    /**
     * 安全的join
     *
     * @param delimiter 间隔符
     * @param elements  元素们
     * @return 结果
     */
    public static String join(String delimiter, List<String> elements) {
        return elements.stream().filter(StringUtils::isNotBlank).collect(Collectors.joining(delimiter));
    }
}

package group.flyfish.dev.ddl.builder;

import group.flyfish.dev.ddl.utils.SQLUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * sql构建器
 *
 * @author wangyu
 * <p>
 * 基于语义化的字符串构建器，更有容错性和扩展性
 * 以行作为基准
 */
public class SqlBuilder {

    private final List<String> lines = new ArrayList<>();

    public static SqlBuilder builder() {
        return new SqlBuilder();
    }

    /**
     * 进入建表模式
     *
     * @param name    表名
     * @param comment 备注
     * @return 结果
     */
    public TableDdl createTable(String name, String comment) {
        TableDdl ddl = new TableDdl();
        ddl.name = name;
        ddl.comment = comment;
        return ddl;
    }

    /**
     * 构建
     *
     * @return 结果
     */
    public String build() {
        return safeJoin("\n", lines);
    }

    private String safeJoin(String delimiter, List<String> list) {
        return list.stream().filter(StringUtils::isNotBlank).collect(Collectors.joining(delimiter));
    }

    /**
     * 表ddl内部模型
     */
    public class TableDdl {

        private Function<TableDdl, String> prefix;

        private String name;

        private String comment;

        private final List<String> contents = new ArrayList<>();

        public TableDdl prefix(Function<TableDdl, String> render) {
            this.prefix = render;
            return this;
        }

        public TableDdl columns(List<String> columns) {
            contents.addAll(columns);
            return this;
        }

        public TableDdl key(String key) {
            contents.add(key);
            return this;
        }

        public TableDdl keys(List<String> keys) {
            contents.addAll(keys);
            return this;
        }

        public SqlBuilder end() {
            List<String> lines = new ArrayList<>();
            if (null != prefix) {
                lines.add(prefix.apply(this));
            }
            lines.add(SQLUtils.join("CREATE TABLE IF NOT EXISTS", name, "("));
            lines.add(safeJoin(",\n", contents));
            lines.add(SQLUtils.join(")",
                    StringUtils.isNotBlank(this.comment) ? ("COMMENT=" + this.comment) : null,
                    ";"));
            SqlBuilder.this.lines.add(safeJoin("\n", lines));
            return SqlBuilder.this;
        }

    }
}

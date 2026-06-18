package group.flyfish.dev.ddl.mapping;

import group.flyfish.dev.bean.DbTable;
import group.flyfish.dev.ddl.builder.SqlBuilder;
import group.flyfish.dev.ddl.compare.bean.DbDifference;
import group.flyfish.dev.ddl.utils.SQLUtils;
import group.flyfish.dev.enums.DbType;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

import java.text.MessageFormat;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ddl生成器
 *
 * @author wangyu
 * 用最简洁的代码实现的生成器，目前兼容mysql
 */
public class DdlGenerator {

    // 通用定义candidates，快速确定列定义
    private static final List<Function<DbTable.DbColumn, String>> CANDIDATES = Arrays.asList(
            SQLUtils::dbType,
            col -> DbType.isNumeric(col.getType()) && BooleanUtils.isTrue(col.getUnsigned()) ? "UNSIGNED" : null,
            col -> DbType.isString(col.getType()) && StringUtils.isNotBlank(col.getCharacterSet()) ?
                    ("CHARACTER SET " + col.getCharacterSet()) : null,
            col -> DbType.isString(col.getType()) && StringUtils.isNotBlank(col.getCollation()) ?
                    ("COLLATE " + col.getCollation()) : null,
            col -> BooleanUtils.isTrue(col.getNullable()) ? "NULL" : "NOT NULL",
            // 特别注意，使用自增后，不支持使用默认值
            col -> StringUtils.isNotBlank(col.getDefaultValue()) && !BooleanUtils.isTrue(col.getAutoIncrement()) ?
                    ("DEFAULT " + wrapDefault(col)) : null,
            col -> DbType.isNumeric(col.getType()) && BooleanUtils.isTrue(col.getAutoIncrement()) ? "AUTO_INCREMENT" : null,
            col -> StringUtils.isNotBlank(col.getComment()) ? ("COMMENT " + wrapString(col.getComment())) : null,
            col -> col.isCreating() ? null : (StringUtils.isNotBlank(col.getAfter()) ?
                    ("AFTER " + wrapped(col.getAfter())) : "FIRST")
    );

    /**
     * 获取建表语句
     *
     * @return 结果
     */
    public static String getCreateSql(DbTable table) {
        return SqlBuilder.builder()
                // 前缀包裹
                .createTable(table.wrappedName(), StringUtils.isNotBlank(table.getComment()) ? wrapString(table.getComment()) : null)
                // 列定义添加
                .columns(table.columns()
                        .map(column -> SQLUtils.join(column.wrappedName(), getDefinition(column.markCreating())))
                        .collect(Collectors.toList())
                )
                // 主键添加
                .key(getDefinition(table.primaryKey()))
                // 添加索引
                .keys(table.getIndexes().stream().map(index -> SQLUtils.join(getDefinition(index))).collect(Collectors.toList()))
                // 完成构建
                .end()
                // 得到sql
                .build();
    }

    /**
     * 获取表结构更新语句
     *
     * @param difference 不同
     * @return 结果
     */
    public static String getUpdateSql(DbDifference difference) {
        List<String> lines = new ArrayList<>();
        DbDifference.Table table = difference.getTable();
        // 修改了表名，先改名字
        Set<DbDifference.TableChangeType> types = null == table.getTypes() ? Collections.emptySet() : table.getTypes();
        if (types.contains(DbDifference.TableChangeType.RENAME)) {
            // 先改名
            String statement = MessageFormat.format("RENAME TABLE {0} TO {1};", table.getPrevious().wrappedName(),
                    table.getCurrent().wrappedName());
            lines.add(statement);
        }
        // 判断各种情况的比较
        boolean columnChanged = CollectionUtils.isNotEmpty(difference.getColumns());
        boolean commentChanged = types.contains(DbDifference.TableChangeType.COMMENT);
        boolean indexChanged = CollectionUtils.isNotEmpty(difference.getIndexes());
        boolean pkChanged = null != difference.getPrimaryKey();
        // 修改了字段，添加字段变更
        if (columnChanged || commentChanged || indexChanged || pkChanged) {
            String alter = MessageFormat.format("ALTER TABLE {0}", table.getCurrent().wrappedName());
            lines.add(alter);
            // 承载内部元素，这些元素使用逗号分割
            List<String> contents = new ArrayList<>();
            if (columnChanged) {
                difference.getColumns().forEach(column -> contents.add(column.getDdl()));
            }
            if (pkChanged) {
                contents.add(difference.getPrimaryKey().getDdl());
            }
            if (indexChanged) {
                difference.getIndexes().forEach(index -> contents.add(index.getDdl()));
            }
            if (commentChanged) {
                contents.add("COMMENT = " + wrapString(table.getCurrent().getComment()));
            }
            // 添加内容
            lines.add(SQLUtils.join(",\n", contents));
        }
        return SQLUtils.join("\n", lines) + ";";
    }

    private static String wrapString(String value) {
        return "'" + value + "'";
    }

    private static String wrapped(String value) {
        return "`" + value + "`";
    }

    /**
     * 获取列定义
     *
     * @return 结果
     */
    public static String getDefinition(DbTable.DbColumn column) {
        if (null == column) {
            return null;
        }
        // 字段定义同建表语句，分为数字型和字符型和日期型。
        // 数字型，包括类型 + 是否无符号 + 空类型 + 默认值 + 属性 + 位置
        // 字符型，包括类型 + 字符集 + 排序 + 空类型 + 默认值 + 属性 + 位置
        // 其他型，包括类型 + 空类型 + 默认值 + 位置
        return CANDIDATES.stream().map(candidate -> candidate.apply(column))
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
    }

    /**
     * 获取索引定义
     *
     * @param index 索引信息
     * @return 结果
     */
    public static String getDefinition(DbTable.DbIndex index) {
        if (null == index) {
            return null;
        }
        String comment = StringUtils.isNotBlank(index.getComment()) ? ("COMMENT " + wrapString(index.getComment()))
                : null;
        String attr = index.getType() == DbTable.DbIndexType.BTREE ? "USING BTREE" : null;
        return SQLUtils.join(index.getType().getCode(), index.target(), index.wrappedName(), "(",
                index.getFields().stream().map(DdlGenerator::wrapped).collect(Collectors.joining(",")),
                ")", attr, comment);
    }


    /**
     * 包装默认值
     *
     * @param column 列
     * @return 结果
     */
    private static String wrapDefault(DbTable.DbColumn column) {
        if (DbType.isString(column.getType())) {
            return wrapString(column.getDefaultValue());
        }
        if (DbType.isDate(column.getType())) {
            if (!column.getDefaultValue().contains("CURRENT_TIMESTAMP")) {
                return wrapString(column.getDefaultValue());
            }
        }
        return column.getDefaultValue();
    }

}

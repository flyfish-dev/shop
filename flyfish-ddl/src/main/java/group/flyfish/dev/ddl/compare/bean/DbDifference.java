package group.flyfish.dev.ddl.compare.bean;

import group.flyfish.dev.bean.DbTable;
import group.flyfish.dev.ddl.mapping.DdlGenerator;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static group.flyfish.dev.ddl.utils.SQLUtils.join;

/**
 * 数据库区别
 *
 * @author wangyu
 */
@Data
public class DbDifference {

    // 表变更
    private Table table;

    // 列变更
    private List<Column> columns;

    // 索引变更
    private List<Index> indexes;

    // 主键变更
    private Index primaryKey;

    /**
     * 是否等同于数据库
     *
     * @return 结果
     */
    public boolean isEqual() {
        return CollectionUtils.isEmpty(columns) &&
                CollectionUtils.isEmpty(indexes) &&
                (null == table || CollectionUtils.isEmpty(table.getTypes())) &&
                null == primaryKey;
    }

    /**
     * 将变更抽取为ddl
     *
     * @return 结果
     */
    public String toDdl() {
        return DdlGenerator.getUpdateSql(this);
    }

    @Override
    public String toString() {
        if (isEqual()) {
            return "没有任何变化";
        }
        StringBuilder sb = new StringBuilder("修改的详情如下：\n");
        if (null != table && CollectionUtils.isNotEmpty(table.types)) {
            sb.append(table.types.stream().map(type -> type.human(table)).collect(Collectors.joining("；\n")))
                    .append("\n");
        }
        if (CollectionUtils.isNotEmpty(columns)) {
            sb.append(columns.stream().map(column ->
                    join("；\n", column.getTypes().stream().map(type -> type.human(column))
                            .collect(Collectors.toList()))).collect(Collectors.joining("\n")))
                    .append("\n");
        }
        if (CollectionUtils.isNotEmpty(indexes)) {
            sb.append(indexes.stream().map(index -> join("；\n", index.getTypes().stream().map(type -> type.human(index))
                                    .collect(Collectors.toList()))).collect(Collectors.joining("\n")))
                    .append("\n");
        }
        return sb.toString();
    }

    public enum ColumnChangeAction {

        MODIFY, CHANGE, DROP, ADD
    }

    @RequiredArgsConstructor
    @Getter
    public enum TableChangeType {

        RENAME("重命名", t -> join("重命名表", t.getPrevious().getName(), "为", t.getCurrent().getName())),
        COMMENT("调整备注", t -> join("调整备注为", t.getCurrent().getComment()));

        private final String name;

        private final Function<Table, String> printer;

        public String human(Table table) {
            if (null != printer) {
                return printer.apply(table);
            }
            return "";
        }
    }

    @RequiredArgsConstructor
    @Getter
    public enum ColumnChangeType {

        ADD("新增", col -> join("新增列：", col.getCurrent().getName())),
        REMOVE("删除", col -> join("删除列：", col.getPrevious().getName())),
        RENAME("重命名", col -> join("重命名列", col.getPrevious().getName(), "为", col.getCurrent().getName())),
        CHANGE("任意改变", col -> join("修改列", col.getCurrent().getName(), "的定义。")),
        RETYPE("重定义类型", null),
        RESIZE("重调整大小", null),
        REPOSITION("调整位置", null),
        DEFAULT_VALUE("调整默认值", null),
        COMMENT("调整备注", null),
        NULLABLE("调整空值", null),
        ATTR("调整其他属性", null);

        private final String name;

        private final Function<Column, String> printer;

        public String human(Column column) {
            if (null != printer) {
                return printer.apply(column);
            }
            return "";
        }
    }

    @RequiredArgsConstructor
    @Getter
    public enum IndexChangeType {

        ADD("新增", col -> join("新增索引：", col.getCurrent().getName())),
        REMOVE("删除", col -> join("删除索引：", col.getPrevious().getName())),
        RENAME("重命名", col -> join("重命名索引", col.getPrevious().getName(), "为", col.getCurrent().getName())),
        CHANGE("任意改变", col -> join("修改索引", col.getCurrent().getName(), "的定义。"));

        private final String name;

        private final Function<Index, String> printer;

        public String human(Index index) {
            if (null != printer) {
                return printer.apply(index);
            }
            return "";
        }
    }

    /**
     * 表级别的变化
     * 名称改变：RENAME TABLE `dt_project`.`sys_catalogs` TO `dt_project`.`sys_catalogs1`;
     * 备注改变：
     */
    @Data
    public static class Table {

        private Set<TableChangeType> types;

        private DbTable previous;

        private DbTable current;

        public void addType(TableChangeType type) {
            if (null == types) {
                types = new HashSet<>();
            }
            types.add(type);
        }
    }

    /**
     * 变化的列，包括位置、长度、名称、
     */
    @Data
    public static class Column {

        // 列改变类型
        private Set<ColumnChangeType> types;

        // 旧列定义
        private DbTable.DbColumn previous;

        // 新列定义
        private DbTable.DbColumn current;

        /**
         * 快速定义新增的列
         *
         * @param current 新增列
         * @return 结果
         */
        public static Column add(DbTable.DbColumn current) {
            Column column = new Column();
            column.setTypes(Collections.singleton(ColumnChangeType.ADD));
            column.setCurrent(current);
            return column;
        }

        /**
         * 快速定义删除的列
         *
         * @param previous 被删除的
         * @return 结果
         */
        public static Column remove(DbTable.DbColumn previous) {
            Column column = new Column();
            column.setTypes(Collections.singleton(ColumnChangeType.REMOVE));
            column.setPrevious(previous);
            return column;
        }

        // ddl
        public String getDdl() {
            // 语义确认
            ColumnChangeAction action = determineAction();
            // 确认客体
            String target = determineTarget();
            // 确认定义
            String definition = action == ColumnChangeAction.DROP ? null : DdlGenerator.getDefinition(current);
            // 拼接后返回
            return join(action.name(), "COLUMN", target, definition);
        }

        /**
         * 确定操作类型
         *
         * @return 列操作类型
         */
        private ColumnChangeAction determineAction() {
            ColumnChangeAction action = ColumnChangeAction.MODIFY;
            if (types.contains(ColumnChangeType.RENAME)) {
                action = ColumnChangeAction.CHANGE;
            } else if (types.contains(ColumnChangeType.REMOVE)) {
                action = ColumnChangeAction.DROP;
            } else if (types.contains(ColumnChangeType.ADD)) {
                action = ColumnChangeAction.ADD;
            }
            return action;
        }

        /**
         * 确定客体
         *
         * @return 结果
         */
        private String determineTarget() {
            if (types.contains(ColumnChangeType.RENAME)) {
                return previous.wrappedName() + " " + current.wrappedName();
            }
            if (types.contains(ColumnChangeType.REMOVE)) {
                return previous.wrappedName();
            }
            return current.wrappedName();
        }

        public void addType(ColumnChangeType type) {
            if (null == types) {
                types = new HashSet<>();
            }
            types.add(type);
        }

    }

    /**
     * 变化的索引，包括命名，
     */
    @Data
    public static class Index {

        // 索引改变类型
        private Set<IndexChangeType> types;

        // 旧索引定义
        private DbTable.DbIndex previous;

        // 新索引定义
        private DbTable.DbIndex current;

        /**
         * 快速定义新增的索引
         *
         * @param current 新增列
         * @return 结果
         */
        public static Index add(DbTable.DbIndex current) {
            Index index = new Index();
            index.setTypes(Collections.singleton(IndexChangeType.ADD));
            index.setCurrent(current);
            return index;
        }

        /**
         * 快速定义删除的索引
         *
         * @param previous 被删除的
         * @return 结果
         */
        public static Index remove(DbTable.DbIndex previous) {
            Index index = new Index();
            index.setTypes(Collections.singleton(IndexChangeType.REMOVE));
            index.setPrevious(previous);
            return index;
        }

        public void addType(IndexChangeType type) {
            if (null == types) {
                types = new HashSet<>();
            }
            types.add(type);
        }

        /**
         * 获取sql语句片段
         *
         * @return 结果
         */
        public String getDdl() {
            // 仅存在重命名，直接重命名
            if (types.size() == 1 && types.contains(IndexChangeType.RENAME)) {
                return join("RENAME", "INDEX", previous.wrappedName(), "TO",
                        current.wrappedName());
            }
            // 只要存在修改，就先删除，后增加
            if (types.contains(IndexChangeType.CHANGE)) {
                return join(",\n", Arrays.asList(getDropDdl(), getAddDdl()));
            }
            // 处理新增和删除
            if (types.contains(IndexChangeType.ADD)) {
                return getAddDdl();
            }
            if (types.contains(IndexChangeType.REMOVE)) {
                return getDropDdl();
            }
            return null;
        }

        private String getAddDdl() {
            return join("ADD", DdlGenerator.getDefinition(current));
        }

        private String getDropDdl() {
            String target = Stream.of(current, previous).filter(Objects::nonNull)
                    .findFirst().map(DbTable.DbIndex::target).orElse(null);
            return join("DROP", target, previous.wrappedName());
        }
    }
}

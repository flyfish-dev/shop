package group.flyfish.dev.ddl.compare.impl;

import group.flyfish.dev.bean.Changeable;
import group.flyfish.dev.bean.DbTable;
import group.flyfish.dev.ddl.compare.DbComparator;
import group.flyfish.dev.ddl.compare.bean.DbDifference;
import group.flyfish.dev.ddl.compare.support.CollectionComparator;
import group.flyfish.dev.ddl.compare.support.ObjectComparator;
import org.apache.commons.collections4.ListUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * 简单的数据库比对逻辑
 *
 * @author wangyu
 */
public class SimpleDbComparator implements DbComparator {

    /**
     * 判断主键是否变化
     *
     * @param table 表
     * @return 结果
     */
    private static DbDifference.Index comparePrimaryKey(DbDifference.Table table) {
        DbTable.DbIndex previous = table.getPrevious().primaryKey();
        DbTable.DbIndex current = table.getCurrent().primaryKey();
        // 移除的情况
        if (null != previous && null == current) {
            return DbDifference.Index.remove(previous);
        }
        // 新增的情况
        if (null == previous && null != current) {
            return DbDifference.Index.add(current);
        }
        // 都为空，无变化
        if (null == previous && null == current) {
            return null;
        }
        // 判断变化
        if (ListUtils.isEqualList(previous.getFields(), current.getFields())) {
            return null;
        }
        DbDifference.Index index = new DbDifference.Index();
        index.addType(DbDifference.IndexChangeType.CHANGE);
        index.setPrevious(previous);
        index.setCurrent(current);
        return index;
    }

    /**
     * 比较数据库和元数据的异同，返回区别
     *
     * @param previous 之前的
     * @param current  现在的
     * @return 结果
     */
    @Override
    public DbDifference compare(DbTable previous, DbTable current) {
        // 不存在原表，不弄
        if (null == previous) {
            return null;
        }
        // 准备工作
        prepare(previous, current);
        // 构建差异信息，准备放入
        DbDifference difference = new DbDifference();
        // 先比对表
        DbDifference.Table table = new DbDifference.Table();
        table.setPrevious(previous);
        table.setCurrent(current);
        // 比较表信息，设置表变更类型
        ObjectComparator<DbTable> tableComparator = ObjectComparator.compare(previous, current);
        tableComparator.ifChange(DbTable::getName, (p, c) -> table.addType(DbDifference.TableChangeType.RENAME));
        tableComparator.ifChange(DbTable::getComment, (p, c) -> table.addType(DbDifference.TableChangeType.COMMENT));
        difference.setTable(table);
        // 比对列
        CollectionComparator<String, DbTable.DbColumn> columnComparator = CollectionComparator.newInstance(
                previous.getColumns(), current.getColumns(), Changeable::previousName, this::judgeChanged);
        List<DbDifference.Column> columns = new ArrayList<>();
        // 填充新增、删除的列
        columnComparator.added().forEach(column -> columns.add(DbDifference.Column.add(column)));
        columnComparator.deleted().forEach(column -> columns.add(DbDifference.Column.remove(column)));
        // 处理变化的列
        columnComparator.changed().forEach(tuple -> columns.add(compareColumn(tuple.getPrevious(), tuple.getCurrent())));
        difference.setColumns(columns);
        // 比对索引
        CollectionComparator<String, DbTable.DbIndex> indexComparator = CollectionComparator.newInstance(
                previous.getIndexes(), current.getIndexes(), Changeable::previousName, this::judgeChanged);
        List<DbDifference.Index> indexes = new ArrayList<>();
        // 填充新增、删除的的索引
        indexComparator.added().forEach(index -> indexes.add(DbDifference.Index.add(index)));
        indexComparator.deleted().forEach(index -> indexes.add(DbDifference.Index.remove(index)));
        // 处理变化的索引
        indexComparator.changed().forEach(tuple -> indexes.add(compareIndex(tuple.getPrevious(), tuple.getCurrent())));
        difference.setIndexes(indexes);
        // 比对主键
        difference.setPrimaryKey(comparePrimaryKey(table));
        // 全干完后，返回
        return difference;
    }

    /**
     * 准备阶段，比对前先做一些准备工作
     *
     * @param previous 前任
     * @param current  现任
     */
    private void prepare(DbTable previous, DbTable current) {
        // 设置每个column的after位置
        Stream.of(previous, current).forEach(table -> {
            List<DbTable.DbColumn> columns = table.getColumns();
            IntStream.range(1, columns.size())
                    .forEach(index -> columns.get(index).setAfter(columns.get(index - 1).getName()));
        });
    }

    /**
     * 判断是否改变了
     *
     * @param previous 前任
     * @param current  现任
     * @return 结果
     */
    private <T extends Changeable<T>> int judgeChanged(T previous, T current) {
        // 不能忘记重命名的变化
        boolean namedChanged = current.nameChanged();
        // 判断任意属性变化
        boolean changed = ObjectComparator.compare(previous, current).anyChanged();
        if (changed) {
            // 标记变更，这样后续就不用再比对一次
            current.markChanged();
        }
        return namedChanged || changed ? 1 : 0;
    }

    /**
     * 比较列
     *
     * @param previous 原列
     * @param current  当前列
     * @return 比对结果
     */
    private DbDifference.Column compareColumn(DbTable.DbColumn previous, DbTable.DbColumn current) {
        DbDifference.Column column = new DbDifference.Column();
        column.setPrevious(previous);
        column.setCurrent(current);
        if (current.nameChanged()) {
            column.addType(DbDifference.ColumnChangeType.RENAME);
        }
        // 判断是否存在属性变更
        if (current.marked()) {
            // 列的变化比较复杂，支持很多变化类型，为了加速，这里不一一判定以填充具体类型，仅标记已修改
            column.addType(DbDifference.ColumnChangeType.CHANGE);
        }
        return column;
    }

    /**
     * 比较索引
     *
     * @param previous 原索引
     * @param current  目前索引
     * @return 比对结果
     */
    private DbDifference.Index compareIndex(DbTable.DbIndex previous, DbTable.DbIndex current) {
        DbDifference.Index index = new DbDifference.Index();
        index.setPrevious(previous);
        index.setCurrent(current);
        if (current.nameChanged()) {
            index.addType(DbDifference.IndexChangeType.RENAME);
        }
        // 判断是否存在属性变更
        if (current.marked()) {
            // 索引变化相对简单，我们不处理更深入的逻辑
            index.addType(DbDifference.IndexChangeType.CHANGE);
        }
        return index;
    }
}

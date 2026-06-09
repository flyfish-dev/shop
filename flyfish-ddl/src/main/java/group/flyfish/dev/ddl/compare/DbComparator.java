package group.flyfish.dev.ddl.compare;

import group.flyfish.dev.bean.DbTable;
import group.flyfish.dev.ddl.compare.bean.DbDifference;

/**
 * 数据表比对器
 *
 * @author wangyu
 */
public interface DbComparator {

    /**
     * 比较数据库和元数据的异同，返回区别
     *
     * @param previous 之前的
     * @param current  现在的
     * @return 结果
     */
    DbDifference compare(DbTable previous, DbTable current);
}

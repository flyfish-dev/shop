package group.flyfish.dev.generator.management.ddl;

import group.flyfish.dev.bean.DbTable;
import group.flyfish.dev.bean.DbTest;
import reactor.core.publisher.Mono;

/**
 * 数据库ddl管理器
 *
 * @author wangyu
 * 实现全量比对，完成表结构随意变化，并备份
 */
public interface DbDdlManager {

    /**
     * 同步数据库表
     *
     * @param table 表
     */
    Mono<Boolean> sync(DbTable table);

    /**
     * 预同步，不入库
     *
     * @param table 表
     * @return 结果
     */
    Mono<DbTest> test(DbTable table);

    /**
     * 删除表
     *
     * @param table 表信息
     * @return 结果
     */
    Mono<Boolean> drop(DbTable table);

}

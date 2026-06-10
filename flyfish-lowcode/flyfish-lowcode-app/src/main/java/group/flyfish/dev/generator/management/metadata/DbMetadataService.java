package group.flyfish.dev.generator.management.metadata;

import group.flyfish.dev.bean.DbSource;
import group.flyfish.dev.bean.DbTable;
import io.r2dbc.spi.Connection;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 数据库元数据服务
 *
 * @author wangyu
 */
public interface DbMetadataService {

    /**
     * 获取数据源下的数据表
     *
     * @param source 数据源
     * @return 表的列表
     */
    Mono<List<DbTable>> getTables(DbSource source);

    /**
     * 获取某张表的详细信息
     *
     * @param source    数据源
     * @param tableName 表名
     * @return 表详情
     */
    Mono<DbTable> getTableDetail(DbSource source, String tableName);

    /**
     * 通过复用connection完成详情查询
     *
     * @param connection 数据库连接
     * @param tableName  表名
     * @return 结果
     */
    Mono<DbTable> getTableDetail(Connection connection, String tableName);
}

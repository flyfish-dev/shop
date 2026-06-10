package group.flyfish.dev.generator.management.ddl.impl;

import group.flyfish.dev.bean.DbSource;
import group.flyfish.dev.bean.DbTable;
import group.flyfish.dev.bean.DbTest;
import group.flyfish.dev.generator.management.ddl.DbDdlManager;
import group.flyfish.dev.ddl.compare.DbComparator;
import group.flyfish.dev.ddl.compare.bean.DbDifference;
import group.flyfish.dev.ddl.compare.impl.SimpleDbComparator;
import group.flyfish.dev.ddl.mapping.DdlGenerator;
import group.flyfish.dev.generator.management.manager.DbConnectionManager;
import group.flyfish.dev.generator.management.metadata.DbMetadataService;
import group.flyfish.dev.generator.management.utils.R2dbcResultUtils;
import group.flyfish.dev.ddl.utils.SQLUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 炒鸡简单的ddl管理器
 *
 * @author wangyu
 * 仅实现基础功能，无容错性
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SimpleDdlManager implements DbDdlManager {

    private final DbConnectionManager connectionManager;

    private final DbMetadataService metadataService;

    private final DbComparator comparator = new SimpleDbComparator();

    /**
     * 同步数据库表
     *
     * @param table 表
     */
    @Override
    public Mono<Boolean> sync(DbTable table) {
        DbSource source = new DbSource(table.getDataSource());
        return connectionManager.queryWithConnection(source, connection ->
                metadataService.getTableDetail(connection, table.previousName())
                        .flatMap(previous -> {
                            DbDifference difference = comparator.compare(previous, table);
                            if (difference == null) {
                                String ddl = DdlGenerator.getCreateSql(table);
                                log.info("ddl: \n" + ddl);
                                return R2dbcResultUtils.rowsUpdated(connection, ddl).thenReturn(true);
                            }
                            if (!difference.isEqual()) {
                                String ddl = difference.toDdl();
                                log.info("ddl: \n" + ddl);
                                return R2dbcResultUtils.rowsUpdated(connection, ddl).thenReturn(true);
                            }
                            return Mono.just(true);
                        })
                        .switchIfEmpty(Mono.defer(() -> {
                            String ddl = DdlGenerator.getCreateSql(table);
                            log.info("ddl: \n" + ddl);
                            return R2dbcResultUtils.rowsUpdated(connection, ddl).thenReturn(true);
                        })));
    }

    /**
     * 预同步，不入库
     *
     * @param table 表
     * @return 结果
     */
    @Override
    public Mono<DbTest> test(DbTable table) {
        DbTest test = new DbTest();
        DbSource source = new DbSource(table.getDataSource());
        return connectionManager.queryWithConnection(source, connection ->
                metadataService.getTableDetail(connection, table.previousName())
                        .doOnNext(previous -> {
                            DbDifference difference = comparator.compare(previous, table);
                            if (difference == null) {
                                String ddl = DdlGenerator.getCreateSql(table);
                                test.setSql(ddl);
                                test.setDesc("注意，数据库尚未存在这张表，本次为全新创建数据表！");
                            } else if (!difference.isEqual()) {
                                String ddl = difference.toDdl();
                                test.setSql(ddl);
                                test.setDesc(difference.toString());
                            }
                        })
                        .switchIfEmpty(Mono.fromRunnable(() -> {
                            String ddl = DdlGenerator.getCreateSql(table);
                            test.setSql(ddl);
                            test.setDesc("注意，数据库尚未存在这张表，本次为全新创建数据表！");
                        }))
                        .thenReturn(test));
    }

    /**
     * 删除表
     *
     * @param table 表信息
     * @return 结果
     */
    @Override
    public Mono<Boolean> drop(DbTable table) {
        DbSource source = new DbSource(table.getDataSource());
        return connectionManager.queryWithConnection(source, connection -> {
            String sql = SQLUtils.join("DROP TABLE IF EXISTS", table.wrappedName());
            return R2dbcResultUtils.rowsUpdated(connection, sql).thenReturn(true);
        });
    }
}

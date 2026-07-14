package group.flyfish.dev.generator.management.data.impl;

import group.flyfish.dev.bean.DbSource;
import group.flyfish.dev.bean.DbTable;
import group.flyfish.dev.common.exception.Assert;
import group.flyfish.dev.generator.management.data.DbTableDataService;
import group.flyfish.dev.generator.management.data.bean.TableDataChange;
import group.flyfish.dev.generator.management.data.bean.TableDataDeleteDto;
import group.flyfish.dev.generator.management.data.bean.TableDataPage;
import group.flyfish.dev.generator.management.data.bean.TableDataQo;
import group.flyfish.dev.generator.management.data.bean.TableDataUpdateDto;
import group.flyfish.dev.generator.management.data.bean.row.TableRowDeleteDto;
import group.flyfish.dev.generator.management.data.bean.row.TableRowInsertDto;
import group.flyfish.dev.generator.management.data.bean.row.TableRowUpdateDto;
import group.flyfish.dev.generator.management.data.utils.TableDataSqlBuilder;
import group.flyfish.dev.generator.management.manager.DbConnectionManager;
import group.flyfish.dev.generator.management.metadata.DbMetadataService;
import group.flyfish.dev.generator.management.utils.R2dbcResultUtils;
import io.r2dbc.spi.Connection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * R2DBC 表数据服务。
 *
 * @author wangyu
 */
@Service
@RequiredArgsConstructor
public class DbTableDataServiceImpl implements DbTableDataService {

    private final DbConnectionManager connectionManager;

    private final DbMetadataService metadataService;

    @Override
    public Mono<TableDataPage<Map<String, Object>>> getPageList(TableDataQo qo) {
        return connectionManager.queryWithConnection(new DbSource(qo.getDatasource()), connection -> {
            TableDataSqlBuilder.BoundSql count = TableDataSqlBuilder.count(qo);
            TableDataSqlBuilder.BoundSql select = TableDataSqlBuilder.selectPage(qo);
            return R2dbcResultUtils.query(connection, count.getSql(), count.getBindings())
                    .map(rows -> rows.getRows().isEmpty() ? 0L : number(rows.getRows().getFirst().get("total")))
                    .flatMap(total -> R2dbcResultUtils.query(connection, select.getSql(), select.getBindings())
                            .map(rows -> new TableDataPage<Map<String, Object>>(qo.getPage(), qo.getSize(), total,
                                    rows.getRows().stream().map(row -> (Map<String, Object>) row).toList())));
        });
    }

    @Override
    public Mono<Integer> updateData(TableDataUpdateDto dto) {
        Map<Integer, TableDataChange> changes = dto.getChanges();
        return connectionManager.queryWithConnection(new DbSource(dto.getSource()), connection ->
                inTransaction(connection, metadataService.getTableDetail(connection, dto.getTable())
                        .flatMapMany(table -> Flux.fromIterable(changes.values())
                                .concatMap(change -> executeChange(connection, dto.getTable(), table, change)))
                        .reduce(0L, Long::sum))
                        .map(Long::intValue));
    }

    @Override
    public Mono<Integer> deleteData(TableDataDeleteDto dto) {
        return connectionManager.queryWithConnection(new DbSource(dto.getSource()), connection ->
                inTransaction(connection, metadataService.getTableDetail(connection, dto.getTable())
                        .flatMapMany(table -> {
                            assertPrimaryKey(table);
                            TableRowDeleteDto delete = new TableRowDeleteDto(table);
                            return Flux.fromIterable(dto.getRows()).concatMap(row -> {
                                delete.setData(row);
                                TableDataSqlBuilder.BoundSql sql = TableDataSqlBuilder.delete(delete);
                                return R2dbcResultUtils.rowsUpdated(connection, sql.getSql(), sql.getBindings());
                            });
                        })
                        .reduce(0L, Long::sum))
                        .map(Long::intValue));
    }

    private Mono<Long> executeChange(Connection connection, String tableName, DbTable table, TableDataChange change) {
        if (change.isCreate()) {
            TableRowInsertDto insert = new TableRowInsertDto(tableName, change.getChange());
            TableDataSqlBuilder.BoundSql sql = TableDataSqlBuilder.insert(insert);
            return R2dbcResultUtils.rowsUpdated(connection, sql.getSql(), sql.getBindings());
        }
        assertPrimaryKey(table);
        TableRowUpdateDto update = new TableRowUpdateDto(change, table);
        TableDataSqlBuilder.BoundSql sql = TableDataSqlBuilder.update(update);
        return R2dbcResultUtils.rowsUpdated(connection, sql.getSql(), sql.getBindings());
    }

    private <T> Mono<T> inTransaction(Connection connection, Mono<T> work) {
        return Mono.from(connection.beginTransaction())
                .then(work)
                .flatMap(result -> Mono.from(connection.commitTransaction()).thenReturn(result))
                .onErrorResume(e -> Mono.from(connection.rollbackTransaction()).then(Mono.error(e)));
    }

    private void assertPrimaryKey(DbTable table) {
        Assert.notNull(table.primaryKey(), "当前表没有主键，无法定位要更新或删除的数据！");
    }

    private long number(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? 0L : Long.parseLong(String.valueOf(value));
    }
}

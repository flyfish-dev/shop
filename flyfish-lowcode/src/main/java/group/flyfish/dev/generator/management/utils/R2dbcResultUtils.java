package group.flyfish.dev.generator.management.utils;

import group.flyfish.dev.generator.management.data.bean.TableDataRow;
import io.r2dbc.spi.ColumnMetadata;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import io.r2dbc.spi.Statement;
import lombok.Data;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 低层 R2DBC 结果集适配。
 */
public final class R2dbcResultUtils {

    private R2dbcResultUtils() {
    }

    public static Mono<QueryRows> query(Connection connection, String sql) {
        return query(connection, sql, Collections.emptyList());
    }

    public static Mono<QueryRows> query(Connection connection, String sql, List<?> bindings) {
        Statement statement = bind(connection.createStatement(sql), bindings);
        AtomicReference<List<String>> columns = new AtomicReference<>(Collections.emptyList());
        return Flux.from(statement.execute())
                .concatMap(result -> result.map((row, metadata) -> {
                    List<String> names = columnNames(metadata);
                    columns.set(names);
                    return toRow(row, names);
                }))
                .collectList()
                .map(rows -> new QueryRows(columns.get(), rows));
    }

    public static Mono<Long> rowsUpdated(Connection connection, String sql) {
        return rowsUpdated(connection, sql, Collections.emptyList());
    }

    public static Mono<Long> rowsUpdated(Connection connection, String sql, List<?> bindings) {
        Statement statement = bind(connection.createStatement(sql), bindings);
        return Flux.from(statement.execute())
                .concatMap(result -> result.getRowsUpdated())
                .reduce(0L, Long::sum);
    }

    public static Statement bind(Statement statement, List<?> bindings) {
        for (int i = 0; i < bindings.size(); i++) {
            Object value = bindings.get(i);
            if (value == null) {
                statement.bindNull(i, Object.class);
            } else {
                statement.bind(i, value);
            }
        }
        return statement;
    }

    public static List<String> columnNames(RowMetadata metadata) {
        List<String> columns = new ArrayList<>();
        for (ColumnMetadata column : metadata.getColumnMetadatas()) {
            columns.add(column.getName());
        }
        return columns;
    }

    public static TableDataRow toRow(Row row, List<String> columns) {
        TableDataRow data = new TableDataRow();
        columns.forEach(column -> data.put(column, row.get(column)));
        return data;
    }

    @Data
    public static class QueryRows {

        private final List<String> columns;

        private final List<TableDataRow> rows;
    }
}

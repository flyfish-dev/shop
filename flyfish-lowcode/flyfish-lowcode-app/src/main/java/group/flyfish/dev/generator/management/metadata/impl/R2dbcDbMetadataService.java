package group.flyfish.dev.generator.management.metadata.impl;

import group.flyfish.dev.bean.DbSource;
import group.flyfish.dev.bean.DbTable;
import group.flyfish.dev.common.exception.ServiceException;
import group.flyfish.dev.enums.DbType;
import group.flyfish.dev.generator.management.manager.DbConnectionManager;
import group.flyfish.dev.generator.management.metadata.DbMetadataService;
import group.flyfish.dev.generator.management.utils.R2dbcResultUtils;
import group.flyfish.dev.generator.management.utils.R2dbcUrlUtils;
import io.r2dbc.spi.Connection;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 基于 R2DBC 的元数据采集服务。
 *
 * @author wangyu
 */
@Service
@RequiredArgsConstructor
public class R2dbcDbMetadataService implements DbMetadataService {

    private static final String TYPE_H2 = "h2";

    private static final String TYPE_MYSQL = "mysql";

    private final DbConnectionManager connectionManager;

    @Override
    public Mono<List<DbTable>> getTables(DbSource source) {
        return connectionManager.queryWithConnection(source, connection -> {
            String type = databaseType(connection);
            return R2dbcResultUtils.query(connection, tableSql(type))
                    .map(rows -> rows.getRows().stream().map(row -> {
                        DbTable table = new DbTable();
                        table.setName(string(row.get("TABLE_NAME")));
                        table.setComment(StringUtils.defaultIfBlank(string(row.get("TABLE_COMMENT")), string(row.get("REMARKS"))));
                        return table;
                    }).toList());
        }).onErrorMap(e -> new ServiceException("数据库连接失败！" + e.getMessage(), e));
    }

    @Override
    public Mono<DbTable> getTableDetail(DbSource source, String tableName) {
        return connectionManager.queryWithConnection(source, connection -> getTableDetail(connection, tableName))
                .onErrorMap(e -> new ServiceException("数据库连接失败！" + e.getMessage(), e));
    }

    @Override
    public Mono<DbTable> getTableDetail(Connection connection, String tableName) {
        String type = databaseType(connection);
        return getTable(connection, tableName, type)
                .flatMap(table -> getColumns(connection, tableName, type)
                        .doOnNext(table::setColumns)
                        .then(getPrimaryKeys(connection, tableName, type)
                                .doOnNext(keys -> keys.forEach(table::setPrimaryKeyColumn)))
                        .then(getIndexes(connection, tableName, type).doOnNext(table::setIndexes))
                        .thenReturn(table));
    }

    private Mono<DbTable> getTable(Connection connection, String tableName, String type) {
        if (isH2(type)) {
            return getH2Table(connection, tableName);
        }
        return R2dbcResultUtils.query(connection, """
                        SELECT TABLE_NAME, TABLE_COMMENT
                        FROM information_schema.TABLES
                        WHERE TABLE_SCHEMA = DATABASE()
                          AND TABLE_TYPE = 'BASE TABLE'
                          AND TABLE_NAME = ?
                        """, List.of(tableName))
                .flatMap(rows -> {
                    if (rows.getRows().isEmpty()) return Mono.empty();
                    DbTable table = new DbTable();
                    Map<String, Object> row = rows.getRows().getFirst();
                    table.setName(string(row.get("TABLE_NAME")));
                    table.setComment(string(row.get("TABLE_COMMENT")));
                    return Mono.just(table);
                });
    }

    private Mono<DbTable> getH2Table(Connection connection, String tableName) {
        return R2dbcResultUtils.query(connection, """
                        SELECT TABLE_NAME, REMARKS
                        FROM INFORMATION_SCHEMA.TABLES
                        WHERE TABLE_SCHEMA = SCHEMA()
                          AND TABLE_TYPE = 'BASE TABLE'
                          AND TABLE_NAME = ?
                        """, List.of(tableName))
                .flatMap(rows -> {
                    if (rows.getRows().isEmpty()) {
                        return Mono.empty();
                    }
                    DbTable table = new DbTable();
                    Map<String, Object> row = rows.getRows().getFirst();
                    table.setName(string(row.get("TABLE_NAME")));
                    table.setComment(string(row.get("REMARKS")));
                    return Mono.just(table);
                });
    }

    private Mono<List<DbTable.DbColumn>> getColumns(Connection connection, String tableName, String type) {
        if (isH2(type)) {
            return getH2Columns(connection, tableName);
        }
        return R2dbcResultUtils.query(connection, """
                        SELECT COLUMN_NAME, COLUMN_TYPE, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH,
                               NUMERIC_PRECISION, NUMERIC_SCALE, IS_NULLABLE, COLUMN_DEFAULT,
                               COLUMN_COMMENT, EXTRA, CHARACTER_SET_NAME, COLLATION_NAME, COLUMN_KEY
                        FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE()
                          AND TABLE_NAME = ?
                        ORDER BY ORDINAL_POSITION
                        """, List.of(tableName))
                .map(rows -> rows.getRows().stream().map(this::mysqlColumn).toList());
    }

    private Mono<List<DbTable.DbColumn>> getH2Columns(Connection connection, String tableName) {
        return R2dbcResultUtils.query(connection, """
                        SELECT COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH,
                               NUMERIC_PRECISION, NUMERIC_SCALE, IS_NULLABLE, COLUMN_DEFAULT,
                               REMARKS, IS_IDENTITY
                        FROM INFORMATION_SCHEMA.COLUMNS
                        WHERE TABLE_SCHEMA = SCHEMA()
                          AND TABLE_NAME = ?
                        ORDER BY ORDINAL_POSITION
                        """, List.of(tableName))
                .map(rows -> rows.getRows().stream().map(this::h2Column).toList());
    }

    private Mono<List<DbTable.DbIndex>> getIndexes(Connection connection, String tableName, String type) {
        if (isH2(type)) {
            return getH2Indexes(connection, tableName);
        }
        return R2dbcResultUtils.query(connection, "SHOW INDEX FROM " + R2dbcUrlUtils.quote(tableName) + " WHERE Key_name != 'PRIMARY'")
                .map(rows -> mysqlIndexes(rows.getRows()));
    }

    private Mono<List<String>> getPrimaryKeys(Connection connection, String tableName, String type) {
        if (isH2(type)) {
            return getH2PrimaryKeys(connection, tableName);
        }
        return R2dbcResultUtils.query(connection, """
                        SELECT COLUMN_NAME
                        FROM information_schema.KEY_COLUMN_USAGE
                        WHERE TABLE_SCHEMA = DATABASE()
                          AND TABLE_NAME = ?
                          AND CONSTRAINT_NAME = 'PRIMARY'
                        ORDER BY ORDINAL_POSITION
                        """, List.of(tableName))
                .map(rows -> rows.getRows().stream().map(row -> string(row.get("COLUMN_NAME"))).toList());
    }

    private Mono<List<String>> getH2PrimaryKeys(Connection connection, String tableName) {
        return R2dbcResultUtils.query(connection, """
                        SELECT COLUMN_NAME
                        FROM INFORMATION_SCHEMA.INDEX_COLUMNS
                        WHERE TABLE_SCHEMA = SCHEMA()
                          AND TABLE_NAME = ?
                          AND UPPER(INDEX_NAME) LIKE '%PRIMARY%'
                        ORDER BY ORDINAL_POSITION
                        """, List.of(tableName))
                .map(rows -> rows.getRows().stream().map(row -> string(row.get("COLUMN_NAME"))).toList())
                .onErrorReturn(List.of());
    }

    private Mono<List<DbTable.DbIndex>> getH2Indexes(Connection connection, String tableName) {
        return R2dbcResultUtils.query(connection, """
                        SELECT INDEX_NAME, COLUMN_NAME, ORDINAL_POSITION, IS_UNIQUE
                        FROM INFORMATION_SCHEMA.INDEX_COLUMNS
                        WHERE TABLE_SCHEMA = SCHEMA()
                          AND TABLE_NAME = ?
                        ORDER BY INDEX_NAME, ORDINAL_POSITION
                        """, List.of(tableName))
                .map(rows -> {
                    Map<String, DbTable.DbIndex> indexes = new LinkedHashMap<>();
                    for (Map<String, Object> row : rows.getRows()) {
                        String name = string(row.get("INDEX_NAME"));
                        if ("PRIMARY_KEY".equalsIgnoreCase(name) || StringUtils.containsIgnoreCase(name, "PRIMARY")) {
                            continue;
                        }
                        DbTable.DbIndex index = indexes.computeIfAbsent(name, key -> {
                            DbTable.DbIndex created = new DbTable.DbIndex();
                            created.setName(key);
                            created.setType(Boolean.FALSE.equals(bool(row.get("IS_UNIQUE")))
                                    ? DbTable.DbIndexType.BTREE : DbTable.DbIndexType.UNIQUE);
                            created.setFields(new ArrayList<>());
                            return created;
                        });
                        index.getFields().add(string(row.get("COLUMN_NAME")));
                    }
                    return new ArrayList<>(indexes.values());
                });
    }

    private DbTable.DbColumn mysqlColumn(Map<String, Object> row) {
        DbTable.DbColumn column = baseColumn(row);
        String columnType = StringUtils.lowerCase(string(row.get("COLUMN_TYPE")));
        String dataType = StringUtils.lowerCase(string(row.get("DATA_TYPE")));
        column.setType(dataType);
        column.setUnsigned(StringUtils.contains(columnType, "unsigned"));
        column.setNullable("YES".equalsIgnoreCase(string(row.get("IS_NULLABLE"))));
        column.setDefaultValue(string(row.get("COLUMN_DEFAULT")));
        column.setComment(string(row.get("COLUMN_COMMENT")));
        column.setCharacterSet(string(row.get("CHARACTER_SET_NAME")));
        column.setCollation(string(row.get("COLLATION_NAME")));
        column.setAutoIncrement(StringUtils.containsIgnoreCase(string(row.get("EXTRA")), "auto_increment"));
        column.setPrimary("PRI".equalsIgnoreCase(string(row.get("COLUMN_KEY"))));
        fillLength(column, row);
        return column;
    }

    private DbTable.DbColumn h2Column(Map<String, Object> row) {
        DbTable.DbColumn column = baseColumn(row);
        applyType(column, StringUtils.lowerCase(string(row.get("DATA_TYPE")), Locale.ROOT));
        column.setNullable("YES".equalsIgnoreCase(string(row.get("IS_NULLABLE"))));
        column.setDefaultValue(string(row.get("COLUMN_DEFAULT")));
        column.setComment(string(row.get("REMARKS")));
        column.setAutoIncrement("YES".equalsIgnoreCase(string(row.get("IS_IDENTITY"))));
        fillLength(column, row);
        return column;
    }

    private DbTable.DbColumn baseColumn(Map<String, Object> row) {
        DbTable.DbColumn column = new DbTable.DbColumn();
        column.setName(string(row.get("COLUMN_NAME")));
        return column;
    }

    private void applyType(DbTable.DbColumn column, String typeName) {
        if (StringUtils.contains(typeName, "unsigned")) {
            column.setUnsigned(true);
            column.setType(StringUtils.substringBefore(typeName, "unsigned").trim());
        } else {
            column.setType(typeName);
        }
    }

    private void fillLength(DbTable.DbColumn column, Map<String, Object> row) {
        if (!DbType.isDate(column.getType())) {
            column.setLength(integer(firstNonNull(row.get("CHARACTER_MAXIMUM_LENGTH"), row.get("NUMERIC_PRECISION"))));
        }
        if (DbType.isNumeric(column.getType())) {
            column.setPrecision(integer(row.get("NUMERIC_SCALE")));
        }
    }

    private List<DbTable.DbIndex> mysqlIndexes(List<? extends Map<String, Object>> rows) {
        Map<String, DbTable.DbIndex> indexes = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            DbTable.DbIndex index = new DbTable.DbIndex();
            index.setName(string(row.get("Key_name")));
            index.setType(determineIndexType(row));
            String field = string(row.get("Column_name"));
            if (indexes.containsKey(index.getName())) {
                int sequence = integer(row.get("Seq_in_index"));
                indexes.get(index.getName()).getFields().add(sequence - 1, field);
                continue;
            }
            List<String> fields = new ArrayList<>();
            fields.add(field);
            index.setFields(fields);
            index.setComment(string(row.get("Index_comment")));
            indexes.put(index.getName(), index);
        }
        return new ArrayList<>(indexes.values());
    }

    private DbTable.DbIndexType determineIndexType(Map<String, Object> row) {
        String type = string(row.get("Index_type"));
        if ("FULLTEXT".equalsIgnoreCase(type)) {
            return DbTable.DbIndexType.FULLTEXT;
        }
        return integer(row.get("Non_unique")) == 1 ? DbTable.DbIndexType.BTREE : DbTable.DbIndexType.UNIQUE;
    }

    private String tableSql(String type) {
        if (isH2(type)) {
            return """
                    SELECT TABLE_NAME, REMARKS
                    FROM INFORMATION_SCHEMA.TABLES
                    WHERE TABLE_SCHEMA = SCHEMA()
                      AND TABLE_TYPE = 'BASE TABLE'
                    ORDER BY TABLE_NAME
                    """;
        }
        return """
                SELECT TABLE_NAME, TABLE_COMMENT
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_TYPE = 'BASE TABLE'
                ORDER BY TABLE_NAME
                """;
    }

    private String databaseType(Connection connection) {
        String product = StringUtils.lowerCase(connection.getMetadata().getDatabaseProductName(), Locale.ROOT);
        return StringUtils.contains(product, TYPE_H2) ? TYPE_H2 : TYPE_MYSQL;
    }

    private boolean isH2(String type) {
        return TYPE_H2.equalsIgnoreCase(type);
    }

    private static Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return StringUtils.isBlank(string(value)) ? null : Integer.parseInt(string(value));
    }

    private static Boolean bool(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value == null ? null : Boolean.parseBoolean(string(value));
    }
}

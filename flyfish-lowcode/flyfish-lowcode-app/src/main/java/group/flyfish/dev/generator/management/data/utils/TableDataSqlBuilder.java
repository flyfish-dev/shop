package group.flyfish.dev.generator.management.data.utils;

import group.flyfish.dev.generator.management.data.bean.TableDataQo;
import group.flyfish.dev.generator.management.data.bean.TableDataRow;
import group.flyfish.dev.generator.management.data.bean.row.TableIdentity;
import group.flyfish.dev.generator.management.data.bean.row.TableRowInsertDto;
import group.flyfish.dev.generator.management.utils.R2dbcUrlUtils;
import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class TableDataSqlBuilder {

    private TableDataSqlBuilder() {
    }

    public static BoundSql selectPage(TableDataQo qo) {
        String columns = CollectionUtils.isEmpty(qo.getColumns())
                ? "*"
                : qo.getColumns().stream().map(R2dbcUrlUtils::quote).collect(Collectors.joining(", "));
        StringBuilder sql = new StringBuilder("SELECT ")
                .append(columns)
                .append(" FROM ")
                .append(R2dbcUrlUtils.quote(qo.getTableName()));
        appendWhere(sql, qo);
        if (qo.isHasOrder()) {
            sql.append(" ORDER BY ").append(qo.getOrder());
        }
        sql.append(" LIMIT ").append(qo.offset()).append(", ").append(qo.getSize());
        return new BoundSql(sql.toString(), List.of());
    }

    public static BoundSql count(TableDataQo qo) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(1) AS total FROM ")
                .append(R2dbcUrlUtils.quote(qo.getTableName()));
        appendWhere(sql, qo);
        return new BoundSql(sql.toString(), List.of());
    }

    public static BoundSql insert(TableRowInsertDto insert) {
        List<String> columns = new ArrayList<>(insert.getColumns());
        String sql = "INSERT INTO " + R2dbcUrlUtils.quote(insert.getTableName()) + " (" +
                columns.stream().map(R2dbcUrlUtils::quote).collect(Collectors.joining(", ")) +
                ") VALUES (" +
                columns.stream().map(column -> "?").collect(Collectors.joining(", ")) +
                ")";
        List<Object> values = columns.stream().map(insert.getData()::get).collect(Collectors.toList());
        return new BoundSql(sql, values);
    }

    public static BoundSql update(TableIdentity update) {
        TableDataRow data = update.getData();
        List<String> columns = new ArrayList<>(data.keySet());
        List<Object> values = columns.stream().map(data::get).collect(Collectors.toCollection(ArrayList::new));
        TableDataRow condition = update.getCondition();
        values.addAll(condition.values());
        String sql = "UPDATE " + R2dbcUrlUtils.quote(update.getTableName()) + " SET " +
                columns.stream().map(column -> R2dbcUrlUtils.quote(column) + " = ?").collect(Collectors.joining(", ")) +
                " WHERE " + condition.keySet().stream()
                .map(column -> R2dbcUrlUtils.quote(column) + " = ?")
                .collect(Collectors.joining(" AND "));
        return new BoundSql(sql, values);
    }

    public static BoundSql delete(TableIdentity delete) {
        TableDataRow condition = delete.getCondition();
        String sql = "DELETE FROM " + R2dbcUrlUtils.quote(delete.getTableName()) +
                " WHERE " + condition.keySet().stream()
                .map(column -> R2dbcUrlUtils.quote(column) + " = ?")
                .collect(Collectors.joining(" AND "));
        return new BoundSql(sql, new ArrayList<>(condition.values()));
    }

    private static void appendWhere(StringBuilder sql, TableDataQo qo) {
        List<String> conditions = new ArrayList<>();
        if (qo.isHasQuery()) {
            conditions.add("(" + qo.getQuery() + ")");
        }
        if (qo.isHasFilter()) {
            conditions.add(qo.getFilter());
        }
        if (!conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }
    }

    @Data
    public static class BoundSql {

        private final String sql;

        private final List<?> bindings;
    }
}

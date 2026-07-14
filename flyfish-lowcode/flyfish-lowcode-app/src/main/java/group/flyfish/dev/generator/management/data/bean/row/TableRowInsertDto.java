package group.flyfish.dev.generator.management.data.bean.row;

import group.flyfish.dev.generator.management.data.bean.TableDataRow;
import lombok.Data;

import java.util.Collection;
import java.util.Set;

/**
 * 表行插入dto
 *
 * @author wangyu
 */
@Data
public class TableRowInsertDto {

    private String tableName;

    private Set<String> columns;

    private Collection<Object> values;

    private TableDataRow data;

    public TableRowInsertDto(String table, TableDataRow data) {
        this.tableName = table;
        this.columns = data.keySet();
        this.values = data.values();
        this.data = data;
    }
}

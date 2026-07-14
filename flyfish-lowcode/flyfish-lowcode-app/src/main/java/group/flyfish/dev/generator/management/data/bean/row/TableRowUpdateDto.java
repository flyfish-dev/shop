package group.flyfish.dev.generator.management.data.bean.row;

import group.flyfish.dev.bean.DbTable;
import group.flyfish.dev.generator.management.data.bean.TableDataChange;
import group.flyfish.dev.generator.management.data.bean.TableDataRow;
import lombok.Data;

import java.util.List;

/**
 * 数据行更新实体
 *
 * @author wangyu
 */
@Data
public class TableRowUpdateDto implements TableIdentity {

    // 表名
    private String tableName;

    // 主键
    private List<String> primaryKeys;

    // 要更新的数据
    private TableDataRow data;

    // 原数据
    private TableDataRow old;

    public TableRowUpdateDto(TableDataChange change, DbTable table) {
        this.tableName = table.getName();
        this.primaryKeys = table.primaryKey().getFields();
        this.data = change.getChange();
        this.old = change.getRecord();
    }

    @Override
    public TableDataRow getSource() {
        return old;
    }
}

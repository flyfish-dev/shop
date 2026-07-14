package group.flyfish.dev.generator.management.data.bean.row;

import group.flyfish.dev.bean.DbTable;
import group.flyfish.dev.generator.management.data.bean.TableDataRow;
import lombok.Data;

import java.util.List;

/**
 * 表行删除实体
 *
 * @author wangyu
 */
@Data
public class TableRowDeleteDto implements TableIdentity {

    // 表名
    private String tableName;

    // 主键
    private List<String> primaryKeys;

    // 要删除的数据
    private TableDataRow data;

    public TableRowDeleteDto(DbTable table) {
        this.primaryKeys = table.primaryKey().getFields();
        this.tableName = table.getName();
    }

    @Override
    public TableDataRow getSource() {
        return data;
    }
}

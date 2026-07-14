package group.flyfish.dev.generator.management.data.bean;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 表格数据删除
 * @author wangyu
 */
@Data
@AllArgsConstructor
public class TableDataDeleteDto {

    private String source;

    private String table;

    private List<TableDataRow> rows;
}

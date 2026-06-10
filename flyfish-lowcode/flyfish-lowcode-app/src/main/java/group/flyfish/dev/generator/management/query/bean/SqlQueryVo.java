package group.flyfish.dev.generator.management.query.bean;

import group.flyfish.dev.generator.management.data.bean.TableDataRow;
import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * sql查询结果
 */
@Data
public class SqlQueryVo {

    private List<String> columns;

    private List<TableDataRow> rows;

    private Integer total;

    public static SqlQueryVo executed() {
        SqlQueryVo vo = new SqlQueryVo();
        vo.setTotal(0);
        vo.setColumns(Collections.emptyList());
        vo.setRows(Collections.emptyList());
        return vo;
    }
}

package group.flyfish.dev.generator.management.data.bean;

import lombok.Data;

/**
 * 数据变更
 *
 * @author wangyu
 */
@Data
public class TableDataChange {

    private TableDataRow record;

    private TableDataRow change;

    private boolean create;
}

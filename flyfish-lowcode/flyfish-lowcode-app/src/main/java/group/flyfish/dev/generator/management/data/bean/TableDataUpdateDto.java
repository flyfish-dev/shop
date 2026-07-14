package group.flyfish.dev.generator.management.data.bean;

import lombok.Data;

import java.util.Map;

/**
 * 表格数据更新实体
 *
 * @author wangyu
 */
@Data
public class TableDataUpdateDto {

    private String source;

    private String table;

    private Map<Integer, TableDataChange> changes;
}

package group.flyfish.dev.generator.management.data.bean.row;

import group.flyfish.dev.generator.management.data.bean.TableDataRow;
import group.flyfish.dev.generator.management.data.utils.TableIdentityUtils;

import java.util.List;

/**
 * 支持标识的实体
 * @author wangyu
 */
public interface TableIdentity {

    String getTableName();

    List<String> getPrimaryKeys();

    TableDataRow getSource();

    TableDataRow getData();

    /**
     * 获取具体条件
     *
     * @return 结果
     */
    default TableDataRow getCondition() {
        return TableIdentityUtils.getIdentity(this);
    }
}

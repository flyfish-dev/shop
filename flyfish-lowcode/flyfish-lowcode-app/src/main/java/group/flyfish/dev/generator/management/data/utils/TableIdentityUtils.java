package group.flyfish.dev.generator.management.data.utils;

import group.flyfish.dev.generator.management.data.bean.TableDataRow;
import group.flyfish.dev.generator.management.data.bean.row.TableIdentity;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

public final class TableIdentityUtils {

    /**
     * 获取标识实体
     *
     * @param data 数据信息
     * @return 结果
     */
    public static TableDataRow getIdentity(TableIdentity data) {
        List<String> primaryKeys = data.getPrimaryKeys();
        TableDataRow source = data.getSource();
        // 存在主键，以主键集合为主
        if (CollectionUtils.isNotEmpty(primaryKeys)) {
            return primaryKeys.stream()
                    .reduce(new TableDataRow(), (result, field) -> {
                        if (source.containsKey(field)) {
                            result.put(field, source.get(field));
                        }
                        return result;
                    }, (a, b) -> a);
        }
        // 不存在，则以原数据为具体条件
        return source;
    }
}

package group.flyfish.dev.generator.management.data;

import group.flyfish.dev.generator.management.data.bean.TableDataDeleteDto;
import group.flyfish.dev.generator.management.data.bean.TableDataPage;
import group.flyfish.dev.generator.management.data.bean.TableDataQo;
import group.flyfish.dev.generator.management.data.bean.TableDataUpdateDto;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 表数据服务
 *
 * @author wangyu
 */
public interface DbTableDataService {

    /**
     * 表数据查询
     *
     * @param qo 查询实体
     * @return 结果
     */
    Mono<TableDataPage<Map<String, Object>>> getPageList(TableDataQo qo);

    /**
     * 更新数据
     *
     * @param update 更新内容
     * @return 结果
     */
    Mono<Integer> updateData(TableDataUpdateDto update);

    /**
     * 删除数据
     *
     * @param delete 要删除的行
     * @return 结果
     */
    Mono<Integer> deleteData(TableDataDeleteDto delete);
}

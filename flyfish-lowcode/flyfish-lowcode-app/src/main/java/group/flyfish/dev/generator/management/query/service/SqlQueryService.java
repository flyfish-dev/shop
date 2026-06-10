package group.flyfish.dev.generator.management.query.service;

import group.flyfish.dev.generator.management.query.bean.SqlQueryQo;
import group.flyfish.dev.generator.management.query.bean.SqlQueryVo;
import reactor.core.publisher.Mono;

/**
 * sql查询服务
 *
 * @author wangyu
 */
public interface SqlQueryService {

    /**
     * sql查询
     *
     * @param qo 查询实体
     * @return 结果
     */
    Mono<SqlQueryVo> query(SqlQueryQo qo);
}

package group.flyfish.dev.generator.management.repository;

import group.flyfish.dev.bean.DbSource;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

/**
 * 数据源仓库
 *
 * @author wangyu
 */
public interface DbSourceRepository extends R2dbcRepository<DbSource, Long> {

    /**
     * 通过键查找
     *
     * @param key 键
     * @return 结果
     */
    Mono<DbSource> findByKey(String key);

    /**
     * 通过键删除
     *
     * @param key 键
     * @return 结果
     */
    Mono<Void> deleteByKey(String key);
}

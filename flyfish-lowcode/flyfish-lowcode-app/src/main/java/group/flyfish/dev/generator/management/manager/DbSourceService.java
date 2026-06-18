package group.flyfish.dev.generator.management.manager;

import group.flyfish.dev.bean.DbSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 数据库连接仓库，持久化
 *
 * @author wangyu
 */
public interface DbSourceService {

    /**
     * 持久化保存
     *
     * @param source 数据源
     */
    Mono<Void> save(DbSource source);

    /**
     * 通过所有者查询
     *
     * @param owner 所有者
     * @return 结果
     */
    Flux<DbSource> list(String owner);

    /**
     * 通过key获取数据源
     *
     * @param source 数据源信息
     * @return 结果
     */
    Mono<DbSource> get(DbSource source);

    /**
     * 通过key删除数据源
     *
     * @param source 数据源
     */
    Mono<Void> remove(DbSource source);
}

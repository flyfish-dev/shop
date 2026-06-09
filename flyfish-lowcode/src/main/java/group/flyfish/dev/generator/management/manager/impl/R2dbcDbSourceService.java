package group.flyfish.dev.generator.management.manager.impl;

import group.flyfish.dev.bean.DbSource;
import group.flyfish.dev.generator.management.manager.DbConnectionManager;
import group.flyfish.dev.generator.management.manager.DbSourceKeyGenerator;
import group.flyfish.dev.generator.management.manager.DbSourceService;
import group.flyfish.dev.generator.management.repository.DbSourceRepository;
import group.flyfish.dev.generator.management.utils.R2dbcUrlUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Example;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 基于r2dbc的数据源服务
 *
 * @author wangyu
 */
@Service
@RequiredArgsConstructor
public class R2dbcDbSourceService implements DbSourceService {

    private final DbSourceKeyGenerator keyGenerator;

    private final DbSourceRepository repository;

    /**
     * 持久化保存
     * 携带id为更新，不携带为插入
     *
     * @param source 数据源
     */
    @Override
    public Mono<Void> save(DbSource source) {
        R2dbcUrlUtils.materialize(source);
        source.setKey(keyGenerator.generate(source));
        Mono<DbSource> target = source.getId() == null
                ? repository.findByKey(source.getKey()).map(saved -> source.setId(saved.getId())).defaultIfEmpty(source)
                : Mono.just(source);
        return target
                .flatMap(repository::save)
                .then();
    }

    /**
     * 通过所有者查询
     *
     * @param owner 所有者
     * @return 结果
     */
    @Override
    public Flux<DbSource> list(String owner) {
        DbSource probe = new DbSource();
        probe.setOwner(owner);
        return repository.findAll(Example.of(probe))
                .map(R2dbcUrlUtils::materialize)
                .flatMap(data -> {
                    if (data.getKey() != null) {
                        return Mono.just(data);
                    }
                    data.setKey(keyGenerator.generate(data));
                    return repository.save(data);
                })
                .map(data -> data.setPassword(DbConnectionManager.STARRED_PASSWORD));
    }

    /**
     * 通过key获取数据源
     *
     * @param source 数据源信息
     * @return 结果
     */
    @Override
    public Mono<DbSource> get(DbSource source) {
        return repository.findByKey(source.getKey()).map(R2dbcUrlUtils::materialize);
    }

    /**
     * 通过key删除数据源
     *
     * @param source 数据源
     */
    @Override
    public Mono<Void> remove(DbSource source) {
        return repository.deleteByKey(source.getKey());
    }
}

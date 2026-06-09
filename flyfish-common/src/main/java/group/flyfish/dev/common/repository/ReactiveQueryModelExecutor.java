package group.flyfish.dev.common.repository;

import group.flyfish.dev.common.base.reactive.Qo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.relational.core.query.Criteria;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

/**
 * 查询模型支持
 *
 * @author wangyu
 * 基于repo的公共扩展
 */
public interface ReactiveQueryModelExecutor<T> {

    /**
     * 通过名称查找一个
     *
     * @param name 名称
     * @return 结果
     */
    Mono<T> findByName(String name);

    /**
     * 通过字段精确查找一个，自动应用逻辑删除筛选。
     *
     * @param key 字段
     * @param value 值
     * @return 结果
     */
    Mono<T> findOneBy(String key, Object value);

    /**
     * 通过条件查询，自动应用逻辑删除筛选。
     *
     * @param criteria 条件
     * @param sort 排序
     * @return 结果
     */
    Flux<T> findAllBy(Criteria criteria, Sort sort);

    /**
     * Returns a single entity matching the given {@link Qo} or {@link Optional#empty()} if none was found.
     *
     * @param query must not be {@literal null}.
     * @return a single entity matching the given {@link Qo} or {@link Optional#empty()} if none was found.
     * @throws org.springframework.dao.IncorrectResultSizeDataAccessException if the Qo yields more than one
     *                                                                        result.
     */
    Mono<T> findOne(Qo<T> query);

    /**
     * Returns all entities matching the given {@link Qo}. In case no match could be found an empty
     * {@link Iterable} is returned.
     *
     * @param query must not be {@literal null}.
     * @return all entities matching the given {@link Qo}.
     */
    Flux<T> findAll(Qo<T> query);

    /**
     * Returns all entities matching the given {@link Qo} applying the given {@link Sort}. In case no match could
     * be found an empty {@link Iterable} is returned.
     *
     * @param query must not be {@literal null}.
     * @param sort  the {@link Sort} specification to sort the results by, may be {@link Sort#empty()}, must not be
     *              {@literal null}.
     * @return all entities matching the given {@link Qo}.
     * @since 1.10
     */
    Flux<T> findAll(Qo<T> query, Sort sort);

    /**
     * Returns a {@link Page} of entities matching the given {@link Qo}. In case no match could be found, an empty
     * {@link Page} is returned.
     *
     * @param query    must not be {@literal null}.
     * @param pageable may be {@link Pageable#unpaged()}, must not be {@literal null}.
     * @return a {@link Page} of entities matching the given {@link Qo}.
     */
    Mono<Page<T>> findAll(Qo<T> query, Pageable pageable);

    /**
     * Returns the number of instances matching the given {@link Qo}.
     *
     * @param query the {@link Qo} to count instances for, must not be {@literal null}.
     * @return the number of instances matching the {@link Qo}.
     */
    Mono<Long> count(Qo<T> query);

    /**
     * 通过特定键的集合查询
     *
     * @param key    键
     * @param values 集合
     * @return 结果
     */
    Flux<T> findAllByValues(String key, List<?> values);

    /**
     * Checks whether the data store contains elements that match the given {@link Qo}.
     *
     * @param query the {@link Qo} to use for the existence check, must not be {@literal null}.
     * @return {@literal true} if the data store contains elements that match the given {@link Qo}.
     */
    Mono<Boolean> exists(Qo<T> query);

    /**
     * 删除全部
     *
     * @param qo 查询实体
     * @return 结果
     */
    Mono<Void> deleteAll(Qo<T> qo);
}

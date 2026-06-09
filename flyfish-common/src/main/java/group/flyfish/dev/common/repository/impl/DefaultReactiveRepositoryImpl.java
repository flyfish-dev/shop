package group.flyfish.dev.common.repository.impl;

import group.flyfish.dev.common.base.reactive.AuditDomain;
import group.flyfish.dev.common.base.reactive.Qo;
import group.flyfish.dev.common.repository.DefaultReactiveRepository;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.reactivestreams.Publisher;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.convert.R2dbcConverter;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.r2dbc.repository.support.SimpleR2dbcRepository;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.data.relational.repository.query.RelationalEntityInformation;
import org.springframework.data.relational.repository.query.RelationalExampleMapper;
import org.springframework.objenesis.instantiator.util.ClassUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;


/**
 * 默认的异步仓库实现
 *
 * @param <T> 泛型
 */
@Slf4j
public class DefaultReactiveRepositoryImpl<T extends AuditDomain> extends SimpleR2dbcRepository<T, Long>
        implements DefaultReactiveRepository<T> {

    private final R2dbcEntityOperations entityOperations;

    private final RelationalEntityInformation<T, Long> entityInformation;

    private final RelationalExampleMapper exampleMapper;

    public DefaultReactiveRepositoryImpl(@NonNull RelationalEntityInformation<T, Long> entityInformation,
                                         @NonNull R2dbcEntityOperations entityOperations,
                                         @NonNull R2dbcConverter r2dbcConverter) {
        super(entityInformation, entityOperations, r2dbcConverter);
        this.entityOperations = entityOperations;
        this.entityInformation = entityInformation;
        this.exampleMapper = new RelationalExampleMapper(r2dbcConverter.getMappingContext());
    }

    @Override
    public <S extends T> Mono<S> save(S entity) {
        return super.save(prepareForSave(entity));
    }

    @Override
    public <S extends T> Flux<S> saveAll(Iterable<S> entities) {
        return super.saveAll(Flux.fromIterable(entities).map(this::prepareForSave));
    }

    @Override
    public <S extends T> Flux<S> saveAll(Publisher<S> entityStream) {
        return super.saveAll(Flux.from(entityStream).map(this::prepareForSave));
    }

    @Override
    public Mono<T> findById(Long id) {
        return entityOperations.selectOne(queryNotDeleted(Criteria.where("id").is(id)), entityInformation.getJavaType());
    }

    @Override
    public Mono<T> findById(Publisher<Long> idPublisher) {
        return Mono.from(idPublisher).flatMap(this::findById);
    }

    @Override
    public Mono<Boolean> existsById(Long id) {
        return findById(id).hasElement();
    }

    @Override
    public Mono<Boolean> existsById(Publisher<Long> idPublisher) {
        return Mono.from(idPublisher).flatMap(this::existsById);
    }

    @Override
    public Flux<T> findAll() {
        return entityOperations.select(queryNotDeleted(), entityInformation.getJavaType());
    }

    @Override
    public Flux<T> findAll(Sort sort) {
        return entityOperations.select(queryNotDeleted().sort(sort), entityInformation.getJavaType());
    }

    @Override
    public Flux<T> findAllById(Iterable<Long> ids) {
        return entityOperations.select(queryNotDeleted(Criteria.where("id").in(ids)), entityInformation.getJavaType());
    }

    @Override
    public Flux<T> findAllById(Publisher<Long> idStream) {
        return Flux.from(idStream).collectList().flatMapMany(this::findAllById);
    }

    @Override
    public Mono<Long> count() {
        return entityOperations.count(queryNotDeleted(), entityInformation.getJavaType());
    }

    @Override
    public Mono<Void> deleteById(Long id) {
        return findById(id).flatMap(this::delete);
    }

    @Override
    public Mono<Void> deleteById(Publisher<Long> idPublisher) {
        return Mono.from(idPublisher).flatMap(this::deleteById);
    }

    @Override
    public Mono<Void> delete(T entity) {
        entity.setDelete(true);
        return save(entity).then();
    }

    @Override
    public Mono<Void> deleteAllById(Iterable<? extends Long> ids) {
        return Flux.fromIterable(ids).flatMap(this::deleteById).then();
    }

    @Override
    public Mono<Void> deleteAll(Iterable<? extends T> entities) {
        return Flux.fromIterable(entities).flatMap(this::delete).then();
    }

    @Override
    public Mono<Void> deleteAll(Publisher<? extends T> entityStream) {
        return Flux.from(entityStream).flatMap(this::delete).then();
    }

    @Override
    public Mono<Void> deleteAll() {
        return entityOperations.update(queryNotDeleted(), Update.update("is_delete", true), entityInformation.getJavaType()).then();
    }

    @Override
    public <S extends T> Mono<S> findOne(Example<S> example) {
        return entityOperations.selectOne(withNotDeleted(exampleMapper.getMappedExample(example)), example.getProbeType());
    }

    @Override
    public <S extends T> Flux<S> findAll(Example<S> example) {
        return entityOperations.select(withNotDeleted(exampleMapper.getMappedExample(example)), example.getProbeType());
    }

    @Override
    public <S extends T> Flux<S> findAll(Example<S> example, Sort sort) {
        return entityOperations.select(withNotDeleted(exampleMapper.getMappedExample(example)).sort(sort), example.getProbeType());
    }

    @Override
    public <S extends T> Mono<Long> count(Example<S> example) {
        return entityOperations.count(withNotDeleted(exampleMapper.getMappedExample(example)), example.getProbeType());
    }

    @Override
    public <S extends T> Mono<Boolean> exists(Example<S> example) {
        return entityOperations.exists(withNotDeleted(exampleMapper.getMappedExample(example)), example.getProbeType());
    }

    /**
     * 通过名称查找一个
     *
     * @param name 名称
     * @return 结果
     */
    @Override
    public Mono<T> findByName(String name) {
        if (StringUtils.isNotBlank(name)) {
            return findOneBy("name", name);
        }
        return Mono.empty();
    }

    @Override
    public Mono<T> findOneBy(String key, Object value) {
        if (value == null) {
            return Mono.empty();
        }
        return entityOperations.selectOne(queryNotDeleted(Criteria.where(key).is(value)),
                entityInformation.getJavaType());
    }

    @Override
    public Flux<T> findAllBy(Criteria criteria, Sort sort) {
        Query query = queryNotDeleted(criteria);
        if (sort != null && sort.isSorted()) {
            query.sort(sort);
        }
        return entityOperations.select(query, entityInformation.getJavaType());
    }

    /**
     * Returns a single entity matching the given {@link Qo} or {@link Optional#empty()} if none was found.
     *
     * @param query must not be {@literal null}.
     * @return a single entity matching the given {@link Qo} or {@link Optional#empty()} if none was found.
     * @throws IncorrectResultSizeDataAccessException if the Qo yields more than one
     *                                                result.
     */
    @Override
    public Mono<T> findOne(Qo<T> query) {
        return Mono.justOrEmpty(getQuery(query))
                .flatMap(querying -> entityOperations.selectOne(querying, entityInformation.getJavaType()));
    }

    /**
     * Returns all entities matching the given {@link Qo}. In case no match could be found an empty
     * {@link Iterable} is returned.
     *
     * @param query must not be {@literal null}.
     * @return all entities matching the given {@link Qo}.
     */
    @Override
    public Flux<T> findAll(Qo<T> query) {
        return Mono.justOrEmpty(getQuery(query))
                .flatMapMany(querying -> entityOperations.select(querying, entityInformation.getJavaType()));
    }

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
    @Override
    public Flux<T> findAll(Qo<T> query, Sort sort) {
        return Mono.justOrEmpty(getQuery(query))
                .flatMapMany(querying -> entityOperations.select(querying.sort(sort), entityInformation.getJavaType()));
    }

    /**
     * Returns a {@link Page} of entities matching the given {@link Qo}. In case no match could be found, an empty
     * {@link Page} is returned.
     *
     * @param query    must not be {@literal null}.
     * @param pageable may be {@link Pageable#unpaged()}, must not be {@literal null}.
     * @return a {@link Page} of entities matching the given {@link Qo}.
     */
    @Override
    public Mono<Page<T>> findAll(Qo<T> query, Pageable pageable) {
        return Mono.justOrEmpty(getQuery(query))
                .flatMap(querying -> entityOperations.select(querying.with(pageable), entityInformation.getJavaType())
                        .collectList()
                        .flatMap(list -> ReactivePageableExecutionUtils.getPage(list, pageable, this.count(query))))
                .defaultIfEmpty(Page.empty());
    }

    /**
     * Returns the number of instances matching the given {@link Qo}.
     *
     * @param query the {@link Qo} to count instances for, must not be {@literal null}.
     * @return the number of instances matching the {@link Qo}.
     */
    @Override
    public Mono<Long> count(Qo<T> query) {
        return Mono.justOrEmpty(getQuery(query))
                .flatMap(querying -> this.entityOperations.count(querying, entityInformation.getJavaType())
                        .defaultIfEmpty(0L));
    }

    /**
     * 通过特定键的集合查询
     *
     * @param key    键
     * @param values 集合
     * @return 结果
     */
    @Override
    public Flux<T> findAllByValues(String key, List<?> values) {
        if (CollectionUtils.isEmpty(values)) {
            return Flux.empty();
        }
        Criteria criteria = Criteria.where(key).in(values);
        Query query = queryNotDeleted(criteria);
        return entityOperations.select(query, entityInformation.getJavaType());
    }

    /**
     * Checks whether the data store contains elements that match the given {@link Qo}.
     *
     * @param query the {@link Qo} to use for the existence check, must not be {@literal null}.
     * @return {@literal true} if the data store contains elements that match the given {@link Qo}.
     */
    @Override
    public Mono<Boolean> exists(Qo<T> query) {
        return Mono.justOrEmpty(getQuery(query))
                .flatMap(querying -> entityOperations.exists(querying, entityInformation.getJavaType()))
                .defaultIfEmpty(false);
    }

    /**
     * 删除全部
     *
     * @param qo 查询实体
     * @return 结果
     */
    @Override
    public Mono<Void> deleteAll(Qo<T> qo) {
        return Mono.justOrEmpty(getQuery(qo))
                .flatMap(querying -> entityOperations.update(querying, Update.update("is_delete", true),
                        entityInformation.getJavaType()))
                .then(Mono.empty());
    }

    /**
     * 从查询实体抽取内部查询信息
     *
     * @param qo 查询实体
     * @return 结果
     */
    private Optional<Query> getQuery(Qo<T> qo) {
        return buildQuery(qo).map(query -> {
            if (CollectionUtils.isNotEmpty(qo.getFields())) {
                query.columns(qo.getFields()).sort(qo.sorts());
            }
            return query;
        });
    }

    /**
     * 构建查询
     *
     * @param qo 查询实体
     * @return 结果¬
     */
    private Optional<Query> buildQuery(Qo<T> qo) {
        if (qo == null) {
            return Optional.of(queryNotDeleted());
        }
        if (null != qo.getCriteria() && !qo.getCriteria().isEmpty()) {
            return Optional.of(queryNotDeleted(qo.getCriteria()));
        } else if (null != qo.getExample()) {
            return Optional.of(withNotDeleted(exampleMapper.getMappedExample(qo.getExample())));
        } else {
            Class<T> type = qo.pojoType();
            if (null != type && !Object.class.equals(type)) {
                T pojo = CopyUtils.copyQueryProps(qo, ClassUtils.newInstance(qo.pojoType()));
                return Optional.of(withNotDeleted(exampleMapper.getMappedExample(Example.of(pojo))));
            }
        }
        return Optional.of(queryNotDeleted());
    }

    private <S extends T> S prepareForSave(S entity) {
        if (entity.getDelete() == null) {
            entity.setDelete(false);
        }
        return entity;
    }

    private Query queryNotDeleted() {
        return Query.query(notDeletedCriteria());
    }

    private Query queryNotDeleted(Criteria criteria) {
        return Query.query(notDeletedCriteria().and(criteria));
    }

    private Query withNotDeleted(Query source) {
        Criteria criteria = source.getCriteria()
                .map(notDeletedCriteria()::and)
                .orElseGet(this::notDeletedCriteria);
        Query query = Query.query(criteria);
        if (CollectionUtils.isNotEmpty(source.getColumns())) {
            query.columns(source.getColumns().toArray(new SqlIdentifier[0]));
        }
        if (source.isSorted()) {
            query.sort(source.getSort());
        }
        if (source.getOffset() > 0) {
            query.offset(source.getOffset());
        }
        if (source.isLimited()) {
            query.limit(source.getLimit());
        }
        return query;
    }

    private Criteria notDeletedCriteria() {
        return Criteria.where("is_delete").is(false);
    }
}

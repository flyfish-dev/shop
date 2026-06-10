package group.flyfish.dev.generator.management.manager.impl;

import group.flyfish.dev.bean.DbSource;
import group.flyfish.dev.common.exception.ServiceException;
import group.flyfish.dev.generator.handlers.ConnectionConsumer;
import group.flyfish.dev.generator.handlers.ConnectionFunction;
import group.flyfish.dev.generator.management.manager.ConnectionFactoryManager;
import group.flyfish.dev.generator.management.manager.DbConnectionManager;
import group.flyfish.dev.generator.management.manager.DbSourceKeyGenerator;
import group.flyfish.dev.generator.management.manager.DbSourceService;
import group.flyfish.dev.generator.management.utils.R2dbcResultUtils;
import group.flyfish.dev.generator.management.utils.R2dbcUrlUtils;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * R2DBC 连接管理器。
 *
 * @author wangyu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SimpleConnectionManager implements DbConnectionManager, DisposableBean {

    private static final Map<String, CachedConnectionFactory> CONNECTION_FACTORY_MAP = new ConcurrentHashMap<>();

    private final DbSourceKeyGenerator keyGenerator;

    private final DbSourceService sourceService;

    private final ConnectionFactoryManager connectionFactoryManager;

    @Override
    public Mono<Connection> getConnection(DbSource source) {
        return getConnectionFactory(source).flatMap(factory -> Mono.from(factory.create())
                .onErrorResume(e -> {
                    removeConnectionFactory(source);
                    return Mono.error(e);
                }));
    }

    @Override
    public Mono<Void> doWithConnection(DbSource source, ConnectionConsumer handler) {
        return queryWithConnection(source, connection -> Mono.from(handler.handle(connection)).then(Mono.empty()));
    }

    @Override
    public <T> Mono<T> queryWithConnection(DbSource source, ConnectionFunction<T> handler) {
        return Mono.usingWhen(getConnection(source),
                connection -> handler.handle(connection)
                        .onErrorMap(e -> e instanceof ServiceException ? e : new ServiceException(e)),
                connection -> Mono.from(connection.close()));
    }

    @Override
    public Mono<ConnectionFactory> getConnectionFactory(DbSource source) {
        return prepareSource(source)
                .map(safe -> {
                    CachedConnectionFactory cached = CONNECTION_FACTORY_MAP.get(safe.getKey());
                    if (cached != null && cached.matches(safe)) {
                        return cached.connectionFactory();
                    }
                    removeConnectionFactory(safe);
                    ConnectionFactory factory = connectionFactoryManager.create(safe).orElseThrow();
                    CONNECTION_FACTORY_MAP.put(safe.getKey(), new CachedConnectionFactory(safe, factory));
                    return factory;
                });
    }

    @Override
    public Mono<Boolean> validate(DbSource source) {
        return preValidate(source)
                .flatMap(this::validateInternal)
                .flatMap(success -> success ? sourceService.save(source).thenReturn(true) : Mono.just(false))
                .onErrorResume(e -> {
                    source.setError("连接发生未知异常！" + rootMessage(e));
                    log.error("验证数据源失败！", e);
                    return Mono.just(false);
                });
    }

    @Override
    public void destroy() {
        CONNECTION_FACTORY_MAP.forEach((key, source) -> connectionFactoryManager.destroy(source.connectionFactory()));
        CONNECTION_FACTORY_MAP.clear();
    }

    private Mono<Boolean> validateInternal(DbSource source) {
        return Mono.defer(() -> {
            R2dbcUrlUtils.materialize(source);
            if (!R2dbcUrlUtils.hasConnectionInfo(source)) {
                source.setError("必须填写数据源地址和数据库名！");
                return Mono.just(false);
            }
            ConnectionFactory factory = connectionFactoryManager.create(source).orElseThrow();
            return Mono.usingWhen(Mono.from(factory.create()),
                            connection -> R2dbcResultUtils.query(connection, "SELECT 1").thenReturn(true),
                            connection -> Mono.from(connection.close()))
                    .doOnNext(success -> {
                        removeConnectionFactory(source);
                        CONNECTION_FACTORY_MAP.put(source.getKey(), new CachedConnectionFactory(source, factory));
                    })
                    .onErrorResume(e -> {
                        source.setError("连接发生未知异常！" + rootMessage(e));
                        connectionFactoryManager.destroy(factory);
                        return Mono.just(false);
                    });
        });
    }

    private Mono<DbSource> prepareSource(DbSource source) {
        if (StringUtils.isNotBlank(source.getKey())) {
            return sourceService.get(source)
                    .switchIfEmpty(Mono.defer(() -> R2dbcUrlUtils.hasConnectionInfo(source)
                            ? Mono.just(source)
                            : Mono.empty()))
                    .map(R2dbcUrlUtils::materialize)
                    .filter(R2dbcUrlUtils::hasConnectionInfo)
                    .switchIfEmpty(Mono.error(() -> new ServiceException("必须填写数据源地址和数据库名！")))
                    .doOnNext(safe -> {
                        if (StringUtils.isBlank(safe.getKey())) {
                            safe.setKey(source.getKey());
                        }
                    });
        }

        if (R2dbcUrlUtils.hasConnectionInfo(source)) {
            source.setKey(keyGenerator.generate(source));
        }
        return Mono.justOrEmpty(R2dbcUrlUtils.hasConnectionInfo(source) ? source : null)
                .map(R2dbcUrlUtils::materialize)
                .filter(R2dbcUrlUtils::hasConnectionInfo)
                .switchIfEmpty(Mono.error(() -> new ServiceException("必须填写数据源地址和数据库名！")))
                .doOnNext(safe -> {
                    if (StringUtils.isBlank(safe.getKey())) {
                        safe.setKey(keyGenerator.generate(safe));
                    }
                });
    }

    private Mono<DbSource> preValidate(DbSource source) {
        Mono<DbSource> prepared;
        if (StringUtils.isNotBlank(source.getKey())) {
            prepared = sourceService.get(source).defaultIfEmpty(source).doOnNext(saved -> {
                if (!R2dbcUrlUtils.hasConnectionInfo(source)) {
                    copySavedConnection(source, saved);
                } else if (STARRED_PASSWORD.equals(source.getPassword())) {
                    source.setPassword(saved.getPassword());
                }
            }).thenReturn(source);
        } else {
            prepared = Mono.just(source);
        }
        return prepared.map(R2dbcUrlUtils::materialize).doOnNext(safe -> {
            if (StringUtils.isBlank(safe.getKey())) {
                safe.setKey(keyGenerator.generate(safe));
            }
        });
    }

    private void removeConnectionFactory(DbSource source) {
        if (source == null || StringUtils.isBlank(source.getKey())) {
            return;
        }
        CachedConnectionFactory cached = CONNECTION_FACTORY_MAP.remove(source.getKey());
        if (cached != null) {
            connectionFactoryManager.destroy(cached.connectionFactory());
        }
    }

    private String rootMessage(Throwable e) {
        Throwable root = ExceptionUtils.getRootCause(e);
        return root == null ? e.getMessage() : root.getMessage();
    }

    private void copySavedConnection(DbSource target, DbSource saved) {
        target.setName(saved.getName());
        target.setUrl(saved.getUrl());
        target.setType(saved.getType());
        target.setHost(saved.getHost());
        target.setPort(saved.getPort());
        target.setDatabaseName(saved.getDatabaseName());
        target.setParams(saved.getParams());
        target.setUsername(saved.getUsername());
        target.setPassword(saved.getPassword());
        target.setOwner(saved.getOwner());
    }

    private record CachedConnectionFactory(String key, String url, String username, String password,
                                           ConnectionFactory connectionFactory) {

        private CachedConnectionFactory(DbSource source, ConnectionFactory connectionFactory) {
            this(source.getKey(), R2dbcUrlUtils.connectionUrl(source), source.getUsername(), source.getPassword(),
                    connectionFactory);
        }

        private boolean matches(DbSource source) {
            return Objects.equals(key, source.getKey())
                    && Objects.equals(url, R2dbcUrlUtils.connectionUrl(source))
                    && Objects.equals(username, source.getUsername())
                    && Objects.equals(password, source.getPassword());
        }
    }
}

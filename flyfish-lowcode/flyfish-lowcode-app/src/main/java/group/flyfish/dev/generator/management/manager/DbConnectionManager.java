package group.flyfish.dev.generator.management.manager;

import group.flyfish.dev.generator.handlers.ConnectionConsumer;
import group.flyfish.dev.generator.handlers.ConnectionFunction;
import group.flyfish.dev.bean.DbSource;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import reactor.core.publisher.Mono;

/**
 * 数据库连接管理器
 *
 * @author wangyu
 * 支持后续扩展连接池
 */
public interface DbConnectionManager {

    // 混淆的密码
    String STARRED_PASSWORD = "********";

    /**
     * 通过数据源获取连接
     *
     * @param source 数据源
     * @return 结果
     */
    Mono<Connection> getConnection(DbSource source);

    /**
     * 基于连接做一些事情
     *
     * @param source  数据源对象
     * @param handler 处理器
     * @return 结果
     */
    Mono<Void> doWithConnection(DbSource source, ConnectionConsumer handler);

    /**
     * 基于连接做一些事情，有返回值
     *
     * @param source  数据源对象
     * @param handler 处理器
     * @return 结果
     */
    <T> Mono<T> queryWithConnection(DbSource source, ConnectionFunction<T> handler);

    /**
     * 获取数据源
     *
     * @param source 数据源信息
     * @return 结果
     */
    Mono<ConnectionFactory> getConnectionFactory(DbSource source);

    /**
     * 验证数据眼
     *
     * @param source 数据源
     * @return 结果
     */
    Mono<Boolean> validate(DbSource source);
}

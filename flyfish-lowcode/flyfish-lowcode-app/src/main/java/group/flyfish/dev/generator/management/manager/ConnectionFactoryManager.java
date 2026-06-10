package group.flyfish.dev.generator.management.manager;

import group.flyfish.dev.bean.DbSource;
import io.r2dbc.spi.ConnectionFactory;

import java.util.Optional;

/**
 * R2DBC 连接工厂管理器。
 *
 * @author wangyu
 */
public interface ConnectionFactoryManager {

    /**
     * 创建连接工厂。
     *
     * @param source 数据源元数据
     * @return 结果
     */
    Optional<ConnectionFactory> create(DbSource source);

    /**
     * 销毁连接工厂。
     *
     * @param connectionFactory 连接工厂
     */
    void destroy(ConnectionFactory connectionFactory);
}

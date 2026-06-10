package group.flyfish.dev.generator.management.manager.impl;

import group.flyfish.dev.bean.DbSource;
import group.flyfish.dev.common.exception.ServiceException;
import group.flyfish.dev.generator.management.manager.ConnectionFactoryManager;
import group.flyfish.dev.generator.management.utils.R2dbcUrlUtils;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Optional;

import static io.r2dbc.spi.ConnectionFactoryOptions.PASSWORD;
import static io.r2dbc.spi.ConnectionFactoryOptions.USER;

@Service
public class R2dbcConnectionFactoryManager implements ConnectionFactoryManager {

    @Override
    public Optional<ConnectionFactory> create(DbSource source) {
        try {
            ConnectionFactoryOptions.Builder builder = ConnectionFactoryOptions.parse(
                    R2dbcUrlUtils.connectionUrl(source)
            ).mutate();
            if (StringUtils.isNotBlank(source.getUsername())) {
                builder.option(USER, source.getUsername());
            }
            if (StringUtils.isNotBlank(source.getPassword())) {
                builder.option(PASSWORD, source.getPassword());
            }
            return Optional.of(ConnectionFactories.get(builder.build()));
        } catch (Exception e) {
            throw new ServiceException("数据源初始化失败！" + e.getMessage(), e);
        }
    }

    @Override
    public void destroy(ConnectionFactory connectionFactory) {
        // R2DBC ConnectionFactory 没有统一关闭协议；具体连接会在使用后关闭。
    }
}

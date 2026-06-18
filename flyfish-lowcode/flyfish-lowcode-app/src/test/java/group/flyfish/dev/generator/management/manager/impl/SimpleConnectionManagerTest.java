package group.flyfish.dev.generator.management.manager.impl;

import group.flyfish.dev.bean.DbSource;
import group.flyfish.dev.generator.management.manager.ConnectionFactoryManager;
import group.flyfish.dev.generator.management.manager.DbConnectionManager;
import group.flyfish.dev.generator.management.manager.DbSourceKeyGenerator;
import group.flyfish.dev.generator.management.manager.DbSourceService;
import io.r2dbc.spi.ConnectionFactory;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SimpleConnectionManagerTest {

    @Test
    void keyedRuntimeSourceUsesStoredPasswordInsteadOfMaskedInput() {
        DbSource saved = new DbSource("db-key")
                .setName("local")
                .setType("mysql")
                .setHost("localhost")
                .setPort(3306)
                .setDatabaseName("flyfish")
                .setUsername("root")
                .setPassword("real-password");
        DbSource maskedInput = new DbSource("db-key")
                .setType("mysql")
                .setHost("localhost")
                .setPort(3306)
                .setDatabaseName("flyfish")
                .setUsername("root")
                .setPassword(DbConnectionManager.STARRED_PASSWORD);
        ConnectionFactory expectedFactory = mock(ConnectionFactory.class);
        DbSourceService sourceService = mock(DbSourceService.class);
        ConnectionFactoryManager connectionFactoryManager = mock(ConnectionFactoryManager.class);
        SimpleConnectionManager manager = new SimpleConnectionManager(mock(DbSourceKeyGenerator.class),
                sourceService, connectionFactoryManager);

        when(sourceService.get(any(DbSource.class))).thenReturn(Mono.just(saved));
        when(connectionFactoryManager.create(saved)).thenReturn(Optional.of(expectedFactory));

        assertSame(expectedFactory, manager.getConnectionFactory(maskedInput).block());
        verify(connectionFactoryManager).create(saved);
    }
}

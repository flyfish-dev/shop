package group.flyfish.dev.generator.handlers;

import io.r2dbc.spi.Connection;
import reactor.core.publisher.Mono;

@FunctionalInterface
public interface ConnectionFunction<T> {

    Mono<T> handle(Connection connection);
}

package group.flyfish.dev.generator.handlers;

import io.r2dbc.spi.Connection;
import org.reactivestreams.Publisher;

@FunctionalInterface
public interface ConnectionConsumer {

    Publisher<?> handle(Connection connection);
}

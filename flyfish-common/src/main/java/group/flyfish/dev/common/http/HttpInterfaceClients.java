package group.flyfish.dev.common.http;

import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * Spring HTTP Interface Client proxy factory.
 *
 * <p>The project keeps WebClient as the reactive transport and centralizes base URLs,
 * headers, codecs and error handling in configuration classes. Business services should
 * depend on typed interfaces instead of assembling request paths and payloads by hand.</p>
 */
public final class HttpInterfaceClients {

    private HttpInterfaceClients() {
    }

    public static <T> T create(WebClient webClient, Class<T> clientType) {
        return HttpServiceProxyFactory.builderFor(WebClientAdapter.create(webClient))
                .build()
                .createClient(clientType);
    }
}

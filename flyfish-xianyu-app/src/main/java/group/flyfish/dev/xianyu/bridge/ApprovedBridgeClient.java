package group.flyfish.dev.xianyu.bridge;

import group.flyfish.dev.common.http.HttpInterfaceClients;
import group.flyfish.dev.xianyu.XianyuProperties;
import group.flyfish.dev.common.bridge.BridgeRequest;
import group.flyfish.dev.common.bridge.BridgeSignature;
import group.flyfish.dev.xianyu.store.XianyuStore;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Map;

@Component
public class ApprovedBridgeClient {
    interface Client {
        @PostExchange("/internal/xianyu/{service}/{operation}")
        Mono<JsonNode> invoke(@PathVariable String service, @PathVariable String operation, @RequestBody BridgeRequest request);
    }
    private final Client shop;
    private final Client auth;
    private final XianyuProperties properties;
    private final XianyuStore store;
    public ApprovedBridgeClient(WebClient.Builder builder, XianyuProperties properties, XianyuStore store) {
        this.properties=properties; this.store=store;
        shop=HttpInterfaceClients.create(builder.clone().baseUrl(properties.getShopBaseUrl()).build(), Client.class);
        auth=HttpInterfaceClients.create(builder.clone().baseUrl(properties.getAuthBaseUrl()).build(), Client.class);
    }
    public Mono<JsonNode> shop(String operation, Map<String, Object> body) { return send(shop,"shop",operation,body); }
    public Mono<JsonNode> auth(String operation, Map<String, Object> body) { return send(auth,"auth",operation,body); }
    private Mono<JsonNode> send(Client client, String service, String operation, Map<String, Object> body) {
        return Mono.defer(() -> {
            properties.requireInfrastructure();
            String path="/internal/xianyu/"+service+"/"+operation;
            return client.invoke(service,operation,BridgeSignature.sign(path,store.write(body),properties.getBridgeSecret()))
                    .timeout(Duration.ofSeconds(30));
        });
    }
}

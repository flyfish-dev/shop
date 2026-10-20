package group.flyfish.dev.shop.xianyu;

import group.flyfish.dev.annotations.user.CurrentUser;
import group.flyfish.dev.auth.api.user.PortalUserVo;
import group.flyfish.dev.shop.support.ShopAuthorizationUtils;
import group.flyfish.dev.common.bean.Result;
import group.flyfish.dev.common.http.HttpInterfaceClients;
import group.flyfish.dev.common.bridge.BridgeRequest;
import group.flyfish.dev.common.bridge.BridgeSignature;
import group.flyfish.dev.common.bridge.XianyuBridgeProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.time.Duration;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;

@RestController
@RequestMapping("/shops/managements/xianyu")
@org.springframework.context.annotation.ImportRuntimeHints(XianyuManagementProxyController.ProxyHints.class)
public class XianyuManagementProxyController {
    public static class ProxyHints implements org.springframework.aot.hint.RuntimeHintsRegistrar {
        @Override public void registerHints(org.springframework.aot.hint.RuntimeHints hints,ClassLoader loader) {
            hints.proxies().registerJdkProxy(Client.class,org.springframework.aop.SpringProxy.class,
                    org.springframework.aop.framework.Advised.class,org.springframework.core.DecoratingProxy.class);
        }
    }
    interface Client {
        @PostExchange("/internal/xianyu/manage/{operation}")
        Mono<JsonNode> invoke(@PathVariable String operation, @RequestBody BridgeRequest request);
    }
    private final Client client;
    private final XianyuBridgeProperties properties;
    private final JsonMapper json;
    private final String base;
    public XianyuManagementProxyController(WebClient.Builder builder, XianyuBridgeProperties properties, JsonMapper json,
                                          @Value("${flyfish.xianyu.management-base-url:}") String base) {
        this.properties=properties; this.json=json; this.base=base;
        client=HttpInterfaceClients.create(builder.clone().baseUrl(base.isBlank() ? "http://127.0.0.1:10083" : base).build(), Client.class);
    }
    @GetMapping("/{operation}")
    public Mono<Object> read(@PathVariable String operation, @CurrentUser PortalUserVo user) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        if (!Set.of("status","capabilities","sellers","listings","orders","jobs").contains(operation))
            return Mono.error(new IllegalArgumentException("管理查询不支持"));
        if ("status".equals(operation) && (!properties.isEnabled() || !properties.isDataFlowApproved() || base.isBlank()))
            return Mono.just(Result.ok(Map.of("configured",false,"salesEnabled",false,"processingEnabled",false,
                    "blockers",java.util.List.of("聚石塔接入服务和批准的数据通道尚未配置"))));
        return forward(operation,Map.of(),user).cast(Object.class);
    }
    @PostMapping("/{operation}")
    public Mono<JsonNode> write(@PathVariable String operation, @RequestBody Map<String,Object> body, @CurrentUser PortalUserVo user) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        if (!Set.of("save-capability","authorize-seller","upload-media","save-listing","listing-command","order-command","refund","retry-job").contains(operation))
            return Mono.error(new IllegalArgumentException("管理操作不支持"));
        return forward(operation,body,user);
    }
    private Mono<JsonNode> forward(String operation, Map<String,Object> body, PortalUserVo user) {
        if (!properties.isEnabled() || !properties.isDataFlowApproved() || base.isBlank())
            return Mono.error(new IllegalStateException("闲鱼渠道尚未配置"));
        Map<String,Object> payload=new LinkedHashMap<>(body); payload.put("actor",String.valueOf(user.getId()));
        String path="/internal/xianyu/manage/"+operation;
        return client.invoke(operation,BridgeSignature.sign(path,json.writeValueAsString(payload),properties.getSecret()))
                .timeout(Duration.ofSeconds(30))
                .onErrorResume(org.springframework.web.reactive.function.client.WebClientResponseException.class,
                        error -> Mono.just(json.readTree(error.getResponseBodyAsString())));
    }
}

package group.flyfish.dev.xianyu.trade;

import group.flyfish.dev.xianyu.XianyuProperties;
import group.flyfish.dev.xianyu.bridge.ApprovedBridgeClient;
import group.flyfish.dev.xianyu.store.XianyuModels.Listing;
import group.flyfish.dev.xianyu.store.XianyuStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ChannelCatalogService {
    private final XianyuStore store;
    private final XianyuProperties properties;
    private final ApprovedBridgeClient bridge;
    private final JsonMapper json;

    private Flux<Listing> selected() {
        if (properties.getMiniappSeller()==null || properties.getMiniappSeller().isBlank()) return Flux.empty();
        return store.sellerListings(properties.getMiniappSeller())
                .filter(l -> Boolean.TRUE.equals(l.payload().get("miniapp_enabled")));
    }
    private Mono<List<Listing>> mappings(String item) {
        return selected().filter(l -> item.equals(l.localItemId())).collectList()
                .filter(list -> !list.isEmpty()).switchIfEmpty(Mono.error(new IllegalArgumentException("商品未在此渠道开放")));
    }
    public Mono<Void> requireSelection(String item,Object sku) {
        return mappings(item).flatMap(list -> {
            Set<String> allowed=skuIds(list);
            String id=sku==null ? "" : String.valueOf(sku);
            if (!allowed.contains(id)) return Mono.error(new IllegalArgumentException("该规格未在此渠道开放"));
            return Mono.empty();
        });
    }
    private Set<String> skuIds(List<Listing> list) {
        Set<String> ids=new HashSet<>();
        for (Listing l:list) {
            if (l.payload().get("item_sku_list") instanceof List<?> skus && !skus.isEmpty())
                skus.forEach(s -> ids.add(String.valueOf(((Map<?,?>)s).get("outer_id"))));
            else ids.add(String.valueOf(l.payload().getOrDefault("single_sku_id","")));
        }
        return ids;
    }
    public Mono<JsonNode> detail(String id,Map<String,Object> query,String token) {
        return mappings(id).flatMap(list -> bridge.shop("request",Map.of("path","/shops/items/"+id,"method","GET","query",query,"token",token))
                .map(response -> filterDetail(response,list)));
    }
    private JsonNode filterDetail(JsonNode response,List<Listing> list) {
        if (!response.path("success").asBoolean()) return response;
        ObjectNode copy=(ObjectNode)response.deepCopy();
        ObjectNode item=(ObjectNode)copy.path("result");
        Set<String> allowed=skuIds(list);
        var skus=json.createArrayNode();
        for (JsonNode sku:item.path("skus")) if (allowed.contains(sku.path("id").asText())) skus.add(sku);
        item.set("skus",skus);
        return copy;
    }
    public Mono<JsonNode> catalog(Map<String,Object> query) {
        int page=Math.max(0,Integer.parseInt(String.valueOf(query.getOrDefault("page",0))));
        int size=Math.max(1,Math.min(100,Integer.parseInt(String.valueOf(query.getOrDefault("size",20)))));
        return selected().map(Listing::localItemId).distinct().collectList().flatMap(ids ->
                Flux.fromIterable(ids).concatMap(id -> detail(id,Map.of(),""))
                        .filter(r -> r.path("success").asBoolean() && r.path("result").path("enabled").asBoolean(true))
                        .map(r -> r.path("result"))
                        .filter(item -> (!query.containsKey("type") || String.valueOf(query.get("type")).equals(item.path("type").asText()))
                                && (!query.containsKey("groupId") || String.valueOf(query.get("groupId")).equals(item.path("groupId").asText()))
                                && (!query.containsKey("keyword") || item.path("name").asText().contains(String.valueOf(query.get("keyword")))))
                        .collectList().map(items -> {
                            var array=json.createArrayNode(); items.stream().skip((long)page*size).limit(size).forEach(array::add);
                            ObjectNode result=json.createObjectNode();result.put("success",true);result.put("code",200);result.set("result",array);
                            result.set("page",json.valueToTree(Map.of("page",page,"size",size,"total",items.size())));return (JsonNode)result;
                        }));
    }
}

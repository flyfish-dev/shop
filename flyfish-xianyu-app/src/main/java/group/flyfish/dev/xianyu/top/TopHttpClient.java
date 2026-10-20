package group.flyfish.dev.xianyu.top;

import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.PostExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

public interface TopHttpClient {
    @PostExchange(contentType = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    Mono<JsonNode> invoke(@RequestBody MultiValueMap<String, String> parameters);
    @PostExchange(contentType = MediaType.MULTIPART_FORM_DATA_VALUE)
    Mono<JsonNode> upload(@RequestBody MultiValueMap<String, org.springframework.http.HttpEntity<?>> parameters);
}

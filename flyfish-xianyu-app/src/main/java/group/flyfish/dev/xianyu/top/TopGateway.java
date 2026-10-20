package group.flyfish.dev.xianyu.top;

import group.flyfish.dev.common.http.HttpInterfaceClients;
import group.flyfish.dev.xianyu.XianyuProperties;
import group.flyfish.dev.xianyu.security.TokenCipher;
import group.flyfish.dev.xianyu.store.XianyuStore;
import group.flyfish.dev.xianyu.store.XianyuModels.Capability;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class TopGateway {
    private final XianyuProperties properties;
    private final XianyuStore store;
    private final TokenCipher cipher;
    private final TopHttpClient client;
    public TopGateway(XianyuProperties properties, XianyuStore store, TokenCipher cipher, WebClient.Builder builder) {
        this.properties = properties; this.store = store; this.cipher = cipher;
        if (!properties.getGateway().equals("https://eco.taobao.com/router/rest")) {
            throw new IllegalStateException("Only the configured official production gateway is supported");
        }
        this.client = HttpInterfaceClients.create(builder.baseUrl(properties.getGateway()).build(), TopHttpClient.class);
    }
    public Mono<Capability> require(String seller, TopOperation operation) {
        return store.seller(seller).switchIfEmpty(Mono.error(new TopFailure("SELLER_NOT_CONFIGURED",false)))
                .flatMap(s->require(seller,s.application(),operation));
    }
    public Mono<Capability> require(String seller,String application,TopOperation operation) {
        properties.requireInfrastructure();
        return store.capability(seller, operation.name(),application).filter(c -> c.granted() && c.verified() && c.expiresAt() > store.now())
                .switchIfEmpty(Mono.error(new TopFailure("CAPABILITY_UNAVAILABLE", false)));
    }
    public Mono<JsonNode> call(String seller, TopOperation operation, Map<String, Object> payload) {
        return require(seller, operation).flatMap(cap -> store.seller(seller)
                .filter(s -> s.enabled() && s.expiresAt() > store.now())
                .switchIfEmpty(Mono.error(new TopFailure("SELLER_AUTH_EXPIRED", false)))
                .flatMap(s -> send(s.application(), cipher.decrypt(s.tokenCipher()), operation, payload, cap.requestKey())));
    }
    public Mono<JsonNode> buyerCall(String seller, String application, String token, TopOperation operation, Map<String,Object> payload) {
        return require(seller,application, operation).flatMap(cap -> send(application, token, operation, payload, cap.requestKey()));
    }
    public Mono<JsonNode> authorize(String application, String code) {
        properties.requireInfrastructure();
        return send(application, null, TopOperation.AUTH_TOKEN, Map.of("code", code), null);
    }
    public Mono<String> upload(String seller, String name, byte[] bytes) {
        if (bytes.length==0 || bytes.length>5*1024*1024 || !name.matches("[a-zA-Z0-9_.-]{1,120}"))
            return Mono.error(new IllegalArgumentException("图片文件不符合限制"));
        return require(seller,TopOperation.MEDIA_UPLOAD).then(store.seller(seller))
                .filter(s -> s.enabled() && s.expiresAt()>store.now()).switchIfEmpty(Mono.error(new TopFailure("SELLER_AUTH_EXPIRED",false)))
                .flatMap(s -> {
                    var credential=properties.getApplications().get(s.application());
                    if (credential==null || credential.getAppKey()==null || credential.getAppKey().isBlank()
                            || credential.getAppSecret()==null || credential.getAppSecret().isBlank()) throw new TopFailure("APPLICATION_NOT_CONFIGURED",false);
                    Map<String,String> p=new LinkedHashMap<>();p.put("name",name);p.put("type","0");p.put("method",TopOperation.MEDIA_UPLOAD.method);
                    p.put("app_key",credential.getAppKey());p.put("session",cipher.decrypt(s.tokenCipher()));p.put("v","2.0");p.put("format","json");p.put("sign_method","md5");
                    p.put("timestamp",ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                    p.put("sign",TopSigner.sign(p,credential.getAppSecret()));
                    var form=new org.springframework.http.client.MultipartBodyBuilder();p.forEach(form::part);
                    form.part("data",new org.springframework.core.io.ByteArrayResource(bytes)).filename(name);
                    return client.upload(form.build()).timeout(Duration.ofSeconds(20)).map(raw -> {
                        String id=unwrap(TopOperation.MEDIA_UPLOAD,raw).path("data").asText();
                        if (!id.matches("[0-9]+")) throw new TopFailure("MEDIA_RESPONSE_SCHEMA",true);
                        return id;
                    });
                });
    }
    private Mono<JsonNode> send(String app, String token, TopOperation op, Map<String,Object> payload, String requestKey) {
        var credential = properties.getApplications().get(app);
        if (credential == null || credential.getAppKey() == null || credential.getAppKey().isBlank()
                || credential.getAppSecret()==null || credential.getAppSecret().isBlank())
            return Mono.error(new TopFailure("APPLICATION_NOT_CONFIGURED", false));
        Map<String, String> parameters = new LinkedHashMap<>();
        String key = requestKey == null ? op.requestKey : requestKey;
        if (key != null && !key.isBlank()) parameters.put(key, store.write(payload));
        else payload.forEach((name, value) -> parameters.put(name, value instanceof Map || value instanceof Iterable
                ? store.write(value) : String.valueOf(value)));
        parameters.put("method", op.method); parameters.put("app_key", credential.getAppKey());
        parameters.put("v", "2.0"); parameters.put("format", "json"); parameters.put("sign_method", "md5");
        parameters.put("timestamp", ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        if (token != null) parameters.put("session", token);
        parameters.put("sign", TopSigner.sign(parameters, credential.getAppSecret()));
        var form = new LinkedMultiValueMap<String,String>();
        parameters.forEach(form::add);
        return client.invoke(form).timeout(Duration.ofSeconds(20)).map(body -> unwrap(op, body))
                .onErrorMap(error -> error instanceof TopFailure ? error : new TopFailure("TOP_RESULT_UNCONFIRMED", op.mutation));
    }
    public static JsonNode unwrap(TopOperation operation, JsonNode body) {
        if (body == null) throw new TopFailure("TOP_RESULT_UNCONFIRMED",operation.mutation);
        if (body.has("error_response")) {
            int code=body.path("error_response").path("code").asInt();
            throw new TopFailure("TOP_REJECTED",operation.mutation && (code==15 || code==50));
        }
        JsonNode result = body.path(operation.method.replace('.', '_') + "_response");
        if (result.isMissingNode()) throw new TopFailure("TOP_RESPONSE_SCHEMA", operation.mutation);
        if (result.has("token_result")) return result.path("token_result");
        for (String success : new String[]{"api_success", "success", "is_success", "result_success"}) {
            if (result.has(success) && !result.path(success).asBoolean()) throw new TopFailure("TOP_BUSINESS_REJECTED", false);
        }
        if (result.has("result")) result = result.path("result");
        for (String success : new String[]{"api_success", "success", "is_success", "result_success"}) {
            if (result.has(success) && !result.path(success).asBoolean()) throw new TopFailure("TOP_BUSINESS_REJECTED", false);
        }
        return result;
    }
    public static final class TopFailure extends RuntimeException {
        public final boolean indeterminate;
        public TopFailure(String code, boolean indeterminate) { super(code); this.indeterminate = indeterminate; }
    }
}

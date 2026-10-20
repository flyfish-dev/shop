package group.flyfish.dev.xianyu.security;

import group.flyfish.dev.xianyu.XianyuProperties;
import group.flyfish.dev.xianyu.bridge.ApprovedBridgeClient;
import group.flyfish.dev.xianyu.store.XianyuModels.Identity;
import group.flyfish.dev.xianyu.store.XianyuStore;
import group.flyfish.dev.xianyu.top.TopGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class XianyuIdentityService {
    private final XianyuStore store;
    private final TopGateway top;
    private final TokenCipher cipher;
    private final XianyuProperties properties;
    private final ApprovedBridgeClient bridge;
    public static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.GeneralSecurityException e) { throw new IllegalStateException(e); }
    }
    public Mono<String> authorize(String code) {
        properties.requireInfrastructure();
        if (!properties.isMiniappEnabled() || code == null || code.isBlank() || code.length()>2048)
            return Mono.error(new IllegalArgumentException("Miniapp authorization unavailable"));
        return top.authorize("buyer",code).flatMap(raw -> {
            JsonNode token = raw.isTextual() ? tools.jackson.databind.json.JsonMapper.builder().build().readTree(raw.asText()) : raw;
            String uid=token.path("taobao_user_id").asText(token.path("user_id").asText());
            String value=token.path("access_token").asText();
            long ttl=token.path("expires_in").asLong();
            if (uid.isBlank() || value.isBlank() || ttl<=0) return Mono.error(new IllegalStateException("TOP_AUTH_SCHEMA"));
            String key=hash(uid), subject=UUID.randomUUID().toString();
            return store.database().sql("INSERT INTO xy_identity VALUES (:key,:subject,:token,'buyer',:expires,NULL,:now)")
                    .bind("key",key).bind("subject",subject).bind("token",cipher.encrypt(value))
                    .bind("expires",store.now()+ttl).bind("now",store.now()).fetch().rowsUpdated()
                    .onErrorResume(DuplicateKeyException.class,e -> Mono.just(0L))
                    .then(store.database().sql("UPDATE xy_identity SET token_cipher=:token,expires_at=:expires WHERE identity_key=:key")
                            .bind("key",key).bind("token",cipher.encrypt(value)).bind("expires",store.now()+ttl).fetch().rowsUpdated())
                    .then(store.database().sql("SELECT subject FROM xy_identity WHERE identity_key=:key").bind("key",key)
                            .map((r,m)->r.get("subject",String.class)).one())
                    .flatMap(this::issueTicket);
        });
    }
    private Mono<String> issueTicket(String subject) {
        String ticket=UUID.randomUUID().toString()+UUID.randomUUID();
        return store.database().sql("INSERT INTO xy_ticket VALUES (:hash,:subject,:expires,false)")
                .bind("hash",hash(ticket)).bind("subject",subject).bind("expires",store.now()+60)
                .fetch().rowsUpdated().thenReturn(ticket);
    }
    public Mono<Map<String,Object>> exchange(String ticket) {
        return store.database().sql("UPDATE xy_ticket SET used=true WHERE ticket_hash=:hash AND used=false AND expires_at>:now")
                .bind("hash",hash(ticket)).bind("now",store.now()).fetch().rowsUpdated().filter(n -> n==1)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Identity ticket expired or consumed")))
                .then(store.database().sql("SELECT subject FROM xy_ticket WHERE ticket_hash=:hash").bind("hash",hash(ticket))
                        .map((r,m)->r.get("subject",String.class)).one())
                .flatMap(subject -> bridge.auth("login",Map.of("subject",subject))
                        .flatMap(token -> savePortalToken(subject,token.path("token").asText()).then(createSession(subject))));
    }
    public Mono<Void> savePortalToken(String subject, String value) {
        if (value == null || value.isBlank()) return Mono.error(new IllegalStateException("AUTH_BRIDGE_SCHEMA"));
        return store.database().sql("UPDATE xy_identity SET portal_token_cipher=:token WHERE subject=:subject")
                .bind("token",cipher.encrypt(value)).bind("subject",subject).fetch().rowsUpdated().then();
    }
    private Mono<Map<String,Object>> createSession(String subject) {
        String session="xy."+UUID.randomUUID()+UUID.randomUUID();
        long expires=store.now()+3600;
        return store.database().sql("INSERT INTO xy_session VALUES (:hash,:subject,:expires)")
                .bind("hash",hash(session)).bind("subject",subject).bind("expires",expires).fetch().rowsUpdated()
                .thenReturn(Map.of("token",session,"expire",expires*1000));
    }
    public Mono<Identity> authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer xy.")) return Mono.error(new SessionExpired());
        return store.database().sql("SELECT i.* FROM xy_identity i JOIN xy_session s ON s.subject=i.subject WHERE s.session_hash=:hash AND s.expires_at>:now AND i.expires_at>:now")
                .bind("hash",hash(authorization.substring(7))).bind("now",store.now()).map((r,m) ->
                        new Identity(r.get("subject",String.class),r.get("application",String.class),r.get("token_cipher",String.class),
                                r.get("portal_token_cipher",String.class),r.get("expires_at",Long.class))).one()
                .switchIfEmpty(Mono.error(new SessionExpired()));
    }
    public Mono<Identity> identity(String subject) {
        return store.database().sql("SELECT * FROM xy_identity WHERE subject=:subject AND expires_at>:now")
                .bind("subject",subject).bind("now",store.now()).map((r,m)->new Identity(subject,r.get("application",String.class),
                        r.get("token_cipher",String.class),r.get("portal_token_cipher",String.class),r.get("expires_at",Long.class))).one();
    }
    public Mono<String> subjectForPlatformId(String uid) {
        return store.database().sql("SELECT subject FROM xy_identity WHERE identity_key=:key").bind("key",hash(uid))
                .map((r,m)->r.get("subject",String.class)).one();
    }
    public String portalToken(Identity identity) { return cipher.decrypt(identity.portalTokenCipher()); }
    public String buyerToken(Identity identity) { return cipher.decrypt(identity.tokenCipher()); }
    public Mono<Void> logout(String authorization) {
        return store.database().sql("DELETE FROM xy_session WHERE session_hash=:hash")
                .bind("hash",hash(authorization.substring(7))).fetch().rowsUpdated().then();
    }
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.UNAUTHORIZED)
    public static class SessionExpired extends RuntimeException { }
}

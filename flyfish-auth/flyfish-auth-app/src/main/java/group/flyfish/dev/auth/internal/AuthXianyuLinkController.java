package group.flyfish.dev.auth.internal;

import group.flyfish.dev.annotations.user.CurrentUser;
import group.flyfish.dev.auth.api.user.PortalUserVo;
import group.flyfish.dev.auth.api.user.UserAuthorizationUtils;
import group.flyfish.dev.common.bean.Result;
import group.flyfish.dev.common.bridge.*;
import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.user.domain.bo.PendingOAuthBinding;
import group.flyfish.dev.user.service.PortalUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

@RestController
@RequiredArgsConstructor
public class AuthXianyuLinkController {
    private final XianyuBridgeGuard guard;
    private final PortalUserService users;
    private final DatabaseClient db;
    private final JsonMapper json;
    @PostMapping("/portal/users/xianyu/link-ticket")
    public Mono<Result<Map<String,Object>>> issue(@CurrentUser PortalUserVo user) {
        UserAuthorizationUtils.requireLogin(user);
        String code=UUID.randomUUID().toString()+UUID.randomUUID();long expires=Instant.now().getEpochSecond()+600;
        return db.sql("INSERT INTO auth_xianyu_link VALUES (:hash,:user,NULL,:expires,false,NULL,false)")
                .bind("hash",hash(code)).bind("user",user.getId()).bind("expires",expires).fetch().rowsUpdated()
                .thenReturn(Result.ok(Map.of("code",code,"expiresAt",expires)));
    }
    @PostMapping("/internal/xianyu/auth/link")
    public Mono<Object> link(@RequestBody BridgeRequest request) {
        return guard.accept(request,"/internal/xianyu/auth/link").flatMap(body -> {
            var input=json.readTree(body);String code=input.path("code").asText(),subject=input.path("subject").asText();
            return db.sql("UPDATE auth_xianyu_link SET used=true,subject=:subject WHERE code_hash=:hash AND used=false AND expires_at>:now")
                    .bind("subject",subject).bind("hash",hash(code)).bind("now",Instant.now().getEpochSecond()).fetch().rowsUpdated()
                    .filter(n -> n==1).switchIfEmpty(Mono.error(new BusinessException("LINK_EXPIRED","关联码已使用或已过期")))
                    .then(db.sql("SELECT target_user_id FROM auth_xianyu_link WHERE code_hash=:hash").bind("hash",hash(code))
                            .map((r,m)->r.get("target_user_id",Long.class)).one())
                    .flatMap(userId -> users.prepareBindXianyuSubject(subject,userId,code)).flatMap(plan -> {
                        if (!plan.requiresConfirmation()) return Mono.just(json.valueToTree(plan.token())).cast(Object.class);
                        return db.sql("UPDATE auth_xianyu_link SET pending=:pending WHERE code_hash=:hash")
                                .bind("pending",json.writeValueAsString(plan.pending())).bind("hash",hash(code)).fetch().rowsUpdated()
                                .thenReturn(Map.of("requiresConfirmation",true,"confirmation",plan.confirmation())).cast(Object.class);
                    });
        });
    }
    @PostMapping("/internal/xianyu/auth/link-confirm")
    public Mono<Object> confirm(@RequestBody BridgeRequest request) {
        return guard.accept(request,"/internal/xianyu/auth/link-confirm").flatMap(body -> {
            var input=json.readTree(body);String code=input.path("code").asText(),subject=input.path("subject").asText();
            if (!input.path("confirmed").asBoolean()) return Mono.error(new BusinessException("LINK_CONFIRM_REQUIRED","请确认账号关联"));
            return db.sql("UPDATE auth_xianyu_link SET confirmed=true WHERE code_hash=:hash AND subject=:subject AND used=true AND confirmed=false AND pending IS NOT NULL AND expires_at>:now")
                    .bind("hash",hash(code)).bind("subject",subject).bind("now",Instant.now().getEpochSecond()).fetch().rowsUpdated()
                    .filter(n -> n==1).switchIfEmpty(Mono.error(new BusinessException("LINK_EXPIRED","关联确认已过期")))
                    .then(db.sql("SELECT pending FROM auth_xianyu_link WHERE code_hash=:hash").bind("hash",hash(code)).map((r,m)->r.get("pending",String.class)).one())
                    .map(text -> json.readValue(text,PendingOAuthBinding.class)).flatMap(users::confirmBindAuthorization).cast(Object.class);
        });
    }
    private String hash(String value) {
        if (value==null || value.length()>256) throw new BusinessException("LINK_INVALID","关联码不正确");
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.GeneralSecurityException e) { throw new IllegalStateException(e); }
    }
}

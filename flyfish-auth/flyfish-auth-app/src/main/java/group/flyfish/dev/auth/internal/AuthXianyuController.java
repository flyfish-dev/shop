package group.flyfish.dev.auth.internal;

import group.flyfish.dev.common.bridge.BridgeRequest;
import group.flyfish.dev.common.bridge.XianyuBridgeGuard;
import group.flyfish.dev.user.service.PortalUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/internal/xianyu/auth")
@RequiredArgsConstructor
public class AuthXianyuController {
    private final XianyuBridgeGuard guard;
    private final PortalUserService users;
    private final JsonMapper json;
    private final group.flyfish.dev.user.service.impl.TokenProvider tokens;
    private final org.springframework.r2dbc.core.DatabaseClient database;

    @PostMapping("/login")
    @org.springframework.transaction.annotation.Transactional
    public Mono<Object> login(@RequestBody BridgeRequest request) {
        return guard.accept(request, "/internal/xianyu/auth/login")
                .flatMap(body -> {
                    String subject=json.readTree(body).path("subject").asText();
                    return database.sql("INSERT INTO auth_xianyu_subject VALUES (:subject,:now) ON DUPLICATE KEY UPDATE subject=VALUES(subject)")
                            .bind("subject",subject).bind("now",java.time.Instant.now().getEpochSecond()).fetch().rowsUpdated()
                            .then(database.sql("SELECT subject FROM auth_xianyu_subject WHERE subject=:subject FOR UPDATE")
                                    .bind("subject",subject).map((r,m)->r.get("subject",String.class)).one())
                            .flatMap(users::registerOrLoginXianyuSubject);
                })
                .cast(Object.class);
    }

    @PostMapping("/current")
    public Mono<Object> current(@RequestBody BridgeRequest request) {
        return guard.accept(request,"/internal/xianyu/auth/current")
                .flatMap(body -> tokens.parseAndValidateToken(json.readTree(body).path("token").asText()))
                .switchIfEmpty(Mono.error(new group.flyfish.dev.common.exception.BusinessException("USER_REQUIRED","请先登录")))
                .flatMap(claims -> users.getById(Long.valueOf(claims.subject())))
                .map(group.flyfish.dev.auth.api.user.PortalUserVo::withoutAuthorizationUserInfo).cast(Object.class);
    }
}

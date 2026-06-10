package group.flyfish.dev.user.repository;

import group.flyfish.dev.auth.api.user.OAuthType;
import group.flyfish.dev.user.domain.po.PortalUser;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PortalUserRepository extends R2dbcRepository<PortalUser, Long> {

    /**
     * 查询openid是否已注册
     *
     * @param openid 开放id
     * @return 结果
     */
    @Query("SELECT * FROM user_portal u INNER JOIN user_portal_oauth o ON u.id = o.user_id " +
            "WHERE o.type = :type AND o.openid = :openid")
    Mono<PortalUser> findByOpenid(OAuthType type, String openid);

    Mono<PortalUser> findByUsername(String username);

    @Query("SELECT * FROM user_portal WHERE LOWER(email) = LOWER(:email) ORDER BY id ASC")
    Flux<PortalUser> findAllByEmailIgnoreCase(String email);
}

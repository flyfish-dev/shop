package group.flyfish.dev.oauth.service;

import group.flyfish.dev.oauth.client.GiteeUserInfoClient;
import group.flyfish.dev.oauth.client.GithubUserInfoClient;
import group.flyfish.dev.oauth.client.OAuthEmail;
import group.flyfish.dev.user.domain.OAuthType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.pac4j.core.profile.UserProfile;
import org.pac4j.oauth.profile.OAuth20Profile;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Enriches OAuth profiles with provider APIs that are outside the primary profile endpoint.
 */
@Slf4j
@RequiredArgsConstructor
public class OAuthProfileEnrichmentService {

    private final GithubUserInfoClient githubUserInfoClient;

    private final GiteeUserInfoClient giteeUserInfoClient;

    public Mono<Map<String, Object>> enrich(UserProfile profile, OAuthType type) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        if (profile != null && profile.getAttributes() != null) {
            attributes.putAll(profile.getAttributes());
        }
        if (!(profile instanceof OAuth20Profile oauthProfile)) {
            return Mono.just(attributes);
        }
        String accessToken = StringUtils.trimToNull(oauthProfile.getAccessToken());
        if (accessToken == null) {
            return Mono.just(attributes);
        }
        if (type == OAuthType.GITHUB) {
            return enrichGithubEmails(attributes, accessToken);
        }
        if (type == OAuthType.GITEE) {
            return enrichGiteeEmails(attributes, accessToken);
        }
        return Mono.just(attributes);
    }

    private Mono<Map<String, Object>> enrichGithubEmails(Map<String, Object> attributes, String accessToken) {
        // GitHub keeps private addresses out of GET /user. With user:email scope,
        // /user/emails returns the primary/verified email needed for customer contact.
        return githubUserInfoClient.listEmails("Bearer " + accessToken)
                .map(emails -> mergeEmails(attributes, emails, "github_user_emails"))
                .onErrorResume(e -> {
                    log.warn("GitHub 邮箱信息获取失败，保留基础资料快照：{}", e.getMessage());
                    return Mono.just(attributes);
                });
    }

    private Mono<Map<String, Object>> enrichGiteeEmails(Map<String, Object> attributes, String accessToken) {
        return giteeUserInfoClient.listEmails(accessToken)
                .map(emails -> mergeEmails(attributes, emails, "gitee_user_emails"))
                .onErrorResume(e -> {
                    log.warn("Gitee 邮箱信息获取失败，保留基础资料快照：{}", e.getMessage());
                    return Mono.just(attributes);
                });
    }

    private Map<String, Object> mergeEmails(Map<String, Object> attributes, List<OAuthEmail> emails, String rawKey) {
        if (emails == null || emails.isEmpty()) {
            return attributes;
        }
        List<Map<String, Object>> emailSnapshots = emails.stream()
                .filter(Objects::nonNull)
                .filter(OAuthEmail::usable)
                .map(this::toSnapshot)
                .toList();
        if (!emailSnapshots.isEmpty()) {
            attributes.put(rawKey, emailSnapshots);
        }
        pickPrimaryEmail(emails).ifPresent(email -> {
            attributes.put("primary_email", email.email());
            attributes.put("email_verified", email.verifiedOrConfirmed());
            attributes.putIfAbsent("email", email.email());
        });
        return attributes;
    }

    private java.util.Optional<OAuthEmail> pickPrimaryEmail(List<OAuthEmail> emails) {
        return emails.stream()
                .filter(Objects::nonNull)
                .filter(OAuthEmail::usable)
                .min(Comparator
                        .comparing((OAuthEmail email) -> !Boolean.TRUE.equals(email.primary()))
                        .thenComparing(email -> !email.verifiedOrConfirmed()));
    }

    private Map<String, Object> toSnapshot(OAuthEmail email) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("email", email.email());
        snapshot.put("primary", email.primary());
        snapshot.put("verified", email.verified());
        snapshot.put("confirmed", email.confirmed());
        snapshot.put("visibility", email.visibility());
        return snapshot;
    }
}

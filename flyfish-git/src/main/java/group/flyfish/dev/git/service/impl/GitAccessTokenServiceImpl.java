package group.flyfish.dev.git.service.impl;

import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.git.config.GiteaProperties;
import group.flyfish.dev.git.config.GithubProperties;
import group.flyfish.dev.git.domain.GitProvider;
import group.flyfish.dev.git.domain.dto.GitAccessTokenCreateDto;
import group.flyfish.dev.git.domain.dto.GitAccessTokenUpdateDto;
import group.flyfish.dev.git.domain.po.GitAccessToken;
import group.flyfish.dev.git.domain.vo.GitAccessTokenVo;
import group.flyfish.dev.git.repository.GitAccessTokenRepository;
import group.flyfish.dev.git.service.GitAccessTokenService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class GitAccessTokenServiceImpl implements GitAccessTokenService {

    private final GitAccessTokenRepository repository;
    private final GiteaProperties giteaProperties;
    private final GithubProperties githubProperties;

    @Override
    public Flux<GitAccessTokenVo> list(String provider) {
        String normalized = GitProvider.normalizeCode(provider);
        return repository.findAllByProvider(normalized).map(this::toVo);
    }

    @Override
    @Transactional
    public Mono<GitAccessTokenVo> create(GitAccessTokenCreateDto dto) {
        GitAccessToken token = new GitAccessToken();
        token.setProvider(GitProvider.normalizeCode(dto.getProvider()));
        token.setName(StringUtils.trim(dto.getName()));
        token.setDescription(StringUtils.trimToNull(dto.getDescription()));
        token.setTokenValue(StringUtils.trim(dto.getTokenValue()));
        token.setUsername(StringUtils.trimToNull(dto.getUsername()));
        token.setExpireTime(dto.getExpireTime());
        token.setEnabled(dto.getEnabled() == null || dto.getEnabled());
        token.setSort(dto.getSort() == null ? 0 : dto.getSort());
        return repository.save(token).map(this::toVo);
    }

    @Override
    @Transactional
    public Mono<GitAccessTokenVo> update(Long id, GitAccessTokenUpdateDto dto) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new BusinessException("GIT_TOKEN_NOT_FOUND", "API Token 不存在")))
                .doOnNext(token -> applyUpdate(token, dto))
                .flatMap(repository::save)
                .map(this::toVo);
    }

    @Override
    @Transactional
    public Mono<Void> delete(Long id) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new BusinessException("GIT_TOKEN_NOT_FOUND", "API Token 不存在")))
                .flatMap(token -> repository.deleteById(token.getId()));
    }

    @Override
    public Mono<String> resolveTokenValue(String provider, Long tokenId) {
        String normalized = GitProvider.normalizeCode(provider);
        Mono<GitAccessToken> tokenMono = tokenId == null
                ? repository.findFirstEnabledByProvider(normalized)
                : repository.findById(tokenId);
        return tokenMono
                .filter(this::usable)
                .map(GitAccessToken::getTokenValue)
                .switchIfEmpty(Mono.defer(() -> fallbackToken(normalized)))
                .filter(StringUtils::isNotBlank)
                .switchIfEmpty(Mono.error(new BusinessException("GIT_TOKEN_REQUIRED",
                        GitProvider.titleOf(normalized) + " API Token 未配置或已过期")));
    }

    @Override
    public Mono<String> resolveManagedTokenValue(String provider, Long tokenId) {
        if (tokenId == null) {
            return Mono.error(new BusinessException("GIT_TOKEN_REQUIRED", "请选择用于同步的 API Token"));
        }
        String normalized = GitProvider.normalizeCode(provider);
        return repository.findById(tokenId)
                .switchIfEmpty(Mono.error(new BusinessException("GIT_TOKEN_NOT_FOUND", "API Token 不存在")))
                .flatMap(token -> validateManagedToken(normalized, token));
    }

    private void applyUpdate(GitAccessToken token, GitAccessTokenUpdateDto dto) {
        if (StringUtils.isNotBlank(dto.getProvider())) {
            token.setProvider(GitProvider.normalizeCode(dto.getProvider()));
        }
        if (dto.getName() != null) {
            token.setName(StringUtils.trim(dto.getName()));
        }
        if (dto.getDescription() != null) {
            token.setDescription(StringUtils.trimToNull(dto.getDescription()));
        }
        if (StringUtils.isNotBlank(dto.getTokenValue())) {
            token.setTokenValue(StringUtils.trim(dto.getTokenValue()));
        }
        if (dto.getUsername() != null) {
            token.setUsername(StringUtils.trimToNull(dto.getUsername()));
        }
        if (dto.getExpireTime() != null) {
            token.setExpireTime(dto.getExpireTime());
        }
        if (dto.getEnabled() != null) {
            token.setEnabled(dto.getEnabled());
        }
        if (dto.getSort() != null) {
            token.setSort(dto.getSort());
        }
    }

    private Mono<String> fallbackToken(String provider) {
        if (GitProvider.GITEA.code().equals(provider)) {
            return Mono.justOrEmpty(StringUtils.trimToNull(giteaProperties.getAdminToken()));
        }
        if (GitProvider.GITHUB.code().equals(provider)) {
            return Mono.justOrEmpty(StringUtils.trimToNull(githubProperties.getAdminToken()));
        }
        return Mono.empty();
    }

    private Mono<String> validateManagedToken(String provider, GitAccessToken token) {
        if (!provider.equals(GitProvider.normalizeCode(token.getProvider()))) {
            return Mono.error(new BusinessException("GIT_TOKEN_PROVIDER_MISMATCH", "API Token 平台不匹配"));
        }
        if (!usable(token)) {
            return Mono.error(new BusinessException("GIT_TOKEN_REQUIRED",
                    GitProvider.titleOf(provider) + " API Token 未配置或已过期"));
        }
        return Mono.just(StringUtils.trim(token.getTokenValue()));
    }

    private boolean usable(GitAccessToken token) {
        return token != null
                && Boolean.TRUE.equals(token.getEnabled())
                && StringUtils.isNotBlank(token.getTokenValue())
                && (token.getExpireTime() == null || token.getExpireTime().isAfter(LocalDateTime.now()));
    }

    private GitAccessTokenVo toVo(GitAccessToken token) {
        String value = StringUtils.defaultString(token.getTokenValue());
        String tail = value.length() <= 6 ? value : value.substring(value.length() - 6);
        return GitAccessTokenVo.builder()
                .id(token.getId())
                .provider(token.getProvider())
                .providerName(GitProvider.titleOf(token.getProvider()))
                .name(token.getName())
                .description(token.getDescription())
                .username(token.getUsername())
                .tokenTail(tail)
                .tokenMasked(StringUtils.isBlank(value) ? null : "******" + tail)
                .expireTime(token.getExpireTime())
                .expired(token.getExpireTime() != null && !token.getExpireTime().isAfter(LocalDateTime.now()))
                .enabled(token.getEnabled())
                .sort(token.getSort())
                .build();
    }
}

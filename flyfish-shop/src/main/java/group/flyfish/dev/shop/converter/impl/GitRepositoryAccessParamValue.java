package group.flyfish.dev.shop.converter.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import group.flyfish.dev.git.domain.GitProvider;
import group.flyfish.dev.shop.converter.ShopItemParamValue;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Git repository access delivery parameters.
 */
@Data
public class GitRepositoryAccessParamValue implements ShopItemParamValue {

    private String provider = "gitea";

    private List<Long> repositoryIds = new ArrayList<>();

    private List<Repository> repositories = new ArrayList<>();

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String owner;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String repo;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String permission = "read";

    /**
     * 将管理端提交的标准字段整理成自动交付需要的稳定值。
     */
    public void normalize() {
        String fallbackProvider = GitProvider.normalizeCode(provider);
        String fallbackPermission = GitProvider.normalizePermission(fallbackProvider, permission);
        Map<String, Repository> normalizedRepositories = new LinkedHashMap<>();
        if (repositoryIds != null) {
            for (Long repositoryId : repositoryIds) {
                if (repositoryId != null) {
                    Repository repository = new Repository();
                    repository.setRepositoryId(repositoryId);
                    repository.setProvider(fallbackProvider);
                    repository.setPermission(fallbackPermission);
                    normalizedRepositories.put(repository.key(), repository);
                }
            }
        }
        if (repositories != null) {
            for (Repository repository : repositories) {
                Repository normalized = normalizeRepository(repository, fallbackPermission);
                if (normalized.hasRepository()) {
                    normalizedRepositories.put(normalized.key(), normalized);
                }
            }
        }
        Repository legacyRepository = normalizeRepository(new Repository(owner, repo, fallbackPermission), fallbackPermission);
        if (legacyRepository.hasRepository()) {
            normalizedRepositories.putIfAbsent(legacyRepository.key(), legacyRepository);
        }
        repositories = new ArrayList<>(normalizedRepositories.values());
        provider = repositories.stream()
                .map(Repository::getProvider)
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .orElse(fallbackProvider);
        repositoryIds = repositories.stream()
                .map(Repository::getRepositoryId)
                .filter(java.util.Objects::nonNull)
                .toList();
        owner = null;
        repo = null;
        permission = GitProvider.normalizePermission(provider, fallbackPermission);
    }

    public boolean hasRepository() {
        return repositories != null && repositories.stream().anyMatch(Repository::hasRepository);
    }

    private Repository normalizeRepository(Repository repository, String fallbackPermission) {
        if (repository == null) {
            return new Repository();
        }
        Repository normalized = new Repository();
        normalized.setRepositoryId(repository.getRepositoryId());
        normalized.setProvider(GitProvider.normalizeCode(StringUtils.defaultIfBlank(repository.getProvider(), provider)));
        normalized.setOwner(cleanSegment(repository.getOwner()));
        normalized.setRepo(cleanRepo(repository.getRepo()));
        normalized.setName(StringUtils.trimToNull(repository.getName()));
        normalized.setPermission(GitProvider.normalizePermission(normalized.getProvider(),
                StringUtils.defaultIfBlank(repository.getPermission(), fallbackPermission)));
        return normalized;
    }

    private static String cleanSegment(String value) {
        return StringUtils.trimToNull(value);
    }

    private static String cleanRepo(String value) {
        String repo = StringUtils.trimToNull(value);
        return repo == null ? null : StringUtils.removeEnd(repo, ".git");
    }

    @Data
    public static class Repository {

        private Long repositoryId;

        private String provider;

        private String owner;

        private String repo;

        private String name;

        private String permission;

        public Repository() {
        }

        public Repository(String owner, String repo, String permission) {
            this.owner = owner;
            this.repo = repo;
            this.permission = permission;
        }

        public boolean hasRepository() {
            return repositoryId != null || StringUtils.isNoneBlank(owner, repo);
        }

        public String fullName() {
            if (StringUtils.isAnyBlank(owner, repo)) {
                return StringUtils.defaultIfBlank(name, repositoryId == null ? "" : "仓库#" + repositoryId);
            }
            return owner + "/" + repo;
        }

        private String key() {
            if (repositoryId != null) {
                return "id:" + repositoryId;
            }
            return GitProvider.normalizeCode(provider) + ":" + fullName().toLowerCase(Locale.ROOT);
        }
    }
}

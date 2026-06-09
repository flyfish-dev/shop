package group.flyfish.dev.shop.git;

import org.apache.commons.lang3.StringUtils;

/**
 * 商品参数解析后的可交付仓库快照。
 */
public record ResolvedGitRepository(
        Long id,
        String provider,
        Long accessTokenId,
        String owner,
        String repo,
        String name,
        String permission,
        String url
) {

    public String fullName() {
        if (StringUtils.isNoneBlank(owner, repo)) {
            return owner + "/" + repo;
        }
        return StringUtils.defaultIfBlank(name, id == null ? "未命名仓库" : "仓库#" + id);
    }
}

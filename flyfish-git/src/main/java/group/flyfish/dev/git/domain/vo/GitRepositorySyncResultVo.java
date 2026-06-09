package group.flyfish.dev.git.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class GitRepositorySyncResultVo {

    private Integer totalCount;

    private Integer createdCount;

    private Integer updatedCount;

    private List<GitManagedRepositoryVo> repositories;
}

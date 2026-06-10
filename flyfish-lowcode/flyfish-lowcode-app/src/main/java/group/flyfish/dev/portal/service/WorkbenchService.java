package group.flyfish.dev.portal.service;

import group.flyfish.dev.portal.domain.vo.WorkbenchVo;
import group.flyfish.dev.auth.api.user.PortalUserVo;
import reactor.core.publisher.Mono;

public interface WorkbenchService {

    Mono<WorkbenchVo> getWorkbench(PortalUserVo user);
}

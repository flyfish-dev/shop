package group.flyfish.dev.portal.service;

import group.flyfish.dev.portal.domain.vo.WorkbenchVo;
import group.flyfish.dev.user.domain.vo.PortalUserVo;
import reactor.core.publisher.Mono;

public interface WorkbenchService {

    Mono<WorkbenchVo> getWorkbench(PortalUserVo user);
}

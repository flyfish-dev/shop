package group.flyfish.dev.lowcode.portal.controller;

import group.flyfish.dev.annotations.user.CurrentUser;
import group.flyfish.dev.common.bean.Result;
import group.flyfish.dev.portal.domain.vo.WorkbenchVo;
import group.flyfish.dev.portal.service.WorkbenchService;
import group.flyfish.dev.auth.api.user.PortalUserVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/portal")
@RequiredArgsConstructor
public class PortalWorkbenchController {

    private final WorkbenchService workbenchService;

    @GetMapping("workbench")
    public Mono<Result<WorkbenchVo>> getWorkbench(@CurrentUser PortalUserVo user) {
        return workbenchService.getWorkbench(user).map(Result::ok);
    }
}

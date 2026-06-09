package group.flyfish.dev.generator.management.runtime.controller;

import group.flyfish.dev.common.bean.Result;
import group.flyfish.dev.generator.management.runtime.bean.SqlRunRequest;
import group.flyfish.dev.generator.management.runtime.bean.SqlRunResult;
import group.flyfish.dev.generator.management.runtime.service.OnlineSqlRunService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("integrity/sources/{source}/online")
public class OnlineSqlRunController {

    private final OnlineSqlRunService onlineSqlRunService;

    @PostMapping("run")
    public Mono<Result<SqlRunResult>> run(@PathVariable String source, @RequestBody SqlRunRequest request) {
        return onlineSqlRunService.run(source, request).map(Result::ok);
    }
}

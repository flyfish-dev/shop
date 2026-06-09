package group.flyfish.dev.generator.management.testing.controller;

import group.flyfish.dev.common.bean.Result;
import group.flyfish.dev.generator.management.testing.bean.IntegrationTestRunRequest;
import group.flyfish.dev.generator.management.testing.bean.IntegrationTestRunResult;
import group.flyfish.dev.generator.management.testing.service.IntegrationTestRunService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("integrity/sources/{source}/integration-tests")
public class IntegrationTestController {

    private final IntegrationTestRunService integrationTestRunService;

    @PostMapping("run")
    public Mono<Result<IntegrationTestRunResult>> run(@PathVariable String source,
                                                      @RequestBody IntegrationTestRunRequest request) {
        return integrationTestRunService.run(source, request).map(Result::ok);
    }
}

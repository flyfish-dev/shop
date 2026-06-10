package group.flyfish.dev.generator.management.testing.service;

import group.flyfish.dev.generator.management.testing.bean.IntegrationTestRunRequest;
import group.flyfish.dev.generator.management.testing.bean.IntegrationTestRunResult;
import reactor.core.publisher.Mono;

public interface IntegrationTestRunService {

    Mono<IntegrationTestRunResult> run(String source, IntegrationTestRunRequest request);
}

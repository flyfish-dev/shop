package group.flyfish.dev.generator.management.testing.service.impl;

import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.generator.management.query.bean.SqlQueryQo;
import group.flyfish.dev.generator.management.query.service.SqlQueryService;
import group.flyfish.dev.generator.management.runtime.service.SqlSafetyInspector;
import group.flyfish.dev.generator.management.testing.assertion.IntegrationAssertionContext;
import group.flyfish.dev.generator.management.testing.assertion.IntegrationAssertionEngine;
import group.flyfish.dev.generator.management.testing.assertion.IntegrationAssertionResult;
import group.flyfish.dev.generator.management.testing.bean.IntegrationAssertionType;
import group.flyfish.dev.generator.management.testing.bean.IntegrationTestCaseRequest;
import group.flyfish.dev.generator.management.testing.bean.IntegrationTestCaseResult;
import group.flyfish.dev.generator.management.testing.bean.IntegrationTestRunRequest;
import group.flyfish.dev.generator.management.testing.bean.IntegrationTestRunResult;
import group.flyfish.dev.generator.management.testing.service.IntegrationTestRunService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 集成测试执行服务。
 * 二期先用只读 SQL + 断言策略覆盖核心数据链路，不在业务库中落测试状态，保持实现轻量可回滚。
 */
@Service
@RequiredArgsConstructor
public class IntegrationTestRunServiceImpl implements IntegrationTestRunService {

    private final SqlQueryService sqlQueryService;
    private final SqlSafetyInspector sqlSafetyInspector;
    private final IntegrationAssertionEngine assertionEngine;

    @Override
    public Mono<IntegrationTestRunResult> run(String source, IntegrationTestRunRequest request) {
        if (request == null || CollectionUtils.isEmpty(request.getCases())) {
            return Mono.error(new BusinessException("TEST_CASE_REQUIRED", "请至少添加一个测试用例"));
        }
        return Flux.fromIterable(request.getCases())
                .concatMap(testCase -> runCase(source, testCase))
                .collectList()
                .map(this::summarize);
    }

    private Mono<IntegrationTestCaseResult> runCase(String source, IntegrationTestCaseRequest testCase) {
        IntegrationAssertionType type = testCase.getAssertionType() == null
                ? IntegrationAssertionType.NO_ERROR
                : testCase.getAssertionType();
        long start = System.currentTimeMillis();
        return Mono.defer(() -> {
            String sql = sqlSafetyInspector.normalizeSingleStatement(testCase.getSql());
            sqlSafetyInspector.requireReadOnly(sql);

            SqlQueryQo qo = new SqlQueryQo();
            qo.setSource(source);
            qo.setSql(sql);
            return sqlQueryService.query(qo);
        })
                .map(queryResult -> {
                    IntegrationAssertionResult assertionResult = assertionEngine.assertThat(
                            new IntegrationAssertionContext(type, testCase.getExpectedValue(), queryResult));
                    IntegrationTestCaseResult result = new IntegrationTestCaseResult();
                    result.setName(StringUtils.defaultIfBlank(testCase.getName(), "未命名用例"));
                    result.setAssertionType(type);
                    result.setPassed(assertionResult.passed());
                    result.setMessage(assertionResult.message());
                    result.setDurationMs(System.currentTimeMillis() - start);
                    result.setResult(queryResult);
                    return result;
                })
                .onErrorResume(error -> Mono.just(failedCase(testCase, type, start, error)));
    }

    private IntegrationTestCaseResult failedCase(IntegrationTestCaseRequest testCase, IntegrationAssertionType type,
                                                long start, Throwable error) {
        IntegrationTestCaseResult result = new IntegrationTestCaseResult();
        result.setName(StringUtils.defaultIfBlank(testCase.getName(), "未命名用例"));
        result.setAssertionType(type);
        result.setPassed(false);
        result.setMessage(error.getMessage());
        result.setDurationMs(System.currentTimeMillis() - start);
        return result;
    }

    private IntegrationTestRunResult summarize(List<IntegrationTestCaseResult> cases) {
        int passed = (int) cases.stream().filter(item -> Boolean.TRUE.equals(item.getPassed())).count();
        IntegrationTestRunResult result = new IntegrationTestRunResult();
        result.setCases(cases);
        result.setTotal(cases.size());
        result.setPassed(passed);
        result.setFailed(cases.size() - passed);
        return result;
    }
}

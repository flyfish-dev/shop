package group.flyfish.dev.generator.management.testing.service.impl;

import group.flyfish.dev.generator.management.data.bean.TableDataRow;
import group.flyfish.dev.generator.management.query.bean.SqlQueryVo;
import group.flyfish.dev.generator.management.query.service.SqlQueryService;
import group.flyfish.dev.generator.management.runtime.service.SqlSafetyInspector;
import group.flyfish.dev.generator.management.testing.assertion.EmptyAssertionStrategy;
import group.flyfish.dev.generator.management.testing.assertion.FirstCellEqualsAssertionStrategy;
import group.flyfish.dev.generator.management.testing.assertion.HasRowsAssertionStrategy;
import group.flyfish.dev.generator.management.testing.assertion.IntegrationAssertionEngine;
import group.flyfish.dev.generator.management.testing.assertion.IntegrationAssertionStrategy;
import group.flyfish.dev.generator.management.testing.assertion.NoErrorAssertionStrategy;
import group.flyfish.dev.generator.management.testing.assertion.RowCountEqualsAssertionStrategy;
import group.flyfish.dev.generator.management.testing.bean.IntegrationAssertionType;
import group.flyfish.dev.generator.management.testing.bean.IntegrationTestCaseRequest;
import group.flyfish.dev.generator.management.testing.bean.IntegrationTestRunRequest;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IntegrationTestRunServiceImplTest {

    @Test
    void runsReadonlyCasesWithAssertionSummary() {
        IntegrationTestRunServiceImpl service = new IntegrationTestRunServiceImpl(
                queryService(),
                new SqlSafetyInspector(),
                assertionEngine()
        );
        IntegrationTestRunRequest request = new IntegrationTestRunRequest();
        request.setCases(List.of(
                testCase("首列校验", "select 1 as result", IntegrationAssertionType.FIRST_CELL_EQUALS, "1"),
                testCase("空结果校验", "select 1 as result", IntegrationAssertionType.EMPTY, null)
        ));

        StepVerifier.create(service.run("ds1", request))
                .assertNext(result -> {
                    assertEquals(2, result.getTotal());
                    assertEquals(1, result.getPassed());
                    assertEquals(1, result.getFailed());
                })
                .verifyComplete();
    }

    @Test
    void blocksMutationSqlInTestCases() {
        IntegrationTestRunServiceImpl service = new IntegrationTestRunServiceImpl(
                queryService(),
                new SqlSafetyInspector(),
                assertionEngine()
        );
        IntegrationTestRunRequest request = new IntegrationTestRunRequest();
        request.setCases(List.of(testCase("误写删除", "delete from user", IntegrationAssertionType.NO_ERROR, null)));

        StepVerifier.create(service.run("ds1", request))
                .assertNext(result -> {
                    assertEquals(1, result.getFailed());
                    assertEquals("当前入口仅允许查询SQL", result.getCases().getFirst().getMessage());
                })
                .verifyComplete();
    }

    private SqlQueryService queryService() {
        return qo -> Mono.just(queryResult());
    }

    private SqlQueryVo queryResult() {
        TableDataRow row = new TableDataRow();
        row.put("result", 1);

        SqlQueryVo result = new SqlQueryVo();
        result.setColumns(List.of("result"));
        result.setRows(List.of(row));
        result.setTotal(1);
        return result;
    }

    private IntegrationAssertionEngine assertionEngine() {
        List<IntegrationAssertionStrategy> strategies = List.of(
                new NoErrorAssertionStrategy(),
                new HasRowsAssertionStrategy(),
                new EmptyAssertionStrategy(),
                new RowCountEqualsAssertionStrategy(),
                new FirstCellEqualsAssertionStrategy()
        );
        return new IntegrationAssertionEngine(strategies);
    }

    private IntegrationTestCaseRequest testCase(String name, String sql, IntegrationAssertionType type,
                                                String expectedValue) {
        IntegrationTestCaseRequest request = new IntegrationTestCaseRequest();
        request.setName(name);
        request.setSql(sql);
        request.setAssertionType(type);
        request.setExpectedValue(expectedValue);
        return request;
    }
}

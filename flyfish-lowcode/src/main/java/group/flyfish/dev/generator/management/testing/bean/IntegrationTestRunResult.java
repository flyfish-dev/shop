package group.flyfish.dev.generator.management.testing.bean;

import lombok.Data;

import java.util.List;

/**
 * 集成测试汇总结果。
 */
@Data
public class IntegrationTestRunResult {

    private Integer total;

    private Integer passed;

    private Integer failed;

    private List<IntegrationTestCaseResult> cases;
}

package group.flyfish.dev.generator.management.testing.bean;

import group.flyfish.dev.generator.management.query.bean.SqlQueryVo;
import lombok.Data;

/**
 * 单个用例执行结果。
 */
@Data
public class IntegrationTestCaseResult {

    private String name;

    private IntegrationAssertionType assertionType;

    private Boolean passed;

    private String message;

    private Long durationMs;

    private SqlQueryVo result;
}

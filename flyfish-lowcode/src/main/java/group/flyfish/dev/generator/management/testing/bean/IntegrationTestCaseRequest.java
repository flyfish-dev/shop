package group.flyfish.dev.generator.management.testing.bean;

import lombok.Data;

/**
 * 单个集成测试用例。
 */
@Data
public class IntegrationTestCaseRequest {

    private String name;

    private String sql;

    private IntegrationAssertionType assertionType;

    private String expectedValue;
}

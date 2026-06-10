package group.flyfish.dev.generator.management.testing.bean;

import lombok.Data;

import java.util.List;

/**
 * 集成测试运行请求。
 */
@Data
public class IntegrationTestRunRequest {

    private List<IntegrationTestCaseRequest> cases;
}

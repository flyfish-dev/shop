package group.flyfish.dev.generator.management.testing.assertion;

import group.flyfish.dev.generator.management.testing.bean.IntegrationAssertionType;

/**
 * 集成测试断言策略。
 */
public interface IntegrationAssertionStrategy {

    IntegrationAssertionType type();

    IntegrationAssertionResult assertThat(IntegrationAssertionContext context);
}

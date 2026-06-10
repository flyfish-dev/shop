package group.flyfish.dev.generator.management.testing.assertion;

import group.flyfish.dev.generator.management.query.bean.SqlQueryVo;
import group.flyfish.dev.generator.management.testing.bean.IntegrationAssertionType;

/**
 * 断言上下文，避免断言策略直接依赖控制层请求对象。
 */
public record IntegrationAssertionContext(
        IntegrationAssertionType type,
        String expectedValue,
        SqlQueryVo result
) {
}

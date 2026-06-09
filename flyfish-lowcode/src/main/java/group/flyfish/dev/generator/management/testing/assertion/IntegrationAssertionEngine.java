package group.flyfish.dev.generator.management.testing.assertion;

import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.generator.management.testing.bean.IntegrationAssertionType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 集成测试断言引擎。
 * 使用策略映射承载断言扩展，后续新增断言时只需要增加一个 Strategy Bean。
 */
@Component
public class IntegrationAssertionEngine {

    private final Map<IntegrationAssertionType, IntegrationAssertionStrategy> strategies;

    public IntegrationAssertionEngine(List<IntegrationAssertionStrategy> strategies) {
        this.strategies = new EnumMap<>(IntegrationAssertionType.class);
        strategies.forEach(strategy -> this.strategies.put(strategy.type(), strategy));
    }

    public IntegrationAssertionResult assertThat(IntegrationAssertionContext context) {
        IntegrationAssertionStrategy strategy = strategies.get(context.type());
        if (strategy == null) {
            throw new BusinessException("ASSERTION_NOT_SUPPORTED", "断言类型暂不支持");
        }
        return strategy.assertThat(context);
    }
}

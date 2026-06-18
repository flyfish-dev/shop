package group.flyfish.dev.generator.management.testing.assertion;

import group.flyfish.dev.generator.management.testing.bean.IntegrationAssertionType;
import org.springframework.stereotype.Component;

@Component
public class EmptyAssertionStrategy implements IntegrationAssertionStrategy {

    @Override
    public IntegrationAssertionType type() {
        return IntegrationAssertionType.EMPTY;
    }

    @Override
    public IntegrationAssertionResult assertThat(IntegrationAssertionContext context) {
        int total = context.result().getTotal() == null ? 0 : context.result().getTotal();
        return total == 0
                ? IntegrationAssertionResult.passed("结果为空")
                : IntegrationAssertionResult.failed("查询结果不为空");
    }
}

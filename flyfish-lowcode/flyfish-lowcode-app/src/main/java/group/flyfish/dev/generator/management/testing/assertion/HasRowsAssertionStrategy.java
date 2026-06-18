package group.flyfish.dev.generator.management.testing.assertion;

import group.flyfish.dev.generator.management.testing.bean.IntegrationAssertionType;
import org.springframework.stereotype.Component;

@Component
public class HasRowsAssertionStrategy implements IntegrationAssertionStrategy {

    @Override
    public IntegrationAssertionType type() {
        return IntegrationAssertionType.HAS_ROWS;
    }

    @Override
    public IntegrationAssertionResult assertThat(IntegrationAssertionContext context) {
        int total = context.result().getTotal() == null ? 0 : context.result().getTotal();
        return total > 0
                ? IntegrationAssertionResult.passed("存在结果")
                : IntegrationAssertionResult.failed("没有查询到结果");
    }
}

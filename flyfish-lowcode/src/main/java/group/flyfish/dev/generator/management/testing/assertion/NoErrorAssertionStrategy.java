package group.flyfish.dev.generator.management.testing.assertion;

import group.flyfish.dev.generator.management.testing.bean.IntegrationAssertionType;
import org.springframework.stereotype.Component;

@Component
public class NoErrorAssertionStrategy implements IntegrationAssertionStrategy {

    @Override
    public IntegrationAssertionType type() {
        return IntegrationAssertionType.NO_ERROR;
    }

    @Override
    public IntegrationAssertionResult assertThat(IntegrationAssertionContext context) {
        return IntegrationAssertionResult.passed("执行成功");
    }
}

package group.flyfish.dev.generator.management.testing.assertion;

import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.generator.management.testing.bean.IntegrationAssertionType;
import org.springframework.stereotype.Component;

@Component
public class RowCountEqualsAssertionStrategy implements IntegrationAssertionStrategy {

    @Override
    public IntegrationAssertionType type() {
        return IntegrationAssertionType.ROW_COUNT_EQUALS;
    }

    @Override
    public IntegrationAssertionResult assertThat(IntegrationAssertionContext context) {
        int expected = parseExpected(context.expectedValue());
        int actual = context.result().getTotal() == null ? 0 : context.result().getTotal();
        return actual == expected
                ? IntegrationAssertionResult.passed("行数符合预期")
                : IntegrationAssertionResult.failed("期望行数 " + expected + "，实际行数 " + actual);
    }

    private int parseExpected(String expectedValue) {
        try {
            return Integer.parseInt(expectedValue);
        } catch (Exception e) {
            throw new BusinessException("ASSERTION_EXPECTED_INVALID", "期望行数必须是整数");
        }
    }
}

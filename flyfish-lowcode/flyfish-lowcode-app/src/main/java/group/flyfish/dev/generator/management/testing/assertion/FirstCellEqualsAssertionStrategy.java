package group.flyfish.dev.generator.management.testing.assertion;

import group.flyfish.dev.generator.management.data.bean.TableDataRow;
import group.flyfish.dev.generator.management.testing.bean.IntegrationAssertionType;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class FirstCellEqualsAssertionStrategy implements IntegrationAssertionStrategy {

    @Override
    public IntegrationAssertionType type() {
        return IntegrationAssertionType.FIRST_CELL_EQUALS;
    }

    @Override
    public IntegrationAssertionResult assertThat(IntegrationAssertionContext context) {
        String actual = firstCellValue(context);
        return StringUtils.equals(actual, context.expectedValue())
                ? IntegrationAssertionResult.passed("首列值符合预期")
                : IntegrationAssertionResult.failed("期望值 " + context.expectedValue() + "，实际值 " + actual);
    }

    private String firstCellValue(IntegrationAssertionContext context) {
        if (context.result().getRows() == null || context.result().getRows().isEmpty()) {
            return null;
        }
        TableDataRow firstRow = context.result().getRows().getFirst();
        return firstRow.values().stream().findFirst().map(String::valueOf).orElse(null);
    }
}

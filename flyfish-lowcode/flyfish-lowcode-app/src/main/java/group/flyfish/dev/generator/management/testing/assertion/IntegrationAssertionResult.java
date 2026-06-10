package group.flyfish.dev.generator.management.testing.assertion;

/**
 * 断言结果。
 */
public record IntegrationAssertionResult(boolean passed, String message) {

    public static IntegrationAssertionResult passed(String message) {
        return new IntegrationAssertionResult(true, message);
    }

    public static IntegrationAssertionResult failed(String message) {
        return new IntegrationAssertionResult(false, message);
    }
}

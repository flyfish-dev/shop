package group.flyfish.dev.common.exception;

/**
 * Created by lubiao on 2017/4/14.
 */
public class ValidationException extends RuntimeException {

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }

    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(Throwable cause) {
        super(cause);
    }

    public ValidationException() {
    }
}

package group.flyfish.dev.common.exception;

import java.util.function.Supplier;

/**
 * 系统全局异常封装类
 *
 * @author wangyu
 */
public class ServiceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ServiceException(String message) {
        super(message);
    }

    public ServiceException(Throwable cause) {
        super(SQLErrors.resolve(cause), cause);
    }

    public ServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public static Supplier<ServiceException> throwIt(String message, Throwable cause) {
        return () -> new ServiceException(message, cause);
    }

    public static Supplier<ServiceException> throwIt(String message) {
        return () -> new ServiceException(message);
    }
}

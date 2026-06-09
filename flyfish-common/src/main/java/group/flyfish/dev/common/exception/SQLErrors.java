package group.flyfish.dev.common.exception;

import io.r2dbc.spi.R2dbcException;

import java.util.HashMap;
import java.util.Map;

public class SQLErrors {

    private static final Map<String, String> dict = new HashMap<>();

    static {
        dict.put("HY000", "必须指定非空字段的值！");
    }

    public static String resolve(Throwable error) {
        if (error instanceof R2dbcException e) {
            return message(e);
        }
        return error.getMessage();
    }

    public static String message(R2dbcException e) {
        String message = dict.getOrDefault(e.getSqlState(), "");
        return message + "具体错误：" + e.getMessage();
    }
}

package group.flyfish.dev.utils.type;

import org.apache.commons.lang3.ClassUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.apache.commons.lang3.ClassUtils.isPrimitiveWrapper;

public class TypeUtils {

    private static final List<Class<?>> SIMPLE_TYPES = Arrays.asList(CharSequence.class, Date.class, LocalDate.class, LocalDateTime.class);

    /**
     * 是否是简单类型
     *
     * @param type 类型
     * @return 结果
     */
    public static boolean isSimple(Class<?> type) {
        return isBasicType(type) || SIMPLE_TYPES.stream().anyMatch(candidate -> ClassUtils.isAssignable(type, candidate));
    }

    public static boolean isBasicType(Class<?> clazz) {
        if (null == clazz) {
            return false;
        }
        return (clazz.isPrimitive() || isPrimitiveWrapper(clazz));
    }
}

package group.flyfish.dev.bean;

import java.beans.Introspector;
import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.util.function.Function;

/**
 * 比对专用field，支持序列化方法引用以解析属性名。
 *
 * @author wangyu
 */
@FunctionalInterface
public interface CompareField<T, R> extends Function<T, R>, Serializable {

    /**
     * 快速获取名称
     *
     * @return 序列化后的名称
     */
    default String getName() {
        return methodToProperty(lambda().getImplMethodName());
    }

    private SerializedLambda lambda() {
        try {
            Method method = getClass().getDeclaredMethod("writeReplace");
            method.setAccessible(true);
            return (SerializedLambda) method.invoke(this);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法解析方法引用名称", e);
        }
    }

    private String methodToProperty(String method) {
        if (method.startsWith("get") && method.length() > 3) {
            return Introspector.decapitalize(method.substring(3));
        }
        if (method.startsWith("is") && method.length() > 2) {
            return Introspector.decapitalize(method.substring(2));
        }
        return method;
    }
}

package group.flyfish.dev.common.config;

import org.springframework.aop.SpringProxy;
import org.springframework.aop.framework.Advised;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.core.DecoratingProxy;
import org.springframework.util.ClassUtils;

/**
 * Native Image hints 注册工具。
 *
 * <p>各业务模块只在自己的 RuntimeHints 中声明自己的类型；这里仅提供无业务感知的工具方法。</p>
 */
public final class RuntimeHintsSupport {

    private RuntimeHintsSupport() {
    }

    public static void registerHttpInterfaceProxyIfPresent(RuntimeHints hints, ClassLoader classLoader,
                                                           String clientTypeName) {
        Class<?> clientType = resolveClass(clientTypeName, classLoader);
        if (clientType != null) {
            hints.proxies().registerJdkProxy(clientType, SpringProxy.class, Advised.class, DecoratingProxy.class);
        }
    }

    public static void registerConfigurationProperties(RuntimeHints hints, ClassLoader classLoader,
                                                       String... propertyTypeNames) {
        for (String propertyTypeName : propertyTypeNames) {
            Class<?> propertyType = resolveClass(propertyTypeName, classLoader);
            if (propertyType == null) {
                continue;
            }
            hints.reflection().registerType(propertyType,
                    MemberCategory.INTROSPECT_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INTROSPECT_DECLARED_METHODS,
                    MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.INTROSPECT_PUBLIC_METHODS,
                    MemberCategory.INVOKE_PUBLIC_METHODS,
                    MemberCategory.DECLARED_FIELDS,
                    MemberCategory.ACCESS_DECLARED_FIELDS);
        }
    }

    public static void registerReflectiveTypes(RuntimeHints hints, ClassLoader classLoader, String... typeNames) {
        for (String typeName : typeNames) {
            hints.reflection().registerTypeIfPresent(classLoader, typeName,
                    MemberCategory.INTROSPECT_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INTROSPECT_PUBLIC_CONSTRUCTORS,
                    MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                    MemberCategory.INTROSPECT_DECLARED_METHODS,
                    MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.INTROSPECT_PUBLIC_METHODS,
                    MemberCategory.INVOKE_PUBLIC_METHODS,
                    MemberCategory.DECLARED_FIELDS,
                    MemberCategory.ACCESS_DECLARED_FIELDS);
        }
    }

    private static Class<?> resolveClass(String typeName, ClassLoader classLoader) {
        try {
            return ClassUtils.forName(typeName, classLoader);
        } catch (Throwable ignored) {
            return null;
        }
    }
}

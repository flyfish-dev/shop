package group.flyfish.dev.common.repository.impl;

import group.flyfish.dev.common.base.reactive.Qo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public final class CopyUtils {

    /**
     * 从查询实体拷贝基本数据
     * 只匹配名称相同的
     *
     * @param query       查询实体
     * @param destination 目标
     * @param <T>         泛型
     * @param <Q>         泛型（查询）
     * @return 结果
     */
    public static <T, Q extends Qo<T>> T copyQueryProps(Q query, T destination) {
        Map<String, PropertyDescriptor> getters = Arrays.stream(BeanUtils.getPropertyDescriptors(query.getClass()))
                .collect(Collectors.toMap(PropertyDescriptor::getName, p -> p));
        PropertyDescriptor[] descriptors = BeanUtils.getPropertyDescriptors(destination.getClass());
        for (PropertyDescriptor descriptor : descriptors) {
            // javaBean属性名
            String propertyName = descriptor.getName();
            if (!"class".equals(propertyName) && getters.containsKey(propertyName)) {
                try {
                    Object value = getters.get(propertyName).getReadMethod().invoke(query);
                    // 过滤空值，节约带宽
                    if (value != null) {
                        // javaBean属性值
                        descriptor.getWriteMethod().invoke(destination, value);
                    }
                } catch (Exception e) {
                    log.error("拷贝查询参数时发生异常！", e);
                }
            }
        }
        return destination;
    }

    /**
     * 拷贝参数
     * entity类型必须和当前类型一致，否则报错
     *
     * @param source      要拷贝的实体
     * @param destination 目标实体
     * @param <T>         泛型
     */
    public static <T, K> K copyProps(T source, K destination) {
        PropertyDescriptor[] descriptors = BeanUtils.getPropertyDescriptors(source.getClass());
        if (descriptors.length != 0) {
            // 是否可赋值
            boolean assignable = source.getClass().isAssignableFrom(destination.getClass());
            for (PropertyDescriptor descriptor : descriptors) {
                // javaBean属性名
                String propertyName = descriptor.getName();
                if (!"class".equals(propertyName)) {
                    try {
                        Object value = descriptor.getReadMethod().invoke(source);
                        // 过滤空值，节约带宽
                        if (value != null) {
                            // javaBean属性值
                            Method writeMethod = assignable ? descriptor.getWriteMethod() :
                                    getWriteMethod(destination, propertyName).orElse(null);
                            if (writeMethod != null) {
                                writeMethod.invoke(destination, value);
                            }
                        }
                    } catch (Exception e) {
                        log.error("拷贝参数时发生异常！", e);
                    }
                }
            }
        }
        return destination;
    }

    private static Optional<Method> getWriteMethod(Object target, String propertyName) {
        return Optional.ofNullable(BeanUtils.getPropertyDescriptor(target.getClass(), propertyName))
                .map(PropertyDescriptor::getWriteMethod);
    }
}

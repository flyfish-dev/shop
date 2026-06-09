package group.flyfish.dev.ddl.compare.support;

import group.flyfish.dev.bean.Comparable;
import group.flyfish.dev.bean.CompareField;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.collections4.ListUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

/**
 * 对象比较器
 * 错误的认知：高性能，利用方法引用，不需要反射就能快速判定
 * 正确的实现：使用可序列化方法引用解析属性名，避免额外 ORM 依赖
 *
 * @author wangyu
 */
@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ObjectComparator<T extends Comparable<T>> {

    // 旧对象
    private T previous;
    // 当前对象
    private T current;

    /**
     * 比较两个对象内容的异同
     *
     * @param previous 前任
     * @param current  现任
     * @param <T>      对象泛型
     * @return 比对实例
     */
    public static <T extends Comparable<T>> ObjectComparator<T> compare(T previous, T current) {
        return new ObjectComparator<>(previous, current);
    }

    /**
     * 判断某个字段是否变化
     *
     * @param method 访问器
     * @return 结果
     */
    public <V> boolean isChanged(CompareField<T, V> method) {
        V previous = method.apply(this.previous);
        V current = method.apply(this.current);
        if (Stream.of(previous, current).anyMatch(List.class::isInstance)) {
            return !ListUtils.isEqualList((Collection<?>) previous, (Collection<?>) current);
        }
        return !Objects.equals(previous, current);
    }

    /**
     * 如果改变了，则处理逻辑
     *
     * @param method  访问器
     * @param handler 处理逻辑，包括新值和旧值
     */
    public <V> void ifChange(CompareField<T, V> method, BiConsumer<V, V> handler) {
        V pv = method.apply(previous);
        V cv = method.apply(current);
        if (null != handler && !Objects.equals(pv, cv)) {
            handler.accept(pv, cv);
        }
    }

    /**
     * 比对多个方法中任意一个变化
     *
     * @param methods 方法们
     * @return 比对结果
     */
    public boolean anyChanged(CompareField<T, ?>... methods) {
        return Stream.of(methods).anyMatch(this::isChanged);
    }

    /**
     * 通过对象自身属性判断变化
     *
     * @return 结果
     */
    public boolean anyChanged() {
        List<CompareField<T, Object>> comparable = previous.comparable();
        return comparable.stream().anyMatch(this::isChanged);
    }

    /**
     * 排除个别外判断是否改变
     *
     * @param methods 方法们
     * @return 比对结果
     */
    public boolean anyChangedExcept(List<CompareField<T, ?>> methods) {
        return current.comparable().stream()
                .filter(method -> methods.stream().map(CompareField::getName).noneMatch(name -> method.getName().equals(name)))
                .anyMatch(this::isChanged);
    }

    /**
     * 排除个别外判断是否改变
     *
     * @param method 方法
     * @return 比对结果
     */
    public boolean anyChangedExcept(CompareField<T, ?> method) {
        return anyChangedExcept(Collections.singletonList(method));
    }
}

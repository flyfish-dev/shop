package group.flyfish.dev.bean;

import java.util.List;

/**
 * 可比较的对象
 * 需要实现指定方法
 *
 * @author wangyu
 */
public interface Comparable<T> {

    /**
     * 要实现比较，必须实现的方法
     *
     * @return 可比较的方法引用
     */
    List<CompareField<T, Object>> comparable();
}

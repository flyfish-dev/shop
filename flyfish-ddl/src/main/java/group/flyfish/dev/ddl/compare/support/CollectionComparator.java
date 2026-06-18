/**
 * CollectionComparator.java com.bibenet.cedp.utils.service
 * <p>
 * ****************************************************************************
 * Change Log
 * <p>
 * 1. wangyu create me at 2016年10月20日16:27 for class DESC. 2.
 * ****************************************************************************
 * Copyright (c) 2016, www.bibenet.com All Rights Reserved.O(∩_∩)O
 */
package group.flyfish.dev.ddl.compare.support;

import group.flyfish.dev.ddl.compare.bean.CompareTuple;
import org.apache.commons.collections4.CollectionUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>CollectionComparator</p>
 * 对象比较实用工具类
 * 用途：
 * 1.用来根据指定字段求出对象集合的差并补集
 * 2.用于比较对象列表，并定义比较器逐级比较
 * 3.提供各种集合封装，便于取出想要的结果集
 * 特性：
 * 1.命名空间保护，不可手动初始化
 * 2.懒加载机制，按需分配资源
 * 3.提供对象复用方法changeTarget
 * 4.提供回调机制，传入比较器求出不同集（高效率）
 * 用法：
 * 1.不带比较器的初始化工厂
 * <pre>
 * CollectionComparator<String, Project> util = CollectionComparator.newInstance(
 * originList,newList, ReflectDataUtil.getMethod(Project.class)
 * );
 * </pre>
 * 2.带比较器的初始化工厂
 * <pre>
 * CollectionComparator<String, Project> util = CollectionComparator.newInstanceWithComparator(
 * originList,newList, ReflectDataUtil.getMethod(Project.class),
 * new Comparator<Project>() { ${compare}}
 * );
 * </pre>
 * 取出变更的内容
 * <pre>util.changedKeys();</pre>
 *
 * @author wangyu
 */
public class CollectionComparator<K, T> {

    private List<T> origin;

    private List<T> copy;

    private List<Function<T, K>> keyMethod;

    private Comparator<T> comparator;

    /* 前者变为后者删除的集合 */
    private List<T> deleted;
    /* 前者变为后者增加的集合 */
    private List<T> added;
    /* 前者变为后者增加的Code集合 */
    private Set<K> addedKeys;
    /* 前者变为后者删除的Code集合 */
    private Set<K> deletedKeys;
    /* 前者转变为后者不变的集合 */
    private Set<K> remainedKeys;
    /* 前者与后者key相同，内容不同的Code集合 */
    private Set<K> changedKeys;
    /* 前者转变为后者不变和变更的Map */
    private Map<String, Object> resultMap;
    /* 原始数据key组装的map */
    private Map<K, T> originMap;
    /* 新数据key组装的map */
    private Map<K, T> newMap;
    /* 额外绑定数据 */
    private Object bind;

    private CollectionComparator() {
    }

    private CollectionComparator(List<T> origin, List<T> copy, List<Function<T, K>> keyMethods, Comparator<T> comparator) {
        this.origin = origin;
        this.copy = copy;
        this.keyMethod = keyMethods;
        this.comparator = comparator;
        handler();
    }

    //泛型多态化
    public static <E, V> CollectionComparator<E, V> newInstance(List<V> origin, List<V> copy, Function<V, E> keyMethod) {
        return newInstance(origin, copy, keyMethod, null);
    }

    public static <E, V> CollectionComparator<E, V> newInstance(List<V> origin, List<V> copy, List<Function<V, E>> keyMethods) {
        return newInstance(origin, copy, keyMethods, null);
    }

    public static <E, V> CollectionComparator<E, V> newInstance(List<V> origin, List<V> copy, Function<V, E> keyMethod, Comparator<V> comparator) {
        return new CollectionComparator<>(origin, copy, Collections.singletonList(keyMethod), comparator);
    }

    public static <E, V> CollectionComparator<E, V> newInstance(List<V> origin, List<V> copy, List<Function<V, E>> keyMethods, Comparator<V> comparator) {
        return new CollectionComparator<>(origin, copy, keyMethods, comparator);
    }

    public CollectionComparator<K, T> changeTarget(List<T> origin, List<T> copy) {
        this.origin = origin;
        this.copy = copy;

        deleted = added = null;
        addedKeys = deletedKeys = remainedKeys = changedKeys = null;
        originMap = newMap = null;
        bind = resultMap = null;

        handler();
        return this;
    }

    /**
     * 前者变为后者增加的集合
     *
     * @return 结果集
     */
    public List<T> added() {
        if (added == null) {
            added = ContainUtils.listWithKeys(newMap, addedKeys());
        }
        return added;
    }

    /**
     * 前者变为后者删除的集合
     *
     * @return 结果集
     */
    public List<T> deleted() {
        if (deleted == null) {
            deleted = ContainUtils.listWithKeys(originMap, deletedKeys());
        }
        return deleted;
    }

    /**
     * 前者变为后者增加的Code集合
     *
     * @return key的集合
     */
    public Set<K> addedKeys() {
        if (addedKeys == null) {
            //新添加的
            addedKeys = new LinkedHashSet<>(newMap.keySet());
            addedKeys.removeAll(originMap.keySet());
        }
        return addedKeys;
    }

    /**
     * 前者变为后者删除的Code集合
     *
     * @return key的集合
     */
    public Set<K> deletedKeys() {
        if (deletedKeys == null) {
            //已经删除的
            deletedKeys = new LinkedHashSet<>(originMap.keySet());
            deletedKeys.removeAll(newMap.keySet());
        }
        return deletedKeys;
    }

    @SuppressWarnings("unchecked")
    public List<T> remainedOriginList() {
        return (List<T>) resultMap().computeIfAbsent("originList", k ->
                ContainUtils.listWithKeys(originMap, remainedKeys()));
    }

    @SuppressWarnings("unchecked")
    public List<T> remainedNewList() {
        return (List<T>) resultMap().computeIfAbsent("newList", k ->
                ContainUtils.listWithKeys(newMap, remainedKeys()));
    }

    @SuppressWarnings("unchecked")
    public List<T> changedOriginList() {
        return (List<T>) resultMap().computeIfAbsent("changeOriginList", k ->
                ContainUtils.listWithKeys(originMap, changedKeys()));
    }

    @SuppressWarnings("unchecked")
    public List<T> changedNewList() {
        return (List<T>) resultMap().computeIfAbsent("changeNewList", k ->
                ContainUtils.listWithKeys(newMap, changedKeys()));
    }

    public List<CompareTuple<T, T>> changed() {
        return changedKeys().stream().map(key -> CompareTuple.of(originMap.get(key), newMap.get(key)))
                .collect(Collectors.toList());
    }

    /**
     * 前者转变为后者不变的集合
     *
     * @return key的集合
     */
    public Set<K> remainedKeys() {
        if (remainedKeys == null) {
            //未增删过的对象
            remainedKeys = new LinkedHashSet<>(originMap.keySet());
            remainedKeys.addAll(newMap.keySet());
            remainedKeys.removeAll(deletedKeys());
            remainedKeys.removeAll(addedKeys());
            remainedKeys.removeAll(changedKeys());
        }
        return remainedKeys;
    }

    public Set<K> changedKeys() {
        if (changedKeys == null) {
            changedKeys = new LinkedHashSet<>();
            if (comparator != null) {
                Set<K> remainedKeys = new LinkedHashSet<>(originMap.keySet());
                remainedKeys.addAll(newMap.keySet());
                remainedKeys.removeAll(deletedKeys());
                remainedKeys.removeAll(addedKeys());
                for (K key : remainedKeys) {
                    if (comparator.compare(originMap.get(key), newMap.get(key)) != 0) {
                        changedKeys.add(key);
                    }
                }
            }
        }
        return changedKeys;
    }

    /**
     * 前者转变为后者不变的集合
     *
     * @return key的集合
     */
    public Map<K, T> originMap() {
        return originMap;
    }

    /**
     * 前者转变为后者不变的集合
     *
     * @return key的集合
     */
    public Map<K, T> newMap() {
        return newMap;
    }

    /**
     * 前者转变为后者不变的Map（正本）
     *
     * @return map
     */
    @SuppressWarnings("unchecked")
    public Map<K, T> remainedOriginMap() {
        return (Map<K, T>) resultMap().computeIfAbsent("origin", k ->
                ContainUtils.putAllWithKeys(new LinkedHashMap<K, T>(), remainedKeys(), originMap));
    }

    /**
     * 前者转变为后者不变的Map（副本）
     *
     * @return map
     */
    @SuppressWarnings("unchecked")
    public Map<K, T> remainedNewMap() {
        return (Map<K, T>) resultMap().computeIfAbsent("copy", k ->
                ContainUtils.putAllWithKeys(new LinkedHashMap<K, T>(), remainedKeys(), newMap));
    }

    /**
     * 前者转变为后者改变的Map（正本）
     *
     * @return map
     */
    @SuppressWarnings("unchecked")
    public Map<K, T> changedOriginMap() {
        return (Map<K, T>) resultMap().computeIfAbsent("change_origin", k ->
                ContainUtils.putAllWithKeys(new LinkedHashMap<K, T>(), changedKeys(), originMap));
    }

    /**
     * 前者转变为后者改变的Map（副本）
     *
     * @return map
     */
    @SuppressWarnings("unchecked")
    public Map<K, T> changedNewMap() {
        return (Map<K, T>) resultMap.computeIfAbsent("change_copy", k ->
                ContainUtils.putAllWithKeys(new LinkedHashMap<K, T>(), changedKeys(), newMap));
    }

    public Object getBind() {
        return bind;
    }

    public void bind(Object bind) {
        this.bind = bind;
    }

    /**
     * 两个集合内容是否相等（针对数据）
     *
     * @return 布尔
     */
    public boolean isEqual() {
        return CollectionUtils.isEmpty(addedKeys()) && CollectionUtils.isEmpty(deletedKeys()) && CollectionUtils.isEmpty(changedKeys());
    }

    private void handler() {
        originMap = new LinkedHashMap<>();
        newMap = new LinkedHashMap<>();

        if (origin != null && copy != null) {
            for (T item : origin) {
                originMap.put(getKey(item), item);
            }
            for (T item : copy) {
                newMap.put(getKey(item), item);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private K getKey(T item) {
        if (1 == keyMethod.size()) {
            return keyMethod.get(0).apply(item);
        }
        StringBuilder key = new StringBuilder();
        for (Function<T, K> method : keyMethod) {
            key.append(method.apply(item));
        }
        return (K) key.toString();
    }

    private Map<String, Object> resultMap() {
        if (resultMap == null) {
            resultMap = new LinkedHashMap<>();
        }
        return resultMap;
    }
}

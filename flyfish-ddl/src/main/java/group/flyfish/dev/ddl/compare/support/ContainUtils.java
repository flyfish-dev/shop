/**
 * ContainUtils.java com.bibenet.cedp.utils.service
 * <p>
 * ****************************************************************************
 * Change Log
 * <p>
 * 1. wangyu create me at 2016年11月25日11:56 for class DESC. 2.
 * ****************************************************************************
 * Copyright (c) 2016, www.bibenet.com All Rights Reserved.O(∩_∩)O
 */

package group.flyfish.dev.ddl.compare.support;

import java.util.*;
import java.util.function.Function;

/**
 * @author wangyu
 * @name 包含工具类
 * <p>
 * 描述：含有键值存储和包含关系的便捷查询
 * 用例：
 * 1.从一个Map里取出包含多个key的值或排除多个key的值
 * 2.将Collection实例化为List或Set
 * 3.从Map里快速去除一系列值
 * 4.从Map里快速取出需要值并将值写入新的Map
 */
class ContainUtils {

    /**
     * 通过源Map将所有key对应的值写入目标Map内
     *
     * @param map
     * @param keys
     * @param source
     * @param <E>
     * @param <V>
     * @return 目标Map
     */
    public static <E, V> Map<E, V> putAllWithKeys(Map<E, V> map, Collection<E> keys, Map<E, V> source) {
        map.putAll(mapWithKeys(source, keys));
        return map;
    }

    /**
     * 通过function转换标识
     *
     * @param list     列表
     * @param function 方法
     * @param <T>      泛型
     * @return 结果
     */
    public static <T> List<Object> getIdentifiers(List<T> list, Function<T, Object> function) {
        List<Object> identifiers = new ArrayList<>();
        for (T data : list) {
            Object identifier = function.apply(data);
            if (null != identifier) {
                identifiers.add(identifier);
            }
        }
        return identifiers;
    }

    /**
     * 排除不同的keys剩下的值
     *
     * @param map
     * @param keys
     * @param <E>
     * @param <V>
     * @return
     */
    public static <E, V> List<V> listWithoutKeys(Map<E, V> map, Collection<E> keys) {
        Collection<E> originKeys = map.keySet();
        originKeys.removeAll(keys);
        return listWithKeys(map, originKeys);
    }

    /**
     * 排除相同的keys的集合
     *
     * @param map
     * @param keys
     * @param <E>
     * @param <V>
     * @return
     */
    public static <E, V> List<V> listWithKeys(Map<E, V> map, Collection<E> keys) {
        List<V> intersected = new ArrayList<>();
        for (E key : keys) {
            if (map.containsKey(key)) {
                intersected.add(map.get(key));
            }
        }
        return intersected;
    }


    /**
     * 排除不同的keys剩下的值
     *
     * @param map
     * @param keys
     * @param <E>
     * @param <V>
     * @return
     */
    public static <E, V> Map<E, V> mapWithoutKeys(Map<E, V> map, Collection<E> keys) {
        Collection<E> originKeys = map.keySet();
        originKeys.removeAll(keys);
        return mapWithKeys(map, originKeys);
    }

    /**
     * 排除相同的keys的集合
     *
     * @param map
     * @param keys
     * @param <E>
     * @param <V>
     * @return
     */
    public static <E, V> Map<E, V> mapWithKeys(Map<E, V> map, Collection<E> keys) {
        Map<E, V> intersectedMap = new HashMap<>();
        for (E key : keys) {
            if (map.containsKey(key)) {
                intersectedMap.put(key, map.get(key));
            }
        }
        return intersectedMap;
    }

    /**
     * 通过指定的key批量删除值
     *
     * @param map
     * @param keys
     * @param <E>
     * @param <V>
     * @return
     */
    public static <E, V> Map<E, V> removeByKeys(Map<E, V> map, Collection<E> keys) {
        for (E key : keys) {
            if (map.containsKey(key)) {
                map.remove(key);
            }
        }
        return map;
    }

    /**
     * 将key和value组成的list转化为map（zipObject）
     * 转换时不保证有序
     * !!但是如果传入的是list，则必然有序!!
     *
     * @param keys
     * @param values
     * @param <E>
     * @param <V>
     * @return
     */
    public static <E, V> Map<E, V> listsToMap(Collection<E> keys, Collection<V> values) {
        Map<E, V> map = new HashMap<>();
        if (null == keys || null == values) {
            return map;
        }
        Iterator<E> keyIterator = keys.iterator();
        Iterator<V> valueIterator = values.iterator();
        while (keyIterator.hasNext() && valueIterator.hasNext()) {
            map.put(keyIterator.next(), valueIterator.next());
        }
        return map;
    }

}

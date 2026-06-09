package group.flyfish.dev.common.base.page;

import group.flyfish.dev.utils.type.CastUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 数据实体转换包装
 *
 * @param <T> 实体泛型
 */
public interface Wrapper<T> {

    /**
     * page对象的转化
     *
     * @param page 原page对象
     * @param list vo或者dto的集合
     * @param <R>  vo或者dto的泛型
     * @return 结果
     */
    default <R> Page<R> transform(Page<T> page, List<R> list) {
        return new PageImpl<>(list, page.getPageable(), page.getTotalElements());
    }

    /**
     * 转换分页对象vo
     *
     * @param page 分页对象
     * @param <R>  vo泛型
     * @return 结果
     */
    default <R> Page<R> toVo(Page<T> page) {
        return transform(page, toVo(page.getContent()));
    }

    /**
     * 转换列表vo
     *
     * @param list 原列表
     * @param <R>  vo泛型
     * @return 结果
     */
    default <R> List<R> toVo(List<T> list) {
        return list.stream().map(this::<R>toVo).collect(Collectors.toList());
    }

    /**
     * 转换列表dto
     *
     * @param list 原列表
     * @param <R>  dto泛型
     * @return 结果
     */
    default <R> List<R> toDto(List<T> list) {
        return Collections.emptyList();
    }

    /**
     * 转换列表vo
     *
     * @param data 原数据
     * @param <R>  泛型
     * @return 结果
     */
    <R> R toVo(T data);

    /**
     * 转换详情vo
     *
     * @param data 原数据
     * @param <R>  泛型
     * @return 结果
     */
    default <R> R toDetailVo(T data) {
        return CastUtils.cast(data);
    }

    /**
     * vo，dto转换po，内部需要instanceof判定
     *
     * @param data 数据
     * @param <R>  泛型
     * @return 结果
     */
    <R> T toPo(R data);
}

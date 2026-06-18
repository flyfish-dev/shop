package group.flyfish.dev.common.base.reactive;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.relational.core.query.Criteria;

import java.util.List;

/**
 * 查询模型
 *
 * @param <T> 泛型
 */
public interface Qo<T> {

    /**
     * 获取分页对象
     *
     * @return 结果
     */
    Pageable getPageable();

    /**
     * 设置分页对象
     *
     * @param pageable 分页对象
     */
    @JsonIgnore
    void setPageable(Pageable pageable);

    /**
     * 获取结果集，用于单例部署时快速封装
     *
     * @return 结果
     */
    List<T> getResult();

    /**
     * 设置结果集，用于单例部署时快速封装
     *
     * @param result 查询结果集
     */
    @JsonIgnore
    void setResult(List<T> result);

    /**
     * 获取jpa的example对象
     *
     * @return 结果
     */
    @JsonIgnore
    Example<T> getExample();

    /**
     * 获取data-mongo的对象Predicate
     *
     * @return 结果
     */
    @JsonIgnore
    Criteria getCriteria();

    /**
     * 获取查询的字段
     *
     * @return 结果
     */
    @JsonIgnore
    List<String> getFields();

    /**
     * 让值全部包含在Pojo里
     *
     * @return 结果
     */
    Class<T> pojoType();

    /**
     * 排序字段，默认createTime
     *
     * @return 结果
     */
    Sort sorts();

    /**
     * 判断查询是否为空
     *
     * @return 结果
     */
    default boolean isEmpty() {
        Criteria criteria = getCriteria();
        Example<T> example = getExample();
        return example == null && null == criteria.getValue();
    }
}

package group.flyfish.dev.common.base.reactive;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.relational.core.query.Criteria;

import java.lang.reflect.ParameterizedType;
import java.util.List;

/**
 * 基本的查询实体
 *
 * @author Mr.Wang
 */
public class BaseQo<T> implements Qo<T> {

    protected Pageable pageable;

    protected List<T> result;

    protected List<String> fields;

    public Qo<T> accept(List<T> result, Pageable pageable) {
        this.pageable = pageable;
        this.result = result;
        return this;
    }

    public Qo<T> accept(Pageable pageable) {
        this.pageable = pageable;
        return this;
    }

    @Override
    public Pageable getPageable() {
        return pageable;
    }

    @JsonIgnore
    @Override
    public void setPageable(Pageable pageable) {
        this.pageable = pageable;
    }

    @Override
    public List<T> getResult() {
        return result;
    }

    /**
     * 设置结果集，用于单例部署时快速封装
     *
     * @param result 查询结果集
     */
    @JsonIgnore
    @Override
    public void setResult(List<T> result) {
        this.result = result;
    }

    /**
     * 定义查询规则的方法
     *
     * @return 包含匹配规则的example
     */
    @JsonIgnore
    @Override
    public Example<T> getExample() {
        return null;
    }

    /**
     * 获取data-mongo的对象Criteria
     *
     * @return 结果
     */
    @Override
    public Criteria getCriteria() {
        return null;
    }

    /**
     * 获取查询的字段
     *
     * @return 结果
     */
    @Override
    public List<String> getFields() {
        return null;
    }

    /**
     * 让值全部包含在Pojo里
     *
     * @return 结果
     */
    @Override
    @SuppressWarnings("unchecked")
    public Class<T> pojoType() {
        ParameterizedType type = (ParameterizedType) this.getClass().getGenericSuperclass();
        return (Class<T>) type.getActualTypeArguments()[0];
    }


    @Override
    public Sort sorts() {
        return Sort.by(Sort.Order.desc("create_time"));
    }
}

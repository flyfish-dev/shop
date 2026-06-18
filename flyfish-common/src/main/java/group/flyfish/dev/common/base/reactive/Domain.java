package group.flyfish.dev.common.base.reactive;

/**
 * 本服务使用的公共domain
 *
 * @author wangyu
 */
public interface Domain<T> {

    /**
     * 获取主键
     *
     * @return 结果
     */
    T getId();
}

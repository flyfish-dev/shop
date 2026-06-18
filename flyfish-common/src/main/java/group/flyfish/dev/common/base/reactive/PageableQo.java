package group.flyfish.dev.common.base.reactive;

import lombok.Setter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * 可分页的查询实体
 *
 * @param <T> 泛型
 */
@Setter
public class PageableQo<T> extends BaseQo<T> {

    private Integer page = 0;

    private Integer size = 10;

    @Override
    public Pageable getPageable() {
        return PageRequest.of(page, size, sorts());
    }
}

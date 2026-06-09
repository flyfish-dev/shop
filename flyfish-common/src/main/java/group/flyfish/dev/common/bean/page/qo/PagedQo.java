package group.flyfish.dev.common.bean.page.qo;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * 分页查询实体（标准）
 *
 * @author wangyu
 */
@Data
public abstract class PagedQo<T> {

    private int page = 1;

    private int size = 10;

    private String column;

    private String order;

    private String field;

    public Pageable page() {
        return PageRequest.of(Math.max(page - 1, 0), size, sort());
    }

    public Sort sort() {
        if (StringUtils.isNotBlank(order)) {
            String property = StringUtils.defaultIfBlank(column, field);
            if (StringUtils.isBlank(property)) {
                return Sort.unsorted();
            }
            Sort.Direction direction = "asc".equalsIgnoreCase(order) ? Sort.Direction.ASC : Sort.Direction.DESC;
            return Sort.by(direction, property);
        }
        return Sort.unsorted();
    }
}

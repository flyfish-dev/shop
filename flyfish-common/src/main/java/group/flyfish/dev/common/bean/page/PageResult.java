package group.flyfish.dev.common.bean.page;

import group.flyfish.dev.common.bean.Result;
import group.flyfish.dev.common.constants.CommonConstant;
import lombok.Data;
import org.springframework.data.domain.Page;

import java.util.List;

@Data
public class PageResult<T> extends Result<List<T>> {

    private PageInfo page;

    @Data
    public static class PageInfo {

        private int page;

        private int size;

        private long total;
    }

    public static <T> Result<List<T>> ok(Page<T> page) {
        PageResult<T> result = new PageResult<>();
        result.setResult(page.getContent());
        result.setSuccess(true);
        result.setCode(CommonConstant.SC_OK_200);
        result.setMessage("成功");
        result.setPage(from(page));
        return result;
    }

    private static PageInfo from(Page<?> page) {
        PageInfo pageInfo = new PageInfo();
        pageInfo.setPage(page.getPageable().getPageNumber());
        pageInfo.setSize(page.getPageable().getPageSize());
        pageInfo.setTotal(page.getTotalElements());
        return pageInfo;
    }
}

package group.flyfish.dev.generator.management.data.bean;

import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * 与前端表格兼容的分页返回体。
 */
@Data
public class TableDataPage<T> {

    private long current;

    private long size;

    private long total;

    private long pages;

    private List<T> records = Collections.emptyList();

    public TableDataPage(long current, long size, long total, List<T> records) {
        this.current = current;
        this.size = size;
        this.total = total;
        this.records = records == null ? Collections.emptyList() : records;
        this.pages = size <= 0 ? 0 : (total + size - 1) / size;
    }
}

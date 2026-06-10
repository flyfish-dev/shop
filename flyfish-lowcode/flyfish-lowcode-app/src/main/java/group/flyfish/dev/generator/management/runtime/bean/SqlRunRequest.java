package group.flyfish.dev.generator.management.runtime.bean;

import lombok.Data;

/**
 * 在线运行请求。
 */
@Data
public class SqlRunRequest {

    /**
     * 待执行的 SQL。默认仅允许查询语句，避免在线运行误改生产数据。
     */
    private String sql;

    /**
     * 明确开启后才允许执行非查询 SQL。
     */
    private Boolean allowMutation;
}

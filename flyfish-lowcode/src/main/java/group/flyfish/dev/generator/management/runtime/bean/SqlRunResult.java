package group.flyfish.dev.generator.management.runtime.bean;

import group.flyfish.dev.generator.management.query.bean.SqlQueryVo;
import lombok.Data;

/**
 * 在线运行结果。
 */
@Data
public class SqlRunResult {

    private String status;

    private String message;

    private Long durationMs;

    private SqlQueryVo result;
}

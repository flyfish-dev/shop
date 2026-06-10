package group.flyfish.dev.generator.management.runtime.service.impl;

import group.flyfish.dev.generator.management.query.bean.SqlQueryQo;
import group.flyfish.dev.generator.management.query.service.SqlQueryService;
import group.flyfish.dev.generator.management.runtime.bean.SqlRunRequest;
import group.flyfish.dev.generator.management.runtime.bean.SqlRunResult;
import group.flyfish.dev.generator.management.runtime.service.OnlineSqlRunService;
import group.flyfish.dev.generator.management.runtime.service.SqlSafetyInspector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 在线 SQL 运行服务。
 * 当前实现复用已有数据源与 SQL 查询能力；非查询语句需要前端显式确认后才允许执行。
 */
@Service
@RequiredArgsConstructor
public class OnlineSqlRunServiceImpl implements OnlineSqlRunService {

    private final SqlQueryService sqlQueryService;
    private final SqlSafetyInspector sqlSafetyInspector;

    @Override
    public Mono<SqlRunResult> run(String source, SqlRunRequest request) {
        String sql = sqlSafetyInspector.normalizeSingleStatement(request.getSql());
        if (!Boolean.TRUE.equals(request.getAllowMutation())) {
            sqlSafetyInspector.requireReadOnly(sql);
        }
        SqlQueryQo qo = new SqlQueryQo();
        qo.setSource(source);
        qo.setSql(sql);
        long start = System.currentTimeMillis();
        return sqlQueryService.query(qo).map(queryResult -> {
            SqlRunResult result = new SqlRunResult();
            result.setStatus("SUCCESS");
            result.setMessage("执行成功");
            result.setDurationMs(System.currentTimeMillis() - start);
            result.setResult(queryResult);
            return result;
        });
    }
}

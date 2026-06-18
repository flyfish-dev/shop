package group.flyfish.dev.generator.management.query.service.impl;

import group.flyfish.dev.bean.DbSource;
import group.flyfish.dev.generator.management.manager.DbConnectionManager;
import group.flyfish.dev.generator.management.query.bean.SqlQueryQo;
import group.flyfish.dev.generator.management.query.bean.SqlQueryVo;
import group.flyfish.dev.generator.management.query.service.SqlQueryService;
import group.flyfish.dev.generator.management.runtime.service.SqlSafetyInspector;
import group.flyfish.dev.generator.management.utils.R2dbcResultUtils;
import group.flyfish.dev.generator.management.utils.R2dbcResultUtils.QueryRows;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class SqlQueryServiceImpl implements SqlQueryService {

    private final DbConnectionManager connectionManager;

    private final SqlSafetyInspector sqlSafetyInspector;

    /**
     * sql查询
     *
     * @param qo 查询实体
     * @return 结果
     */
    @Override
    public Mono<SqlQueryVo> query(SqlQueryQo qo) {
        return connectionManager.queryWithConnection(new DbSource(qo.getSource()), connection -> {
            String sql = qo.getSql();
            if (!sqlSafetyInspector.isReadOnly(sql)) {
                return R2dbcResultUtils.rowsUpdated(connection, sql).thenReturn(SqlQueryVo.executed());
            }
            return R2dbcResultUtils.query(connection, sql).map(this::buildQueryResult);
        });
    }

    private SqlQueryVo buildQueryResult(QueryRows rows) {
        SqlQueryVo vo = new SqlQueryVo();
        vo.setColumns(rows.getColumns());
        vo.setRows(rows.getRows());
        vo.setTotal(rows.getRows().size());
        return vo;
    }
}

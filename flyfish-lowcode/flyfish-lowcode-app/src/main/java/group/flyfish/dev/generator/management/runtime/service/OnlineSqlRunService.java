package group.flyfish.dev.generator.management.runtime.service;

import group.flyfish.dev.generator.management.runtime.bean.SqlRunRequest;
import group.flyfish.dev.generator.management.runtime.bean.SqlRunResult;
import reactor.core.publisher.Mono;

public interface OnlineSqlRunService {

    Mono<SqlRunResult> run(String source, SqlRunRequest request);
}

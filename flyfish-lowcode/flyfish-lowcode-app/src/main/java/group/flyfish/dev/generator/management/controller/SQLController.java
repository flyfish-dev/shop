package group.flyfish.dev.generator.management.controller;


import group.flyfish.dev.common.bean.Result;
import group.flyfish.dev.generator.management.query.bean.SqlQueryQo;
import group.flyfish.dev.generator.management.query.bean.SqlQueryVo;
import group.flyfish.dev.generator.management.query.service.SqlQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * sql控制器
 *
 * @author wangyu
 * 此处不需要防止sql注入，因为就是直接执行sql
 */
@RequestMapping("integrity/sources/{source}/sql")
@RestController
@RequiredArgsConstructor
public class SQLController {

    private final SqlQueryService queryService;

    /**
     * 执行sql语句
     *
     * @return 结果
     */
    @PostMapping
    public Mono<Result<SqlQueryVo>> doQuery(@PathVariable("source") String source, @RequestBody SqlQueryQo qo) {
        qo.setSource(source);
        return queryService.query(qo).map(Result::ok);
    }
}

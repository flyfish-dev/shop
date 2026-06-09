package group.flyfish.dev.generator.management.controller;

import group.flyfish.dev.common.bean.Result;
import group.flyfish.dev.common.exception.Assert;
import group.flyfish.dev.common.exception.ServiceException;
import group.flyfish.dev.generator.handlers.DefaultMysqlHandler;
import group.flyfish.dev.bean.DbSource;
import group.flyfish.dev.bean.DbTable;
import group.flyfish.dev.bean.DbTest;
import group.flyfish.dev.generator.management.data.DbTableDataService;
import group.flyfish.dev.generator.management.data.bean.*;
import group.flyfish.dev.generator.management.ddl.DbDdlManager;
import group.flyfish.dev.ddl.mapping.DdlGenerator;
import group.flyfish.dev.generator.management.manager.DbConnectionManager;
import group.flyfish.dev.generator.management.manager.DbSourceService;
import group.flyfish.dev.generator.management.metadata.DbMetadataService;
import group.flyfish.dev.generator.properties.GeneratorProperties;
import group.flyfish.dev.generator.utils.FileZipUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.zip.ZipOutputStream;

/**
 * 代码集成控制器
 *
 * @author wangyu
 * 作为轻量级控制器，为前端UI提供数据库管理能力和基础的存取
 */
@RestController
@RequestMapping("integrity")
@RequiredArgsConstructor
public class CodeIntegrityController {

    private final DbConnectionManager connectionManager;
    private final DbMetadataService metadataService;
    private final DbDdlManager ddlManager;
    private final DbSourceService dbSourceService;
    private final DbTableDataService tableDataService;

    /**
     * 验证并创建数据源，获得唯一key
     *
     * @param source 数据源
     * @return 结果
     */
    @PostMapping("sources")
    public Mono<Result<String>> getKey(@RequestBody DbSource source) {
        return connectionManager.validate(source)
                .map(result -> result ?
                        Result.accept(source.getKey()) :
                        Result.error("数据源验证失败！" + source.getError()));
    }

    /**
     * 查询公共数据源
     *
     * @return 结果
     */
    @GetMapping("sources")
    public Mono<Result<List<DbSource>>> listSources() {
        return dbSourceService.list("public").collect(Collectors.toList()).map(Result::ok);
    }

    /**
     * 删除数据源
     *
     * @return 结果
     */
    @DeleteMapping("sources/{key}")
    public Mono<Result<Void>> deleteSource(@PathVariable("key") String key) {
        return dbSourceService.remove(new DbSource(key)).then(Mono.just(Result.ok()));
    }

    /**
     * 查询数据库下的数据表
     *
     * @param source 数据源
     * @return 结果
     */
    @GetMapping("sources/{source}/tables")
    public Mono<Result<List<DbTable>>> getTables(@PathVariable String source) {
        return metadataService.getTables(new DbSource(source)).map(Result::ok);
    }

    /**
     * 获取某张表的详情
     *
     * @param source 数据源
     * @param table  表
     * @return 详细信息
     */
    @GetMapping("sources/{source}/tables/{table}")
    public Mono<Result<DbTable>> getTableDetail(@PathVariable String source, @PathVariable("table") String table) {
        return metadataService.getTableDetail(new DbSource(source), table).map(Result::ok);
    }

    /**
     * 获取表的ddl
     *
     * @param source 数据源
     * @param table  表
     * @return 结果
     */
    @GetMapping("sources/{source}/tables/{table}/ddl")
    public Mono<Result<String>> getTableDdl(@PathVariable String source, @PathVariable("table") String table) {
        return metadataService.getTableDetail(new DbSource(source), table)
                .map(DdlGenerator::getCreateSql)
                .map(Result::accept);
    }

    /**
     * 最终生成并持久化表
     *
     * @param source 数据源key
     * @param table  表r
     * @return 结果
     */
    @PostMapping("sources/{source}/tables/{table}")
    public Mono<Result<DbTable>> sync(@PathVariable String source, @PathVariable("table") String table, @RequestBody DbTable body) {
        // 以url参数为主
        body.setName(table);
        body.setDataSource(source);
        return ddlManager.sync(body).thenReturn(Result.ok());
    }

    /**
     * 表生成测试，使用sql语句表达
     *
     * @param source 数据源key
     * @param table  表
     * @return 结果
     */
    @PatchMapping("sources/{source}/tables/{table}")
    public Mono<Result<DbTest>> testSync(@PathVariable String source, @PathVariable("table") String table, @RequestBody DbTable body) {
        // 以url参数为主
        body.setName(table);
        body.setDataSource(source);
        return ddlManager.test(body).map(Result::ok);
    }

    /**
     * 高级查询数据
     *
     * @param qo 查询实体
     * @return 结果
     */
    @PostMapping("sources/{source}/tables/{table}/data")
    public Mono<Result<TableDataPage<Map<String, Object>>>> getDataList(@PathVariable String source, @PathVariable("table") String table,
                                                                        @RequestBody TableDataQo qo) {
        qo.setDatasource(source);
        qo.setTableName(table);
        return tableDataService.getPageList(qo).map(Result::ok);
    }

    /**
     * 高级数据编辑
     *
     * @return 结果
     */
    @PutMapping("sources/{source}/tables/{table}/data")
    public Mono<Result<Integer>> updateData(@PathVariable String source, @PathVariable("table") String table,
                                            @RequestBody Map<Integer, TableDataChange> changes) {
        TableDataUpdateDto update = new TableDataUpdateDto();
        update.setSource(source);
        update.setTable(table);
        update.setChanges(changes);
        return tableDataService.updateData(update).map(Result::ok);
    }

    /**
     * 删除选中的行
     *
     * @param source 数据源
     * @param table  表
     * @param rows   行，仅传必要字段
     * @return 结果
     */
    @DeleteMapping("sources/{source}/tables/{table}/data")
    public Mono<Result<Integer>> deleteData(@PathVariable String source, @PathVariable("table") String table,
                                            @RequestBody List<TableDataRow> rows) {
        Assert.notEmpty(rows, "要删除的行不可为空！");
        return tableDataService.deleteData(new TableDataDeleteDto(source, table, rows))
                .map(Result::ok);
    }

    /**
     * 表删除
     *
     * @param source 数据源
     * @param table  表
     * @return 结果
     */
    @DeleteMapping("sources/{source}/tables/{table}")
    public Mono<Result<Boolean>> deleteTable(@PathVariable String source, @PathVariable("table") String table) {
        DbTable dbTable = new DbTable();
        dbTable.setName(table);
        dbTable.setDataSource(source);
        return ddlManager.drop(dbTable).map(Result::ok);
    }

    /**
     * 快速复制一张表
     *
     * @param source 数据源
     * @param table  表
     * @return 结果
     */
    @PostMapping("sources/{source}/tables/{table}/duplicate")
    public Mono<Result<Boolean>> duplicateTable(@PathVariable String source, @PathVariable("table") String table) {
        // 查询详情
        return metadataService.getTableDetail(new DbSource(source), table)
                .filter(Objects::nonNull)
                .flatMap(detail -> {
                    // 修改表名
                    detail.setName(table + "_copy");
                    // 写入数据库
                    detail.setDataSource(source);
                    return ddlManager.sync(detail);
                })
                .map(Result::ok)
                .defaultIfEmpty(Result.error("要复制的表不存在！"));
    }

    /**
     * 生成代码
     *
     * @return 结果
     */
    @PostMapping("sources/{source}/codes")
    public Mono<Void> generate(@RequestBody GeneratorProperties properties, @PathVariable("source") String source,
                               ServerWebExchange exchange) {
        // 写入
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().setContentType(MediaType.APPLICATION_OCTET_STREAM);
        response.getHeaders().setContentDisposition(ContentDisposition.attachment()
                .filename("代码.zip", StandardCharsets.UTF_8)
                .build());
        return response.writeWith(
                resolveTables(source, properties)
                        .map(tables -> {
                            String id = System.currentTimeMillis() + exchange.getRequest().getId();
                            String dir = "." + File.separator + "generated" + id;
                            properties.setOutputDir(dir);
                            properties.setTables(tables);
                            return generateZip(properties);
                        })
        );
    }

    private Mono<List<DbTable>> resolveTables(String source, GeneratorProperties properties) {
        DbSource dbSource = new DbSource(source);
        if (properties.getIncludes() == null || properties.getIncludes().isEmpty()) {
            return metadataService.getTables(dbSource)
                    .flatMapMany(Flux::fromIterable)
                    .flatMap(table -> metadataService.getTableDetail(dbSource, table.getName()))
                    .collectList();
        }
        return Flux.fromIterable(properties.getIncludes())
                .flatMap(table -> metadataService.getTableDetail(dbSource, table))
                .collectList();
    }

    /**
     * 将目录下的文件进行打包并输出
     *
     * @param properties 配置信息
     * @return 结果
     */
    private DataBuffer generateZip(GeneratorProperties properties) {
        try {
            // 生成文件到指定目录
            new DefaultMysqlHandler(properties).generate();
            // 构造data buffer，将zip数据直接写入响应中
            DefaultDataBuffer dataBuffer = new DefaultDataBufferFactory().allocateBuffer();
            // 开始构建zip
            try (ZipOutputStream out = new ZipOutputStream(dataBuffer.asOutputStream())) {
                FileZipUtils.zipFiles(properties.getOutputDir(), out);
            }
            return dataBuffer;
        } catch (Exception e) {
            throw new ServiceException("压缩代码时发生了异常！" + e.getMessage(), e);
        } finally {
            FileSystemUtils.deleteRecursively(new File(properties.getOutputDir()));
        }
    }
}

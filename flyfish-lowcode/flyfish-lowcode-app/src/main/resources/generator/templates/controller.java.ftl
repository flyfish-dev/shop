package ${package.Controller};

import com.baomidou.mybatisplus.core.metadata.IPage;
import bean.common.group.flyfish.dev.Result;
import org.springframework.web.bind.annotation.RequestMapping;
import com.example.system.common.functional.log.LogOptType;
import com.example.system.common.functional.log.LogType;
import com.example.system.common.functional.log.SysOperationLog;
import exception.common.group.flyfish.dev.Assert;
import ${package.Entity}.${entity};
import ${package.Service}.${table.serviceName};
import com.example.system.common.bean.page.qo.SimpleQo;
import org.springframework.web.bind.annotation.*;
<#if !restControllerStyle>
import org.springframework.stereotype.Controller;
</#if>
<#if superControllerClassPackage??>
import ${superControllerClassPackage};
</#if>
import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * ${table.comment!} 前端控制器
 * </p>
 *
 * @author ${author}
 * @since ${date}
 */
<#if restControllerStyle>
@RestController
<#else>
@Controller
</#if>
@RequestMapping("<#if package.ModuleName?? && package.ModuleName != "">/${package.ModuleName}</#if>/<#if controllerMappingHyphenStyle>${controllerMappingHyphen}<#else>${table.entityPath}</#if>")
<#if kotlin>
class ${table.controllerName}<#if superControllerClass??> : ${superControllerClass}()</#if>
<#else>
<#if superControllerClass??>
public class ${table.controllerName} extends ${superControllerClass} {
<#else>
public class ${table.controllerName} {
</#if>
    @Resource
    private ${table.serviceName} baseService;

    /**
     * 分页查询列表
     * @param qo 查询实体
     * @return 结果包装的分页对象
     */
    @GetMapping(params = {"page", "size"})
    public Result<IPage<${entity}>> getPageList(SimpleQo<${entity}> qo) {
       return Result.ok(baseService.page(qo.page(), qo.wrapper()));
    }

    /**
     * 查询列表，不分页
     * @param qo 查询实体
     * @return 结果列表
     */
    @GetMapping(params = {"!page", "!size"})
    public Result<List<${entity}>> getList(SimpleQo<${entity}> qo) {
        return Result.ok(baseService.list(qo.wrapper()));
    }

    /**
     * 查询单条数据
     * @param id 主键
     * @return 实体结果
     */
    @GetMapping("{id}")
    public Result<${entity}> getList(@PathVariable("id") String id) {
        return Result.ok(baseService.getById(id));
    }

    /**
     * 新增实体
     * @param entity 实体信息
     * @return 响应
     */
    @PostMapping
    @SysOperationLog(type = LogType.BUSSINESS, optType = LogOptType.ADD, optName = "新增${table.comment!}")
    public Result<Void> add(@RequestBody ${entity} entity) {
        baseService.save(entity);
        return Result.ok();
    }

    /**
     * 修改实体
     * @param entity 实体信息
     * @param id 主键
     * @return 响应
     */
    @PutMapping("{id}")
    @SysOperationLog(type = LogType.BUSSINESS, optType = LogOptType.UPDATE, optName = "更新${table.comment!}")
    public Result<Void> edit(@RequestBody ${entity} entity, @PathVariable("id") String id) {
        entity.setId(id);
        baseService.updateById(entity);
        return Result.ok();
    }

    /**
     * 删除一个或多个实体
     *
     * @param ids 要删除的主键
     * @return 响应
     */
    @DeleteMapping("/{ids}")
    @SysOperationLog(type = LogType.BUSSINESS, optType = LogOptType.DELETE, optName = "删除${table.comment!}")
    public Result<Void> delete(@PathVariable("ids") List<String> ids) {
        Assert.notEmpty(ids, "请传入要删除的主键！");
        baseService.removeByIds(ids);
        return Result.ok();
    }

}
</#if>

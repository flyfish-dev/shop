import RestProxy from '@/api/core/rest-proxy';

// 生成标准rest代理
const proxy = RestProxy.of('/${controllerMappingHyphen}');

// 本模块的枚举常量
export const options = {
<#list options as option>
  ${option.name}: [
<#list option.list as item>
    { value: '${item.key}', label: '${item.value}' },
</#list>
  ],
</#list>
}

// 导出默认配置
export default proxy;

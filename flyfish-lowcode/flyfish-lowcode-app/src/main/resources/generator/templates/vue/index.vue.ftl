<template>
  <simple-table
    name="${table.comment!}"
    :search="search"
    :columns="columns"
    :operations="operations"
    :config="config"
    :form="form"
    :details="details"
    ref="table"
    row-key="id"
    title="管理${table.comment!}"
  />
</template>

<script>
import SimpleTable from '@/components/auto/SimpleTable';
import form from './form';
import details from './detail';
import proxy from './api';
<#if (options?size > 0)>
import { options } from './api';

</#if>

export default {
  name: '${entity}',
  components: { SimpleTable },
  data() {
    return {
      // CRUD接口集
      config: proxy,
      // 搜索列表
      search: [],
      // 自定义操作
      operations: [],
      // 表格列
      columns: [
        <#list columns as field>
        {
          code: '${field.key}',
          title: '${field.value}',
          <#list options! as option>
          <#if option.name == field.key>
          render: text => options.${field.key}.filter(({ value }) => value === text).map(({ label }) => label).join(','),
          </#if>
          </#list>
        },
        </#list>
        {
          code: 'createTime',
          title: '创建时间',
        },
      ],
      // 表单项
      form,
      // 表单详情项
      details,
    };
  },
};
</script>

<style scoped>
</style>

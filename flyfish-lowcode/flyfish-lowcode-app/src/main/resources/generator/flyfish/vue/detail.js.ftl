import { getUser } from '@/api/api';
<#if (options?size > 0)>
import { options } from './api';

</#if>
/**
 * 详情配置
 */
export default {
  column: 2,
  lazy: {
    createByName: async ({ createBy }) => {
      const { success, result } = await getUser(createBy);
      return success ? result.userName : createBy;
    },
    updateByName: async ({ updateBy }) => {
      const { success, result } = await getUser(updateBy);
      return success ? result.userName : updateBy;
    },
<#list forms as form>
    <#if form.component == "a-select">
    ${form.code}: async ({ ${form.code} }) =>
      options.${form.code}.filter(({ value }) => value === ${form.code}).map(({ label }) => label).join(','),
    </#if>
</#list>
  },
  form: [
    {
      code: 'id',
      title: '主键'
    },
<#list forms as form>
    {
      code: '${form.code}',
      title: '${form.title}',
      <#if form.component == "a-textarea" || form.component == "object-input">
      span: 2,
      </#if>
    },
</#list>
    {
      code: 'createTime',
      title: '创建时间',
    },
    {
      code: 'createByName',
      title: '创建人',
    },
    {
      code: 'updateTime',
      title: '上次修改时间',
    },
    {
      code: 'updateByName',
      title: '修改人',
    },
  ]
};

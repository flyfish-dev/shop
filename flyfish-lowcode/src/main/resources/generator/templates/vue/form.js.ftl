<#if (options?size > 0)>
import { options } from './api';

</#if>
// 整行布局
const fullStyles = {
  span: 24,
  grid: {
    label: {
      xs: 24,
      sm: 3,
    },
    wrapper: {
      xs: 24,
      sm: 20
    },
  },
};

export default {
  code: '${formCode}',
  name: '${table.comment!}',
  // 表单全局样式
  layout: {
    span: 12,
    label: {
      xs: 24,
      sm: 6,
    },
    wrapper: {
      xs: 24,
      sm: 16
    },
  },
  form: [
    <#list forms as form>
    {
      code: '${form.code}',
      title: '${form.title}',
      component: '${form.component}',
      props: {
        <#list form.props as prop>
        ${prop[0]}: ${prop[1]},
        </#list>
      },
      <#if form.required>
      validation: [{ required: true, message: '${form.action}${form.title}!' }],
      </#if>
      <#if form.component == "a-textarea" || form.component == "object-input">
      ...fullStyles,
      </#if>
    },
    </#list>
  ]
}

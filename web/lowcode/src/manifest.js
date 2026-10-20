export const lowcodeModules = [
  {
    name: '数据建模',
    code: 'model-design',
    desc: '模型设计',
    children: [
      {
        name: '数据源确认和维护',
        code: 'select-data-source'
      },
      {
        name: '数据表确认和维护',
        code: 'select-data-table'
      },
      {
        name: '在线模型设计',
        code: 'select-model-design'
      },
      {
        name: '模型发布保存',
        code: 'select-save-model'
      }
    ]
  },
  {
    name: '代码生成',
    code: 'code-generate',
    desc: '代码包生成'
  },
  {
    name: '在线运行',
    code: 'online-launch',
    desc: 'SQL运行'
  },
  {
    name: '集成测试',
    code: 'integrate-test',
    desc: '用例执行'
  }
];


// 精简
export const minimal = () => [
  { _id: Date.now() + 1, name: 'id', type: 'varchar', length: 36, primary: true, comment: '主键' },
  { _id: Date.now() + 2, name: 'name', type: 'varchar', length: 125, comment: '名称' },
]

// 标准字段集
export const standard = () => [
  ...minimal(),
  { _id: Date.now() + 3, name: 'create_time', type: 'datetime', comment: '创建时间' },
  { _id: Date.now() + 4, name: 'update_time', type: 'datetime', comment: '更新时间' },
  { _id: Date.now() + 5, name: 'create_by', type: 'varchar', length: 100, comment: '创建人' },
  { _id: Date.now() + 6, name: 'update_by', type: 'varchar', length: 100, comment: '更新人' },
]

// 逻辑删字段集
export const full = () => [
  ...standard(),
  { _id: Date.now() + 7, name: 'is_delete', type: 'tinyint', length: 1, comment: '逻辑删除', defaultValue: '0' },
]

export default {
  standard,
  full,
  minimal,
}

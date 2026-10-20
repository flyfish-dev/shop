/**
 * 生成sql，通过给定的数据
 * @param temp 要变更的数据集
 * @param rows 原数据行
 * @param meta 元信息
 * @return string
 */
export function generateSql(temp, rows, meta) {

}

export function renderBool({ value }) {
  switch (value) {
    case true:
    case 1: return '是';
    case false:
    case 0: return '否';
    default: return <span style="color: lightgray">NULL</span>
  }
}

export function renderDefault({ value }) {
  if (value === undefined || value === null) {
    return <span style="color: lightgray">NULL</span>
  }
  return value;
}

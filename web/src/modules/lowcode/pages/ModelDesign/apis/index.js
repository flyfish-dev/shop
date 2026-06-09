import { del, get, patch, post, put } from '@/network/request';

/**
 * 验证数据源并获取key
 * @param dataSource
 * @return 数据源key
 */
export async function validateDataSource(dataSource) {
  return post('/integrity/sources', {
    body: dataSource,
  })
}

/**
 * 查询数据源
 */
export async function listDataSource() {
  return get('/integrity/sources');
}

/**
 * 删除数据源
 * @param dataSource 数据源
 */
export async function deleteDataSource(dataSource) {
  return del(`/integrity/sources/${dataSource}`)
}

/**
 * 获取数据表
 * @param dataSource
 * @returns any 数据表
 */
export async function getTables(dataSource) {
  return get(`/integrity/sources/${dataSource}/tables`);
}

/**
 * 查询数据表详情
 * @param dataSource
 * @param tableName
 * @returns any
 */
export async function getTableDetail(dataSource, tableName) {
  return get(`/integrity/sources/${dataSource}/tables/${tableName}`);
}

/**
 * 保存表设计数据
 * @param dataSource 数据源
 * @param table 表
 * @returns any
 */
export async function saveTable(dataSource, table) {
  return post(`/integrity/sources/${dataSource}/tables/${table.name}`, {
    body: table,
  });
}

/**
 * 删除表并从数据库删除它
 * @param dataSource 数据源
 * @param tableName 表名
 */
export async function dropTable(dataSource, tableName) {
  return del(`/integrity/sources/${dataSource}/tables/${tableName}`);
}

/**
 * 从数据库复制当前表并添加_copy后缀
 * @param dataSource 数据源
 * @param tableName 表名
 */
export async function copyTable(dataSource, tableName) {
  return post(`/integrity/sources/${dataSource}/tables/${tableName}/duplicate`);
}

/**
 * 获取表ddl
 * @param dataSource 数据源
 * @param tableName 表名
 * @return {Promise<void>} 结果
 */
export async function getTableDdl(dataSource, tableName) {
  return get(`/integrity/sources/${dataSource}/tables/${tableName}/ddl`)
}

/**
 * 测试表的修改结果，生成sql和描述
 * @param dataSource 数据源key
 * @param table 表
 */
export async function testTable(dataSource, table) {
  return patch(`/integrity/sources/${dataSource}/tables/${table.name}`, {
    body: table,
  });
}

/**
 * 查询表格数据
 * @param source 数据源
 * @param table 表
 * @param params 参数
 * @return {Promise<Blob|*|undefined>} 结果
 */
export const fetchTableData = (source, table, params = {}) => {
  return post(`/integrity/sources/${source}/tables/${table}/data`, {
    body: params
  });
}

/**
 * 保存表格数据
 * @param source 数据源
 * @param table 表
 * @param data 数据
 * @return {Promise<Blob|*|undefined>} 结果
 */
export const saveTableData = (source, table, data) => {
  return put(`/integrity/sources/${source}/tables/${table}/data`, {
    body: data
  });
}

/**
 * 删除表格数据
 * @param source 数据源
 * @param table 表
 * @param data 数据行
 */
export const deleteTableData = (source, table, data) => {
  return del(`/integrity/sources/${source}/tables/${table}/data`, {
    body: data
  });
}

/**
 * 手动执行sql
 * @param source 数据源
 * @param sql 数据查询语句
 */
export const executeSql = (source, sql) => {
  return post(`/integrity/sources/${source}/sql`, { body: { sql } })
}

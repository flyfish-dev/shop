import { post } from '@/network/request';

/**
 * 生成代码
 */
export async function generate(data) {
  const { source, config } = data;
  return post(`/integrity/sources/${source}/codes`, {
    body: config,
    blob: true,
  })
}

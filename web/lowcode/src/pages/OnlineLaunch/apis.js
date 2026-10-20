import { post } from '@/network/request';

export function runOnlineSql(source, body) {
  return post(`/integrity/sources/${source}/online/run`, {
    body,
  });
}

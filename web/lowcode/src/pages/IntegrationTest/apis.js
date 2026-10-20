import { post } from '@/network/request';

export function runIntegrationTests(source, cases) {
  return post(`/integrity/sources/${source}/integration-tests/run`, {
    body: { cases },
  });
}

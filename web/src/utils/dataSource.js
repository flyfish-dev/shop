const DEFAULT_PORT = 3306;

export const normalizeParams = params => {
  const value = String(params || '').trim().replace(/^[?&]+/, '');
  if (!value) {
    return '';
  }
  return value
    .split('&')
    .map(part => part.split('='))
    .filter(([key, val]) => {
      const name = String(key || '').toLowerCase();
      const optionValue = String(val || '').toLowerCase();
      return name && !((name === 'sslmode' && optionValue === 'disabled') || (name === 'ssl' && optionValue === 'false'));
    })
    .map(parts => parts.join('='))
    .join('&');
};

export const defaultDataSource = () => ({
  type: 'mysql',
  port: DEFAULT_PORT,
  params: '',
});

export const normalizeDataSourceDraft = source => ({
  ...defaultDataSource(),
  ...source,
  params: normalizeParams(source?.params),
});

export const dataSourceAddress = source => {
  if (!source) {
    return '';
  }
  const host = source.host || '';
  if (!host) {
    return source.url || '';
  }
  const port = source.port ? `:${source.port}` : '';
  const databaseName = source.databaseName ? `/${source.databaseName}` : '';
  const params = normalizeParams(source.params);
  return `${host}${port}${databaseName}${params ? `?${params}` : ''}`;
};

export const dataSourceOptionLabel = source => `${source.name} - ${dataSourceAddress(source)}`;

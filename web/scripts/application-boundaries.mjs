export function forbiddenApplicationModule(id, target) {
  const normalized = id.replaceAll('\\', '/');
  return target === 'lowcode'
    ? /\/src\/modules\/shop\//.test(normalized)
    : /\/lowcode\/src\/|\/@surely-vue\//.test(normalized);
}

export function applicationBoundaries() {
  let target = 'shop';
  return {
    name: 'flyfish-application-boundaries',
    apply: 'build',
    configResolved(config) {
      target = config.define?.['import.meta.env.VITE_APP_TARGET'] === '"lowcode"' ? 'lowcode' : 'shop';
    },
    generateBundle() {
      const forbidden = [...this.getModuleIds()].filter(id => forbiddenApplicationModule(id, target));
      if (forbidden.length) this.error(`${target} build crosses application boundaries:\n${forbidden.join('\n')}`);
    }
  };
}

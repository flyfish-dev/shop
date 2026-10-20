import { defineConfig, mergeConfig } from 'vite';
import base from './vite.config.js';
import { fileURLToPath } from 'node:url';
export default mergeConfig(base, defineConfig({
  base: './',
  publicDir: false,
  cacheDir: 'node_modules/.vite-xianyu',
  define: { 'import.meta.env.VITE_CHANNEL': JSON.stringify('xianyu') },
  resolve: { dedupe: ['vue'], alias: [{ find: '@app-routes', replacement: fileURLToPath(new URL('./src/modules/xianyu/routes.js', import.meta.url)) }] },
  build: { outDir: 'dist-xianyu', rollupOptions: { input: 'xianyu.html' } }
}));

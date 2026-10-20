import { defineConfig, mergeConfig } from 'vite';
import { fileURLToPath } from 'node:url';
import base from '../vite.config.js';

const root = fileURLToPath(new URL('.', import.meta.url));
export default mergeConfig(base, defineConfig({
  root,
  base: './',
  publicDir: false,
  cacheDir: '../node_modules/.vite-lowcode',
  define: { 'import.meta.env.VITE_APP_TARGET': JSON.stringify('lowcode') },
  resolve: {
    dedupe: ['vue', 'pinia'],
    alias: {
      '@app-routes': fileURLToPath(new URL('./src/applicationRoutes.js', import.meta.url)),
      '@lowcode': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: { port: 9998, fs: { allow: [fileURLToPath(new URL('..', import.meta.url))] } },
  build: { outDir: 'dist', emptyOutDir: true, manifest: true }
}));

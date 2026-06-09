import { fileURLToPath, URL } from 'node:url'
import path from 'node:path'
import fs from 'node:fs'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueJsx from '@vitejs/plugin-vue-jsx'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    vue({
      template: {
        compilerOptions: {
          isCustomElement: tag => tag === 'emoji-picker'
        }
      }
    }),
    vueJsx(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
    extensions: ['.mjs', '.js', '.ts', '.jsx', '.tsx', '.json', '.vue']
  },
  css: {
    preprocessorOptions: {
      less: {
        javascriptEnabled: true,
        modifyVars: {
        },
      },
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) {
            return;
          }
          if (id.includes('/vue') || id.includes('/pinia') || id.includes('/@vueuse/')) {
            return 'vendor-vue';
          }
          if (id.includes('/ant-design-vue/')
            || id.includes('/@ant-design/')
            || id.includes('/@ctrl/tinycolor/')
            || id.includes('/async-validator/')
            || id.includes('/dayjs/')
            || id.includes('/lodash-es/')) {
            return 'vendor-ant-design';
          }
        }
      }
    }
  },
  server: {
    port: 9999,
    host: '127.0.0.1',
    proxy: {
      '/integrity': {
        target: 'http://localhost:10081',
        // target: 'http://10.211.55.6:10081',
        // target: 'http://backend.example.com/',
        ws: false,
        changeOrigin: true,
      },
      '/portal': {
        target: 'http://localhost:10081',
        // target: 'http://10.211.55.6:10081',
        // target: 'http://backend.example.com/',
        ws: true,
        changeOrigin: true,
      },
      '/shops': {
        target: 'http://localhost:10081',
        // target: 'http://10.211.55.6:10081',
        // target: 'http://backend.example.com/',
        ws: false,
        changeOrigin: true,
      },
      '/oauth': {
        target: 'http://localhost:10081',
        // target: 'http://10.211.55.6:10081',
        // target: 'http://backend.example.com/',
        ws: false,
        changeOrigin: true,
      },
      '/wx': {
        target: 'http://localhost:10081',
        // target: 'http://10.211.55.6:10081',
        // target: 'http://backend.example.com/',
        ws: false,
        changeOrigin: true,
      },
      '/email': {
        target: 'http://localhost:10081',
        // target: 'http://10.211.55.6:10081',
        // target: 'http://backend.example.com/',
        ws: false,
        changeOrigin: true,
      },
      '^/images/(?!contact/).*': {
        target: 'http://localhost:10081',
        ws: false,
        changeOrigin: true,
      }
    }
  }
})

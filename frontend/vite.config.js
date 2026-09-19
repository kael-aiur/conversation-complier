import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'node:path'

export default defineConfig(({ mode }) => ({
  plugins: [vue()],
  base: mode === 'spring' ? '/' : './',
  server: {
    host: '127.0.0.1',
    port: 5173,
    proxy: {
      '/api': 'http://127.0.0.1:8080',
    },
  },
  build: {
    outDir: mode === 'spring'
      ? resolve(import.meta.dirname, '../src/main/resources/static')
      : resolve(import.meta.dirname, 'dist'),
    assetsDir: 'assets',
    emptyOutDir: true,
  },
}))

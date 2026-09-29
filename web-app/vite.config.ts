import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

/**
 * 开发态通过 Vite 代理把 REST 与 WebSocket 都转发到后端，
 * 保证浏览器同源，避免跨域与 WS 握手被代理层掐断。
 * 生产态由 Nginx 做同样的事情。
 */
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const target = env.VITE_PROXY_TARGET || 'http://127.0.0.1:3001'

  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      port: 8888,
      host: '127.0.0.1',
      strictPort: true,
      proxy: {
        '/api': {
          target,
          changeOrigin: true,
        },
        // 后端静态资源（默认头像等）：数据库里存的是 /static/... 相对路径，
        // 开发态必须转发到后端，否则 <img> 会 404 到 Vite 自身
        '/static': {
          target,
          changeOrigin: true,
        },
        '/ws': {
          target: target.replace(/^http/, 'ws'),
          ws: true,
          changeOrigin: true,
        },
      },
    },
    build: {
      target: 'es2020',
      chunkSizeWarningLimit: 900,
      rollupOptions: {
        output: {
          manualChunks: {
            vue: ['vue', 'vue-router', 'pinia'],
          },
        },
      },
    },
  }
})

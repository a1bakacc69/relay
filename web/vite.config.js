import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 前端只跟 Relay 后端（8080）说话，供应商那边由 Relay 转发。
// 浏览器直连后端会跨域，所以统一走 /api 前缀，由 Vite 代理掉。
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    open: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      },
      // 供应商是另一个服务（8081）。只有商品页的「供应商货架」用得到 ——
      // 下单、查物流这些业务动作还是走 Relay，前端不绕过它
      '/supplier-api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/supplier-api/, '')
      }
    }
  }
})

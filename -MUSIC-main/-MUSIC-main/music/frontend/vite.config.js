import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],

  define: {
    global: 'globalThis',
  },

  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
      '/oauth2': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
      // WebSocket(STOMP/SockJS) - 세션 쿠키가 동일 출처로 전달되도록 프록시
      '/ws-stomp': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
        ws: true,
      },
      '/ws-chat': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
        ws: true,
      },
    },
  },
});
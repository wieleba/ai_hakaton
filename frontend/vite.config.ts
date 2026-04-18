import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig({
  plugins: [react()],
  // sockjs-client references `global`, a Node.js builtin. In a browser context
  // this throws and prevents React from mounting. Alias to globalThis.
  define: {
    global: 'globalThis',
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    host: '0.0.0.0',
    port: 3000,
    proxy: {
      '/api': {
        // In docker, VITE_API_TARGET=http://backend:8080 (service name on the
        // chat-network). Locally without docker, defaults to localhost:8080.
        target: process.env.VITE_API_TARGET || 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ws': {
        target: process.env.VITE_API_TARGET || 'http://localhost:8080',
        changeOrigin: true,
        ws: true,
      },
    },
  },
})

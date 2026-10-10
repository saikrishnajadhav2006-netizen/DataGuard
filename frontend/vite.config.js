import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: process.env.DATAGUARD_API_TARGET || 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})

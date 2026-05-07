// Configuracion de Vite, el servidor de desarrollo del frontend.
// Lo arrancamos con npm run dev, escucha en el puerto 5173 y sirve los archivos Vue.

// Lo mas importante de aqui es el PROXY: cuando el navegador hace una peticion a /api/... o /ws/...,
// Vite la intercepta y la reenvia al backend de Spring (localhost:8080) por dentro.
// Con esto el navegador "cree" que todo es del mismo origen (localhost:5173) y no hay problemas de CORS.

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      // Peticiones HTTP normales al backend
      '/api': 'http://localhost:8080',
      // WebSocket de la consola (ws: true es necesario para que reenvie el upgrade de HTTP a WebSocket)
      '/ws': { target: 'ws://localhost:8080', ws: true }
    }
  }
})

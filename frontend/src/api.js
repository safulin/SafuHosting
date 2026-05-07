// Cliente HTTP centralizado para hablar con el backend Spring Boot.
// Lo importamos en cualquier vista o componente que necesite hacer una peticion HTTP, asi:
//   import { api } from '../api.js'
//   const { data } = await api.get('/api/servidores/todos')

// Aqui pasan dos cosas importantes ademas de configurar la URL base:
//  1. Antes de cada peticion metemos el token JWT en la cabecera Authorization (lo saca de localStorage).
//  2. Si una peticion devuelve 401 (token caducado o invalido), borramos el token y mandamos a /login.

// El baseURL esta vacio porque trabajamos con el proxy de Vite (configurado en vite.config.js):
// el frontend hace peticiones a /api/... que su servidor de dev (localhost:5173) reenvia al backend (localhost:8080).
// Asi evitamos problemas de CORS y en produccion solo hay que cambiar la config del proxy/nginx.

import axios from 'axios'

export const api = axios.create({
  baseURL: ''
})

// Interceptor que añade el token JWT a todas las peticiones automáticamente.
// Cuando el usuario hace login guardamos el token en localStorage (en Login.vue),
// y aquí lo recogemos para meterlo en la cabecera Authorization de cada petición saliente.
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Si alguna petición devuelve 401 (no autorizado, token caducado o invalido),
// limpiamos el token y mandamos al login. Asi el usuario no se queda en una pantalla rota.
api.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      localStorage.removeItem('token')
      window.location.href = '/login'
    }
    return Promise.reject(err)
  }
)

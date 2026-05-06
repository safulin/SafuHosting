import axios from 'axios'

// Cliente axios configurado para hablar con nuestro backend Spring Boot
export const api = axios.create({
  baseURL: ''
})

// Interceptor que añade el token JWT a todas las peticiones automáticamente
// Cuando el usuario hace login guardamos el token en localStorage, y aquí lo recogemos
// para meterlo en la cabecera Authorization de cada petición
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Si alguna petición devuelve 401 (no autorizado) limpiamos el token y mandamos al login
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

import { createRouter, createWebHistory } from 'vue-router'
import Login from './views/Login.vue'
import Registro from './views/Registro.vue'
import Servidores from './views/Servidores.vue'
import Servidor from './views/Servidor.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/servidores' },
    { path: '/login', component: Login },
    { path: '/registro', component: Registro },
    { path: '/servidores', component: Servidores },
    { path: '/servidores/:id', component: Servidor, props: true }
  ]
})

// Guard de navegación: si no hay token y la ruta no es /login o /registro, mandamos a login
router.beforeEach((to) => {
  const publicas = ['/login', '/registro']
  if (!publicas.includes(to.path) && !localStorage.getItem('token')) {
    return '/login'
  }
})

export default router

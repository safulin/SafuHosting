// Router de Vue. Aqui definimos que componente se muestra en cada URL.
// Tambien tenemos un "guardia" que redirige a /login si intentas entrar a una ruta privada sin tener token.

import { createRouter, createWebHistory } from 'vue-router'
import Login from './views/Login.vue'
import Registro from './views/Registro.vue'
import Servidores from './views/Servidores.vue'
import Servidor from './views/Servidor.vue'

const router = createRouter({
  // createWebHistory usa la History API del navegador, asi las URLs son limpias (/servidores en lugar de /#/servidores)
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/servidores' },                          // raiz redirige al listado
    { path: '/login', component: Login },                            // pantalla de login
    { path: '/registro', component: Registro },                      // pantalla de registro
    { path: '/servidores', component: Servidores },                  // listado y formulario de creacion
    { path: '/servidores/:id', component: Servidor, props: true }    // detalle de un servidor (consola, mods, whitelist...)
                                                                     // props: true le pasa el :id de la URL al componente como prop
  ]
})

// Guard de navegación: se ejecuta antes de cada cambio de ruta.
// Si no hay token y la ruta no es /login o /registro, mandamos a login.
// Asi nadie puede acceder a /servidores sin estar logueado.
router.beforeEach((to) => {
  const publicas = ['/login', '/registro']
  if (!publicas.includes(to.path) && !localStorage.getItem('token')) {
    return '/login'
  }
})

export default router

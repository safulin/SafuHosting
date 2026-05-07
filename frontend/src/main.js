// Punto de entrada del frontend Vue. Es el primer archivo que ejecuta Vite cuando arrancas npm run dev.
// Aqui hacemos el "bootstrap" basico: importar Vue, importar el componente raiz (App.vue), importar el router
// con todas las rutas, y montar todo en el div con id="app" de index.html.

import { createApp } from 'vue'
import App from './App.vue'
import router from './router.js'
import './style.css' // estilos globales (colores, tarjetas, botones, etc.)

// createApp(App) crea la app Vue.
// .use(router) registra el router para que las <router-link> y router.push funcionen.
// .mount('#app') le dice "mete todo dentro del div con id=app que esta en index.html".
createApp(App).use(router).mount('#app')

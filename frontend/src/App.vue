<script setup>
// App.vue es el componente raiz que envuelve a toda la aplicacion. Aqui pintamos:
//  - Una barra superior con el nombre de la app, link a "Mis servidores" y boton de cerrar sesion.
//    Solo aparece si el usuario esta logueado y NO esta en /login o /registro.
//  - Un <router-view /> que es donde el router pinta el componente de la ruta actual
//    (Login, Registro, Servidores, Servidor segun la URL).

import { useRouter, useRoute } from 'vue-router'
import { computed } from 'vue'

const router = useRouter()
const route = useRoute()

// computed se recalcula automaticamente cuando cambia algo de lo que depende.
// Aqui en realidad localStorage no es reactivo, pero como esta funcion se ejecuta cada vez
// que se renderiza el template, basta con esto. Cuando hacemos logout y router.push('/login')
// el componente se vuelve a renderizar y el v-if del template se actualiza.
const logueado = computed(() => !!localStorage.getItem('token'))

function logout() {
  localStorage.removeItem('token')
  router.push('/login')
}
</script>

<template>
  <!-- Barra superior: solo se muestra si hay token Y no estamos en pantallas de login/registro -->
  <div class="barra" v-if="logueado && !['/login', '/registro'].includes(route.path)">
    <strong>SafuHost</strong>
    <router-link to="/servidores">Mis servidores</router-link>
    <button @click="logout" style="margin-left: auto">Cerrar sesión</button>
  </div>
  <!-- router-view es el "agujero" donde Vue Router pinta el componente correspondiente a la URL actual -->
  <router-view />
</template>

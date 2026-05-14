<script setup>
import { useRouter, useRoute } from 'vue-router'
import { computed } from 'vue'

const router = useRouter()
const route = useRoute()

const logueado = computed(() => !!localStorage.getItem('token'))

function logout() {
  localStorage.removeItem('token')
  router.push('/login')
}
</script>

<template>
  <div class="barra" v-if="logueado && !['/login', '/registro'].includes(route.path)">
    <strong>SafuHost</strong>
    <router-link to="/servidores">Mis servidores</router-link>
    <button @click="logout">Cerrar sesión</button>
  </div>
  <router-view />
</template>

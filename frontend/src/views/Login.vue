<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api.js'

const router = useRouter()
const username = ref('')
const password = ref('')
const error = ref('')

async function login() {
  error.value = ''
  try {
    const { data } = await api.post('/api/usuarios/login', {
      username: username.value,
      password: password.value
    })
    // Guardamos el token y vamos a la pantalla de servidores
    localStorage.setItem('token', data.token)
    router.push('/servidores')
  } catch (e) {
    error.value = e.response?.data || 'Error al iniciar sesión'
  }
}
</script>

<template>
  <div class="tarjeta" style="max-width: 400px; margin: 60px auto;">
    <h1>Iniciar sesión</h1>
    <form @submit.prevent="login">
      <input v-model="username" placeholder="Usuario" required style="width:100%" />
      <input v-model="password" type="password" placeholder="Contraseña" required style="width:100%" />
      <button type="submit" style="width:100%; margin-top:10px">Entrar</button>
    </form>
    <p v-if="error" class="error">{{ error }}</p>
    <p style="margin-top: 10px">
      ¿No tienes cuenta? <router-link to="/registro">Regístrate</router-link>
    </p>
  </div>
</template>

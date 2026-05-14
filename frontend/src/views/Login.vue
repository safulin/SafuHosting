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
    localStorage.setItem('token', data.token)
    router.push('/servidores')
  } catch (e) {
    error.value = e.response?.data || 'Error al iniciar sesión'
  }
}
</script>

<template>
  <div style="min-height:100vh; margin:-20px; background-image:url('/img/fondousers.png'); background-size:cover; background-position:top center; display:flex; align-items:center; justify-content:center; padding:20px;">
    <div class="tarjeta" style="width:100%; max-width:420px;">
      <div style="text-align:center; margin-bottom:20px;">
        <h1 style="font-size:14px; color:var(--mc-text);">SafuHost</h1>
        <p style="color:var(--mc-text-muted); font-size:12px; margin-top:6px;">Gestión de servidores Minecraft</p>
      </div>

      <form @submit.prevent="login">
        <label>Usuario</label>
        <input v-model="username" placeholder="Tu nombre de usuario" required />
        <label style="margin-top:12px;">Contraseña</label>
        <input v-model="password" type="password" placeholder="Tu contraseña" required />
        <button type="submit" style="width:100%; margin-top:20px; padding:12px; font-size:14px;">
          Iniciar sesión
        </button>
      </form>

      <p v-if="error" class="error" style="margin-top:12px;">{{ error }}</p>
      <p style="margin-top:16px; text-align:center; font-size:13px; color:var(--mc-text-muted);">
        ¿No tienes cuenta? <router-link to="/registro">Regístrate</router-link>
      </p>
    </div>
  </div>
</template>

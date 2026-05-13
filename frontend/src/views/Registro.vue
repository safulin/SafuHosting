<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api.js'

const router = useRouter()
const username = ref('')
const email = ref('')
const password = ref('')
const error = ref('')
const ok = ref('')

async function registrar() {
  error.value = ''
  ok.value = ''
  try {
    await api.post('/api/usuarios/registro', {
      username: username.value,
      email: email.value,
      password: password.value
    })
    ok.value = 'Registrado correctamente. Ya puedes iniciar sesión.'
    setTimeout(() => router.push('/login'), 1500)
  } catch (e) {
    error.value = e.response?.data || 'Error al registrarse'
  }
}
</script>

<template>
  <div class="tarjeta" style="max-width: 400px; margin: 60px auto;">
    <h1>Crear cuenta</h1>
    <form @submit.prevent="registrar">
      <input v-model="username" placeholder="Usuario" required style="width:100%" />
      <input v-model="email" type="email" placeholder="Email" required style="width:100%" />
      <input v-model="password" type="password" placeholder="Contraseña" required style="width:100%" />
      <button type="submit" style="width:100%; margin-top:10px">Registrarse</button>
    </form>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="ok" class="ok">{{ ok }}</p>
    <p style="margin-top: 10px">
      ¿Ya tienes cuenta? <router-link to="/login">Inicia sesión</router-link>
    </p>
  </div>
</template>

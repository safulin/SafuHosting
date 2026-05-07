<script setup>
// Pantalla de registro de usuarios nuevos. Pide username, email y contraseña, llama al backend
// para crear el usuario y si va bien manda al login para que se autentique.

// El backend (ServicioUsuario.registrar) cifra la contraseña con BCrypt antes de guardarla en SQLite,
// asi que aunque mandemos la contraseña en claro por la red (HTTPS la protege en produccion),
// en la BD nunca queda en texto plano.

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
    // Endpoint publico, no requiere token (esta en permitAll en el backend).
    // El backend valida que el username y el email no esten ya cogidos.
    await api.post('/api/usuarios/registro', {
      username: username.value,
      email: email.value,
      password: password.value
    })
    ok.value = 'Registrado correctamente. Ya puedes iniciar sesión.'
    // Damos un margen para que el usuario lea el mensaje y luego le mandamos a login
    setTimeout(() => router.push('/login'), 1500)
  } catch (e) {
    // Si el username/email ya existian el backend devuelve 400 con un mensaje claro
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

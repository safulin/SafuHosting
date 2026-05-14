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
  <div style="min-height:100vh; margin:-20px; background-image:url('/img/fondousers.png'); background-size:cover; background-position:top center; display:flex; align-items:center; justify-content:center; padding:20px;">
    <div class="tarjeta" style="width:100%; max-width:420px;">
      <div style="text-align:center; margin-bottom:20px;">
        <h1 style="font-size:14px; color:var(--mc-text);">Crear cuenta</h1>
        <p style="color:var(--mc-text-muted); font-size:12px; margin-top:6px;">Únete a SafuHost</p>
      </div>

      <form @submit.prevent="registrar">
        <label>Usuario</label>
        <input v-model="username" placeholder="Tu nombre de usuario" required />
        <label style="margin-top:12px;">Email</label>
        <input v-model="email" type="email" placeholder="tu@email.com" required />
        <label style="margin-top:12px;">Contraseña</label>
        <input v-model="password" type="password" placeholder="Elige una contraseña" required />
        <button type="submit" style="width:100%; margin-top:20px; padding:12px; font-size:14px;">
          Crear cuenta
        </button>
      </form>

      <p v-if="error" class="error" style="margin-top:12px;">{{ error }}</p>
      <p v-if="ok" class="ok" style="margin-top:12px;">{{ ok }}</p>
      <p style="margin-top:16px; text-align:center; font-size:13px; color:var(--mc-text-muted);">
        ¿Ya tienes cuenta? <router-link to="/login">Inicia sesión</router-link>
      </p>
    </div>
  </div>
</template>

<script setup>
// Pantalla de login. Aqui el usuario mete su username y contraseña, le damos a Entrar y:
//  - Si las credenciales son correctas, el backend devuelve un token JWT que guardamos en localStorage
//  - api.js a partir de ese momento mete el token en cada peticion automaticamente
//  - Hacemos router.push('/servidores') para mandar al listado de servidores

import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api.js'

const router = useRouter()
// ref crea variables reactivas: cuando cambia su .value, Vue actualiza el HTML automaticamente
const username = ref('')
const password = ref('')
const error = ref('')

async function login() {
  error.value = ''
  try {
    // Llamada al endpoint publico /api/usuarios/login (no requiere token, esta en permitAll en el backend).
    // Devuelve { token: "eyJhbGc..." }
    const { data } = await api.post('/api/usuarios/login', {
      username: username.value,
      password: password.value
    })
    // Guardamos el token en localStorage. A partir de aqui api.js lo metera en cada peticion
    // gracias al interceptor de request que tenemos configurado.
    localStorage.setItem('token', data.token)
    router.push('/servidores')
  } catch (e) {
    // Si el backend devuelve 401 (credenciales erroneas) lo mostramos al usuario
    error.value = e.response?.data || 'Error al iniciar sesión'
  }
}
</script>

<template>
  <div class="tarjeta" style="max-width: 400px; margin: 60px auto;">
    <h1>Iniciar sesión</h1>
    <!-- @submit.prevent evita que el form recargue la pagina al enviar (comportamiento por defecto del HTML) -->
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

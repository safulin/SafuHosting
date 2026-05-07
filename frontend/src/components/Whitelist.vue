<script setup>
// Componente que muestra y gestiona la whitelist de jugadores de un servidor.
// Lo monta Servidor.vue dentro de la pagina de detalle de un servidor.
// Recibe el id del servidor por prop y habla con el backend en /api/servidores/{id}/whitelist.

// El backend (ServicioWhitelist) ademas de actualizar la BD, si el servidor esta EN_LINEA tambien
// le manda el comando "whitelist add/remove" directamente a la consola de Minecraft via Docker,
// asi no hace falta reiniciar para que surta efecto.

import { ref, onMounted } from 'vue'
import { api } from '../api.js'

const props = defineProps({ id: { type: [String, Number], required: true } })

const lista = ref([])
const nuevo = ref('')
const error = ref('')

async function cargar() {
  // GET /api/servidores/{id}/whitelist devuelve un array de strings con los nombres permitidos
  const { data } = await api.get(`/api/servidores/${props.id}/whitelist`)
  lista.value = data
}

async function añadir() {
  if (!nuevo.value.trim()) return
  try {
    // Pasamos el nombre del jugador como path param (URL-encoded en el caso de espacios/raros)
    await api.post(`/api/servidores/${props.id}/whitelist/${nuevo.value}`)
    nuevo.value = ''
    await cargar() // recargamos para ver el jugador añadido
  } catch (e) {
    error.value = e.response?.data || 'Error'
  }
}

async function quitar(jugador) {
  try {
    await api.delete(`/api/servidores/${props.id}/whitelist/${jugador}`)
    await cargar()
  } catch (e) {
    error.value = e.response?.data || 'Error'
  }
}

// onMounted = "se ejecuta cuando el componente acaba de aparecer en pantalla"
// Aqui cargamos la lista por primera vez
onMounted(cargar)
</script>

<template>
  <div class="tarjeta">
    <h2>Whitelist</h2>
    <form @submit.prevent="añadir" style="display:flex; gap:6px">
      <input v-model="nuevo" placeholder="Nombre del jugador" style="flex:1" />
      <button type="submit">Añadir</button>
    </form>
    <ul style="margin-top:10px; list-style:none">
      <!-- v-for itera la lista. :key le dice a Vue como identificar cada item para reciclar el HTML -->
      <li v-for="j in lista" :key="j" style="padding:4px 0; display:flex; justify-content:space-between; align-items:center; border-bottom:1px solid #444">
        <span>{{ j }}</span>
        <button class="peligro" @click="quitar(j)">Quitar</button>
      </li>
    </ul>
    <p v-if="!lista.length" style="margin-top:10px; color:#888">Whitelist vacía</p>
    <p v-if="error" class="error">{{ error }}</p>
  </div>
</template>

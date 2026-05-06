<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { api } from '../api.js'

const props = defineProps({ id: { type: [String, Number], required: true } })

const lineas = ref([])
const comando = ref('')
const ws = ref(null)
const cajaLogs = ref(null)

onMounted(() => {
  // Conectamos por WebSocket al endpoint del backend
  // Usamos ws:// (no wss://) porque estamos en local sin TLS
  ws.value = new WebSocket(`ws://localhost:8080/ws/consola/${props.id}`)
  ws.value.onmessage = async (evento) => {
    lineas.value.push(evento.data)
    // Auto-scroll al final cada vez que llega una línea nueva
    await nextTick()
    if (cajaLogs.value) cajaLogs.value.scrollTop = cajaLogs.value.scrollHeight
  }
  ws.value.onerror = () => lineas.value.push('[Error de conexión con la consola]')
})

onUnmounted(() => {
  // Cerramos el WebSocket cuando salimos de la página para liberar recursos en el backend
  if (ws.value) ws.value.close()
})

async function enviar() {
  if (!comando.value.trim()) return
  try {
    await api.post(`/api/servidores/${props.id}/consola/comando`, comando.value, {
      headers: { 'Content-Type': 'text/plain' }
    })
    comando.value = ''
  } catch (e) {
    lineas.value.push('[Error al enviar comando: ' + (e.response?.data || e.message) + ']')
  }
}
</script>

<template>
  <div class="tarjeta">
    <h2>Consola en tiempo real</h2>
    <div ref="cajaLogs" style="background:#000; color:#0f0; padding:10px; height:400px; overflow-y:auto; font-family:monospace; font-size:12px; border-radius:4px">
      <div v-for="(l, i) in lineas" :key="i">{{ l }}</div>
    </div>
    <form @submit.prevent="enviar" style="display:flex; gap:6px; margin-top:8px">
      <input v-model="comando" placeholder="Escribe un comando (ej: say Hola, list, time set day)" style="flex:1" />
      <button type="submit">Enviar</button>
    </form>
  </div>
</template>

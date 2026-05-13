<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { api } from '../api.js'

const props = defineProps({ id: { type: [String, Number], required: true } })

const lineas = ref([])
const comando = ref('')
const ws = ref(null)
const cajaLogs = ref(null)
const reconectando = ref(false)
let cerradoManualmente = false
let timeoutReconexion = null

function conectar() {
  if (ws.value && ws.value.readyState !== WebSocket.CLOSED) {
    ws.value.onclose = null
    ws.value.close()
  }

  const protocolo = location.protocol === 'https:' ? 'wss' : 'ws'
  ws.value = new WebSocket(`${protocolo}://${location.host}/ws/consola/${props.id}`)

  ws.value.onmessage = async (evento) => {
    lineas.value.push(evento.data)
    await nextTick()
    if (cajaLogs.value) cajaLogs.value.scrollTop = cajaLogs.value.scrollHeight
  }

  ws.value.onerror = () => {
    lineas.value.push('[Error de conexión con la consola]')
  }

  ws.value.onclose = () => {
    if (cerradoManualmente) return
    reconectando.value = true
    timeoutReconexion = setTimeout(() => {
      reconectando.value = false
      conectar()
    }, 1500)
  }
}

function reconectar() {
  lineas.value = []
  conectar()
}

defineExpose({ reconectar })

onMounted(conectar)

onUnmounted(() => {
  cerradoManualmente = true
  if (timeoutReconexion) clearTimeout(timeoutReconexion)
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
    <h2>Consola en tiempo real
      <span v-if="reconectando" style="font-size:12px; color:#f90; font-weight:normal">— reconectando...</span>
    </h2>
    <div ref="cajaLogs" style="background:#000; color:#0f0; padding:10px; height:400px; overflow-y:auto; font-family:monospace; font-size:12px; border-radius:4px">
      <div v-for="(l, i) in lineas" :key="i">{{ l }}</div>
    </div>
    <form @submit.prevent="enviar" style="display:flex; gap:6px; margin-top:8px">
      <input v-model="comando" placeholder="Escribe un comando (ej: say Hola, list, time set day)" style="flex:1" />
      <button type="submit">Enviar</button>
    </form>
  </div>
</template>

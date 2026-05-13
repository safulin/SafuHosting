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
    <div style="display:flex; align-items:center; justify-content:space-between; margin-bottom:12px;">
      <h2>Consola en tiempo real</h2>
      <span v-if="reconectando"
            style="font-size:11px; color:#c8851a; font-weight:700; background:#fff8e8; padding:4px 10px; border-radius:12px; border:1px solid #c8851a;">
        ↻ Reconectando...
      </span>
    </div>

    <div ref="cajaLogs"
         style="background:#0D0D0D; color:#39FF14; padding:14px; height:420px; overflow-y:auto; font-family:'Courier New', monospace; font-size:12px; border-radius:4px; border:2px solid #222; line-height:1.5;">
      <div v-if="!lineas.length" style="color:#444; font-style:italic;">Esperando conexión con el servidor...</div>
      <div v-for="(l, i) in lineas" :key="i" style="word-break:break-all;">{{ l }}</div>
    </div>

    <form @submit.prevent="enviar" style="display:flex; gap:8px; margin-top:10px;">
      <input v-model="comando" placeholder="Escribe un comando (ej: say Hola, list, time set day)"
             style="flex:1; background:#1a1a1a; color:#39FF14; border-color:#333; font-family:'Courier New', monospace; margin:0;" />
      <button type="submit" style="margin:0; white-space:nowrap;">Enviar</button>
    </form>
  </div>
</template>

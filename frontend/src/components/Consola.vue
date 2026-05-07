<script setup>
// Consola en tiempo real del servidor de Minecraft. La pinta Servidor.vue dentro del panel de un servidor.
//
// Funcionamiento:
//  - Abrimos un WebSocket a ws://localhost:5173/ws/consola/{id} (lo proxea Vite a localhost:8080)
//  - El backend (ConsolaWebSocketHandler) abre un stream de logs de Docker para ese contenedor
//    y nos envia cada linea que escupe Minecraft
//  - Cada linea recibida se añade al array "lineas" y aparece en el div con auto-scroll al final
//
// Para ENVIAR comandos no usamos el WebSocket sino una peticion HTTP normal: POST /api/servidores/{id}/consola/comando.
// Es mas simple y no necesitamos comunicacion bidireccional permanente para algo que ocurre puntualmente.
//
// Reconexion: cuando el contenedor de Docker se para o reinicia, el stream de logs muere y el WebSocket recibe onclose.
// En ese caso reconectamos solos (con un margen de 1.5s para que Docker tenga tiempo de arrancar el nuevo contenedor).
// Tambien Servidor.vue puede forzar la reconexion al pulsar Iniciar/Parar llamando a nuestro metodo reconectar().

import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { api } from '../api.js'

const props = defineProps({ id: { type: [String, Number], required: true } })

const lineas = ref([])              // array de strings, cada uno una linea del log
const comando = ref('')             // texto del input para enviar comandos
const ws = ref(null)                // referencia al WebSocket actual
const cajaLogs = ref(null)          // referencia al div del log para hacer scroll automatico
const reconectando = ref(false)     // controla el indicador "reconectando..." del titulo
let cerradoManualmente = false      // flag para distinguir cierre intencional (al salir de la pagina) vs cierre por Docker
let timeoutReconexion = null

function conectar() {
  // Si ya hay conexión activa la cerramos primero (sin marcarla como manual, asi no triggea reconexion infinita).
  // Quitamos el onclose antes de cerrar para que no se ejecute la reconexion automatica
  if (ws.value && ws.value.readyState !== WebSocket.CLOSED) {
    ws.value.onclose = null
    ws.value.close()
  }

  // ws:// si la pagina es http://, wss:// si es https://. En dev siempre sera ws.
  // location.host es el host de la pagina actual (localhost:5173), asi pasamos por el proxy de Vite.
  const protocolo = location.protocol === 'https:' ? 'wss' : 'ws'
  ws.value = new WebSocket(`${protocolo}://${location.host}/ws/consola/${props.id}`)

  // Cada vez que el backend nos manda una linea de log, la añadimos al array y hacemos scroll al final
  ws.value.onmessage = async (evento) => {
    lineas.value.push(evento.data)
    // nextTick = espera a que Vue actualice el DOM con el nuevo elemento antes de hacer scroll
    await nextTick()
    if (cajaLogs.value) cajaLogs.value.scrollTop = cajaLogs.value.scrollHeight
  }

  ws.value.onerror = () => {
    lineas.value.push('[Error de conexión con la consola]')
  }

  // Cuando el contenedor de Docker para o reinicia, el stream de logs termina y el WebSocket
  // recibe un onclose. Reconectamos automáticamente para coger los logs del nuevo arranque.
  ws.value.onclose = () => {
    if (cerradoManualmente) return
    reconectando.value = true
    timeoutReconexion = setTimeout(() => {
      reconectando.value = false
      conectar()
    }, 1500)
  }
}

// Permitimos que el componente padre (Servidor.vue) fuerce un reconnect cuando el usuario
// pulsa Iniciar/Parar. Asi limpiamos los logs viejos y nos enganchamos al nuevo stream.
function reconectar() {
  lineas.value = []
  conectar()
}

// defineExpose hace que reconectar() sea visible desde fuera via la ref del componente.
// Servidor.vue lo llama con consolaRef.value.reconectar()
defineExpose({ reconectar })

onMounted(conectar)

onUnmounted(() => {
  // Al salir de la pagina cerramos manualmente, asi onclose no triggea la reconexion infinita
  cerradoManualmente = true
  if (timeoutReconexion) clearTimeout(timeoutReconexion)
  if (ws.value) ws.value.close()
})

async function enviar() {
  if (!comando.value.trim()) return
  try {
    // Mandamos el comando como text/plain (cuerpo de texto plano, sin envoltorios JSON).
    // El backend lo recibe en el @RequestBody String comando del controlador.
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

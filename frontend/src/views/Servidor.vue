<script setup>
// Pagina de detalle de un servidor. Aqui esta todo el panel de gestion de UN servidor concreto:
//  - Datos basicos (nombre, version, puerto, estado)
//  - Botones Iniciar / Parar / Refrescar estado / Eliminar
//  - Formulario de configuracion (dificultad, modo, pvp, etc.) con boton Guardar
//  - Consola en tiempo real (componente Consola.vue, via WebSocket)
//  - Whitelist (componente Whitelist.vue)
//  - Mods instalados y buscador de Modrinth (componente Mods.vue)

// El id del servidor viene en la URL (/servidores/:id) y nos llega como prop gracias a "props: true" en router.js.

import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api.js'
import Consola from '../components/Consola.vue'
import Whitelist from '../components/Whitelist.vue'
import Mods from '../components/Mods.vue'

const props = defineProps({ id: { type: String, required: true } })
const router = useRouter()

const servidor = ref(null)        // datos completos del servidor que viene del backend
const estadoReal = ref('')        // estado consultado a Docker (puede diferir del que tenemos en BD si el contenedor se cerro solo)
const error = ref('')
const consolaRef = ref(null)      // referencia al componente Consola para poder llamar a su metodo reconectar()

async function cargar() {
  try {
    // GET /api/servidores/{id} → devuelve el servidor completo o 400 si no es del usuario logueado
    const { data } = await api.get(`/api/servidores/${props.id}`)
    servidor.value = data
  } catch (e) {
    error.value = e.response?.data || 'Error al cargar'
  }
}

async function consultarEstado() {
  // El estado en BD puede estar desactualizado si el contenedor se cerro solo (por crash, OOM, etc.).
  // Este endpoint pregunta a Docker el estado real y de paso lo sincroniza en BD.
  try {
    const { data } = await api.get(`/api/servidores/${props.id}/estado`)
    estadoReal.value = data
    if (servidor.value) servidor.value.estado = data
  } catch (e) {
    error.value = e.response?.data || 'Error'
  }
}

async function iniciar() {
  await api.post(`/api/servidores/${props.id}/iniciar`)
  await consultarEstado()
  // Damos un margen para que Docker arranque el contenedor y luego reconectamos
  // la consola al nuevo stream de logs (el viejo terminó cuando el contenedor paró)
  setTimeout(() => consolaRef.value?.reconectar(), 1000)
}

async function parar() {
  await api.post(`/api/servidores/${props.id}/parar`)
  await consultarEstado()
  setTimeout(() => consolaRef.value?.reconectar(), 1000)
}

async function guardar() {
  // PUT /api/servidores/{id} con el objeto entero. El backend solo actualiza los campos editables
  // (nombre, dificultad, modo, pvp, whitelist...). Puerto, idContenedor y version NO se tocan
  // porque estan ligados al contenedor de Docker que ya existe.
  try {
    await api.put(`/api/servidores/${props.id}`, servidor.value)
    error.value = ''
  } catch (e) {
    error.value = e.response?.data || 'Error al guardar'
  }
}

async function eliminar() {
  // confirm() del navegador, simple pero suficiente. Si el usuario cancela no hacemos nada.
  if (!confirm('¿Eliminar este servidor? Se borrará todo, incluido el contenedor de Docker.')) return
  await api.delete(`/api/servidores/${props.id}`)
  router.push('/servidores')
}

onMounted(cargar)
</script>

<template>
  <div v-if="servidor">
    <h1>{{ servidor.nombre }}</h1>
    <p style="color:#aaa">Versión {{ servidor.version }} · {{ servidor.tipo }} · Puerto {{ servidor.puerto }}</p>

    <div class="tarjeta">
      <h2>Control</h2>
      <p>Estado actual: <strong>{{ servidor.estado }}</strong></p>
      <button @click="iniciar">Iniciar</button>
      <button class="peligro" @click="parar">Parar</button>
      <button @click="consultarEstado">Refrescar estado</button>
      <button class="peligro" @click="eliminar" style="float:right">Eliminar servidor</button>
    </div>

    <div class="tarjeta">
      <h2>Configuración</h2>
      <div style="display:grid; grid-template-columns:1fr 1fr; gap:8px">
        <label>Nombre <input v-model="servidor.nombre" /></label>
        <label>Dificultad
          <select v-model="servidor.dificultad">
            <option>peaceful</option><option>easy</option><option>normal</option><option>hard</option>
          </select>
        </label>
        <label>Modo de juego
          <select v-model="servidor.modoJuego">
            <option>survival</option><option>creative</option><option>adventure</option>
          </select>
        </label>
        <label>OPs <input v-model="servidor.administradores" /></label>
        <label><input type="checkbox" v-model="servidor.pvp" /> PvP</label>
        <label><input type="checkbox" v-model="servidor.modoOnline" /> Modo Premium</label>
        <label><input type="checkbox" v-model="servidor.usarWhitelist" /> Whitelist activada</label>
      </div>
      <button @click="guardar" style="margin-top:10px">Guardar cambios</button>
    </div>

    <Consola :id="id" ref="consolaRef" />
    <Whitelist :id="id" />
    <Mods :id="id" />

    <p v-if="error" class="error">{{ error }}</p>
  </div>
  <p v-else>Cargando...</p>
</template>

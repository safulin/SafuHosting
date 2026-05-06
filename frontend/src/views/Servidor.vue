<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api.js'
import Consola from '../components/Consola.vue'
import Whitelist from '../components/Whitelist.vue'
import Mods from '../components/Mods.vue'

const props = defineProps({ id: { type: String, required: true } })
const router = useRouter()

const servidor = ref(null)
const estadoReal = ref('')
const error = ref('')

async function cargar() {
  try {
    const { data } = await api.get(`/api/servidores/${props.id}`)
    servidor.value = data
  } catch (e) {
    error.value = e.response?.data || 'Error al cargar'
  }
}

async function consultarEstado() {
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
}

async function parar() {
  await api.post(`/api/servidores/${props.id}/parar`)
  await consultarEstado()
}

async function guardar() {
  try {
    await api.put(`/api/servidores/${props.id}`, servidor.value)
    error.value = ''
  } catch (e) {
    error.value = e.response?.data || 'Error al guardar'
  }
}

async function eliminar() {
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

    <Consola :id="id" />
    <Whitelist :id="id" />
    <Mods :id="id" />

    <p v-if="error" class="error">{{ error }}</p>
  </div>
  <p v-else>Cargando...</p>
</template>

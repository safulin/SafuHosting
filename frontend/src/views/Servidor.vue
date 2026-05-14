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
const consolaRef = ref(null)

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
  setTimeout(() => consolaRef.value?.reconectar(), 1000)
}

async function parar() {
  await api.post(`/api/servidores/${props.id}/parar`)
  await consultarEstado()
  setTimeout(() => consolaRef.value?.reconectar(), 1000)
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
    <div style="display:flex; align-items:center; gap:16px; margin-bottom:6px;">
      <img v-if="servidor.urlIcono" :src="servidor.urlIcono"
           style="width:56px; height:56px; border-radius:4px; border:3px solid var(--mc-border-dark); box-shadow:3px 3px 0 rgba(0,0,0,0.3);" />
      <div v-else
           style="width:56px; height:56px; border-radius:4px; border:3px solid var(--mc-border-dark); background:var(--mc-tan-dark); display:flex; align-items:center; justify-content:center; font-size:28px; box-shadow:3px 3px 0 rgba(0,0,0,0.3);">
        🌍
      </div>
      <div>
        <h1 style="color:var(--mc-text-light); text-shadow:3px 3px 0 rgba(0,0,0,0.4); font-size:16px;">{{ servidor.nombre }}</h1>
        <p style="color:var(--mc-tan-dark); font-size:13px; margin-top:4px;">
          {{ servidor.version }} · {{ servidor.tipo }} · Puerto {{ servidor.puerto }}
        </p>
      </div>
    </div>

    <div class="tarjeta">
      <h2 style="margin-bottom:16px;">Control del servidor</h2>
      <div style="display:flex; align-items:center; gap:10px; margin-bottom:14px; padding:12px; background:var(--mc-tan-light); border-radius:4px; border:2px solid var(--mc-border-dark);">
        <span style="display:inline-block; width:14px; height:14px; border-radius:50%;"
              :style="{ background: servidor.estado?.toLowerCase().includes('run') || servidor.estado?.toLowerCase().includes('activo') ? '#5AAB37' : servidor.estado?.toLowerCase().includes('stop') || servidor.estado?.toLowerCase().includes('parado') ? '#AA2222' : '#c8851a' }">
        </span>
        <span style="font-weight:700; font-size:14px; color:var(--mc-text);">{{ servidor.estado || 'Desconocido' }}</span>
      </div>
      <div style="display:flex; flex-wrap:wrap; gap:6px; align-items:center;">
        <button @click="iniciar" style="font-size:13px;">▶ Iniciar</button>
        <button class="peligro" @click="parar" style="font-size:13px;">⏹ Parar</button>
        <button @click="consultarEstado"
                style="font-size:13px; background:var(--mc-tan-dark); border-bottom-color:var(--mc-border-dark); color:var(--mc-text);">
          ↻ Refrescar estado
        </button>
        <button class="peligro" @click="eliminar" style="font-size:13px; margin-left:auto;">🗑 Eliminar servidor</button>
      </div>
    </div>

    <div class="tarjeta">
      <h2 style="margin-bottom:16px;">Configuración</h2>
      <div style="display:grid; grid-template-columns:1fr 1fr; gap:14px;">
        <div>
          <label>Nombre</label>
          <input v-model="servidor.nombre" />
        </div>
        <div>
          <label>Dificultad</label>
          <select v-model="servidor.dificultad">
            <option>peaceful</option><option>easy</option><option>normal</option><option>hard</option>
          </select>
        </div>
        <div>
          <label>Modo de juego</label>
          <select v-model="servidor.modoJuego">
            <option>survival</option><option>creative</option><option>adventure</option>
          </select>
        </div>
        <div>
          <label>OPs (Administradores)</label>
          <input v-model="servidor.administradores" />
        </div>
        <label style="display:flex; gap:8px; align-items:center; cursor:pointer;">
          <input type="checkbox" v-model="servidor.pvp" /> PvP activado
        </label>
        <label style="display:flex; gap:8px; align-items:center; cursor:pointer;">
          <input type="checkbox" v-model="servidor.modoOnline" /> Modo Premium
        </label>
        <label style="display:flex; gap:8px; align-items:center; cursor:pointer;">
          <input type="checkbox" v-model="servidor.usarWhitelist" /> Whitelist activada
        </label>
      </div>
      <button @click="guardar" style="margin-top:16px; font-size:13px;">Guardar cambios</button>
    </div>

    <Consola :id="id" ref="consolaRef" />
    <Whitelist :id="id" />
    <Mods :id="id" />

    <p v-if="error" class="error">{{ error }}</p>
  </div>

  <div v-else style="display:flex; justify-content:center; align-items:center; min-height:40vh;">
    <div class="tarjeta" style="text-align:center; padding:40px 60px;">
      <div style="font-size:32px; margin-bottom:12px;">⏳</div>
      <p style="color:var(--mc-text-muted); font-size:14px;">Cargando servidor...</p>
    </div>
  </div>
</template>

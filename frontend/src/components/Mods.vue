<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../api.js'

const props = defineProps({ id: { type: [String, Number], required: true } })

const instalados = ref([])
const busqueda = ref('')
const resultados = ref([])
const error = ref('')
const mensaje = ref('')

async function cargarInstalados() {
  const { data } = await api.get(`/api/servidores/${props.id}/mods`)
  instalados.value = data
}

async function buscar() {
  error.value = ''
  resultados.value = []
  if (!busqueda.value.trim()) return
  try {
    const { data } = await api.get('/api/servidores/mods/buscar', {
      params: { query: busqueda.value }
    })
    // La API de Modrinth devuelve { hits: [...], total_hits, ... }
    resultados.value = data.hits || []
  } catch (e) {
    error.value = e.response?.data || 'Error al buscar'
  }
}

async function instalar(modrinthId, titulo) {
  error.value = ''
  mensaje.value = `Instalando ${titulo}...`
  try {
    await api.post(`/api/servidores/${props.id}/mods/instalar/${modrinthId}`)
    mensaje.value = `${titulo} instalado correctamente. Reinicia el servidor para que cargue.`
    await cargarInstalados()
  } catch (e) {
    error.value = e.response?.data || 'Error al instalar'
    mensaje.value = ''
  }
}

async function eliminar(nombreMod) {
  if (!confirm(`¿Eliminar ${nombreMod}?`)) return
  try {
    await api.delete(`/api/servidores/${props.id}/mods/${nombreMod}`)
    await cargarInstalados()
  } catch (e) {
    error.value = e.response?.data || 'Error'
  }
}

onMounted(cargarInstalados)
</script>

<template>
  <div class="tarjeta">
    <h2>Mods (Modrinth)</h2>

    <form @submit.prevent="buscar" style="display:flex; gap:6px">
      <input v-model="busqueda" placeholder="Buscar mod (ej: jei, sodium, create)" style="flex:1" />
      <button type="submit">Buscar</button>
    </form>

    <div v-if="resultados.length" style="margin-top:10px">
      <h3>Resultados</h3>
      <div v-for="r in resultados" :key="r.project_id" style="display:flex; gap:10px; padding:8px; border-bottom:1px solid #444; align-items:center">
        <img v-if="r.icon_url" :src="r.icon_url" style="width:48px; height:48px; border-radius:4px" />
        <div style="flex:1">
          <strong>{{ r.title }}</strong>
          <div style="font-size:12px; color:#aaa">{{ r.description }}</div>
        </div>
        <button @click="instalar(r.project_id, r.title)">Instalar</button>
      </div>
    </div>

    <h3 style="margin-top:20px">Instalados ({{ instalados.length }})</h3>
    <ul style="list-style:none">
      <li v-for="m in instalados" :key="m" style="padding:4px 0; display:flex; justify-content:space-between; align-items:center; border-bottom:1px solid #444">
        <span>{{ m }}</span>
        <button class="peligro" @click="eliminar(m)">Eliminar</button>
      </li>
    </ul>
    <p v-if="!instalados.length" style="color:#888">Ningún mod instalado</p>

    <p v-if="mensaje" class="ok">{{ mensaje }}</p>
    <p v-if="error" class="error">{{ error }}</p>
  </div>
</template>

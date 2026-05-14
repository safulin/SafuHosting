<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../api.js'

const props = defineProps({ id: { type: [String, Number], required: true } })

const instalados = ref([])
const busqueda = ref('')
const resultados = ref([])
const error = ref('')
const mensaje = ref('')
const verificando = ref(null)
const dependenciasPendientes = ref({ modId: null, deps: [] })
const servidor = ref(null)

async function cargarInstalados() {
  const { data } = await api.get(`/api/servidores/${props.id}/mods`)
  instalados.value = data
}

async function cargarServidor() {
  try {
    const { data } = await api.get(`/api/servidores/${props.id}`)
    servidor.value = data
  } catch (e) { }
}

async function buscar() {
  error.value = ''
  resultados.value = []
  if (!busqueda.value.trim()) return
  try {
    const { data } = await api.get('/api/servidores/mods/buscar', {
      params: { query: busqueda.value }
    })
    resultados.value = data.hits || []
  } catch (e) {
    error.value = e.response?.data || 'Error al buscar'
  }
}

async function instalarMod(modrinthId, titulo) {
  error.value = ''
  mensaje.value = `Instalando ${titulo}...`
  try {
    await api.post(`/api/servidores/${props.id}/mods/instalar/${modrinthId}`)
    mensaje.value = `${titulo} instalado. Reinicia el servidor para que cargue.`
    await cargarInstalados()
  } catch (e) {
    error.value = e.response?.data || 'Error al instalar'
    mensaje.value = ''
  }
}

async function instalar(modrinthId, titulo) {
  error.value = ''
  verificando.value = modrinthId
  dependenciasPendientes.value = { modId: null, deps: [] }
  try {
    const { data: verificacion } = await api.get('/api/servidores/mods/verificar', {
      params: { modId: modrinthId, tipo: servidor.value?.tipo, version: servidor.value?.version }
    })
    if (!verificacion.compatible) {
      error.value = verificacion.motivo
      return
    }
    const depsNuevas = (verificacion.dependencias || []).filter(
      d => !instalados.value.some(nombre => nombre.toLowerCase().includes(d.titulo.toLowerCase()))
    )
    if (depsNuevas.length > 0) {
      dependenciasPendientes.value = { modId: modrinthId, titulo, deps: depsNuevas }
      return
    }
    await instalarMod(modrinthId, titulo)
  } catch (e) {
    error.value = e.response?.data || 'Error al verificar'
  } finally {
    verificando.value = null
  }
}

async function instalarConDependencias() {
  const { modId, titulo, deps } = dependenciasPendientes.value
  dependenciasPendientes.value = { modId: null, deps: [] }
  for (const dep of deps) {
    await instalarMod(dep.id, dep.titulo)
  }
  await instalarMod(modId, titulo)
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

onMounted(() => { cargarInstalados(); cargarServidor() })
</script>

<template>
  <div class="tarjeta">
    <h2 style="margin-bottom:16px;">Mods (Modrinth)</h2>

    <form @submit.prevent="buscar" style="display:flex; gap:8px; margin-bottom:14px;">
      <input v-model="busqueda" placeholder="Buscar mod (ej: jei, sodium, create)" style="flex:1; margin:0;" />
      <button type="submit" style="margin:0; white-space:nowrap;">Buscar</button>
    </form>

    <div v-if="dependenciasPendientes.deps.length"
         style="background:#fff8e8; border:2px solid #c8851a; border-radius:4px; padding:14px; margin-bottom:14px;">
      <strong style="color:#c8851a; font-size:13px;">⚠ Este mod requiere dependencias no instaladas:</strong>
      <div style="display:flex; flex-wrap:wrap; gap:6px; margin:10px 0;">
        <span v-for="d in dependenciasPendientes.deps" :key="d.id"
              style="background:#f0e0b0; border-radius:12px; padding:3px 10px; font-size:12px; color:var(--mc-text);">
          {{ d.titulo }}
        </span>
      </div>
      <div style="display:flex; gap:8px;">
        <button @click="instalarConDependencias" style="font-size:12px; padding:5px 12px; margin:0;">Instalar todo</button>
        <button @click="instalarMod(dependenciasPendientes.modId, dependenciasPendientes.titulo); dependenciasPendientes = { modId: null, deps: [] }"
                style="font-size:12px; padding:5px 12px; margin:0; background:var(--mc-tan-dark); border-bottom-color:var(--mc-border-dark); color:var(--mc-text);">
          Solo el mod
        </button>
      </div>
    </div>

    <div v-if="resultados.length" style="margin-bottom:20px;">
      <h3 style="margin-bottom:10px;">Resultados de búsqueda</h3>
      <div style="border:2px solid var(--mc-border-dark); border-radius:4px; overflow:hidden; background:white;">
        <div v-for="r in resultados" :key="r.project_id"
             style="display:flex; gap:12px; padding:12px 14px; border-bottom:1px solid #eee; align-items:center;">
          <img v-if="r.icon_url" :src="r.icon_url"
               style="width:48px; height:48px; border-radius:4px; border:1px solid #ddd; flex-shrink:0;" />
          <div v-else
               style="width:48px; height:48px; border-radius:4px; background:#f0f0f0; flex-shrink:0; display:flex; align-items:center; justify-content:center; font-size:20px;">🧩</div>
          <div style="flex:1; min-width:0;">
            <strong style="font-size:14px; color:var(--mc-text);">{{ r.title }}</strong>
            <div style="font-size:12px; color:#888; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; margin-top:2px;">{{ r.description }}</div>
          </div>
          <button @click="instalar(r.project_id, r.title)"
                  :disabled="verificando === r.project_id"
                  style="min-width:100px; margin:0; font-size:12px; flex-shrink:0;">
            {{ verificando === r.project_id ? 'Verificando...' : 'Instalar' }}
          </button>
        </div>
      </div>
    </div>

    <h3 style="margin-bottom:12px;">Instalados ({{ instalados.length }})</h3>
    <div v-if="instalados.length"
         style="border:2px solid var(--mc-border-dark); border-radius:4px; overflow:hidden; background:var(--mc-tan-light);">
      <div v-for="m in instalados" :key="m"
           style="display:flex; justify-content:space-between; align-items:center; padding:10px 14px; border-bottom:1px solid var(--mc-tan-dark);">
        <div style="display:flex; align-items:center; gap:8px;">
          <span style="font-size:16px;">🧩</span>
          <span style="font-size:13px; font-weight:600; color:var(--mc-text);">{{ m }}</span>
        </div>
        <button class="peligro" @click="eliminar(m)" style="font-size:12px; padding:5px 10px; margin:0;">Eliminar</button>
      </div>
    </div>
    <p v-if="!instalados.length" style="color:var(--mc-text-muted); text-align:center; padding:20px; font-size:13px;">
      Ningún mod instalado
    </p>

    <p v-if="mensaje" class="ok" style="margin-top:12px;">{{ mensaje }}</p>
    <p v-if="error" class="error" style="margin-top:12px;">{{ error }}</p>
  </div>
</template>

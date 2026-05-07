<script setup>
// Componente que gestiona los mods de un servidor existente. Lo monta Servidor.vue.
// Permite:
//  - Ver los mods .jar que hay en la carpeta /mods del servidor
//  - Buscar mods en Modrinth (la web/api de mods de Minecraft)
//  - Verificar compatibilidad antes de instalar (tipo de loader + version MC + no es modpack)
//  - Si el mod tiene dependencias requeridas, ofrecer instalarlas tambien
//  - Eliminar mods instalados
//
// Para instalar mods al CREAR el servidor existe otro flujo similar dentro de Servidores.vue.
// Aqui es para cuando el servidor ya existe y quieres añadir/quitar mods.

import { ref, onMounted } from 'vue'
import { api } from '../api.js'

const props = defineProps({ id: { type: [String, Number], required: true } })

const instalados = ref([])                              // array de strings con los nombres de los .jar instalados
const busqueda = ref('')                                // texto que el usuario escribe para buscar
const resultados = ref([])                              // resultados de Modrinth (objeto con title, description, project_id, icon_url...)
const error = ref('')
const mensaje = ref('')
const verificando = ref(null)                           // project_id del mod que estamos verificando ahora mismo (para deshabilitar el boton)
const dependenciasPendientes = ref({ modId: null, deps: [] })  // si un mod tiene deps no instaladas, las guardamos aqui para que el usuario decida
const servidor = ref(null)                              // datos del servidor (necesarios para saber tipo y version al verificar)

async function cargarInstalados() {
  // GET /api/servidores/{id}/mods → array con los nombres de archivos .jar de la carpeta /mods
  const { data } = await api.get(`/api/servidores/${props.id}/mods`)
  instalados.value = data
}

async function cargarServidor() {
  // Necesitamos el servidor para saber su tipo (FORGE/FABRIC) y version (1.21, 1.20.1...)
  // y poder pasarselos al endpoint de verificacion
  try {
    const { data } = await api.get(`/api/servidores/${props.id}`)
    servidor.value = data
  } catch (e) { /* ignoramos errores aqui, ya se mostraran si el usuario intenta instalar */ }
}

async function buscar() {
  error.value = ''
  resultados.value = []
  if (!busqueda.value.trim()) return
  try {
    // El backend hace de proxy a Modrinth para evitar CORS y centralizar la logica
    const { data } = await api.get('/api/servidores/mods/buscar', {
      params: { query: busqueda.value }
    })
    // La API de Modrinth devuelve { hits: [...], total_hits, ... }
    resultados.value = data.hits || []
  } catch (e) {
    error.value = e.response?.data || 'Error al buscar'
  }
}

// Descarga el .jar y lo coloca en la carpeta /mods del servidor. Sin verificacion previa, lo hace directo.
// Lo llaman las funciones de mas arriba cuando ya saben que el mod es compatible y las deps estan resueltas.
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

// Funcion principal del boton "Instalar". Antes de descargar el mod:
//  1. Verificamos en Modrinth que es compatible con el tipo y version del servidor
//  2. Si tiene dependencias requeridas no instaladas, las mostramos al usuario y esperamos confirmacion
//  3. Si no hay dependencias o el usuario confirma, llamamos a instalarMod
async function instalar(modrinthId, titulo) {
  error.value = ''
  verificando.value = modrinthId
  dependenciasPendientes.value = { modId: null, deps: [] }
  try {
    // 1. Verificacion de compatibilidad. El backend devuelve { compatible, archivo, modpack, dependencias }
    const { data: verificacion } = await api.get('/api/servidores/mods/verificar', {
      params: { modId: modrinthId, tipo: servidor.value?.tipo, version: servidor.value?.version }
    })
    if (!verificacion.compatible) {
      error.value = verificacion.motivo
      return
    }
    // 2. Filtramos las dependencias que NO esten ya instaladas (comparando por nombre, simple pero suficiente)
    const depsNuevas = (verificacion.dependencias || []).filter(
      d => !instalados.value.some(nombre => nombre.toLowerCase().includes(d.titulo.toLowerCase()))
    )
    if (depsNuevas.length > 0) {
      // Guardamos las deps pendientes y mostramos el aviso, el usuario decide en el template
      dependenciasPendientes.value = { modId: modrinthId, titulo, deps: depsNuevas }
      return
    }
    // 3. Sin dependencias pendientes → instalamos directamente
    await instalarMod(modrinthId, titulo)
  } catch (e) {
    error.value = e.response?.data || 'Error al verificar'
  } finally {
    verificando.value = null
  }
}

// El usuario ha confirmado "Instalar todo": instalamos primero las dependencias y luego el mod principal
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

// Al montarse el componente cargamos en paralelo los mods instalados y los datos del servidor
onMounted(() => { cargarInstalados(); cargarServidor() })
</script>

<template>
  <div class="tarjeta">
    <h2>Mods (Modrinth)</h2>

    <form @submit.prevent="buscar" style="display:flex; gap:6px">
      <input v-model="busqueda" placeholder="Buscar mod (ej: jei, sodium, create)" style="flex:1" />
      <button type="submit">Buscar</button>
    </form>

    <!-- Aviso de dependencias requeridas -->
    <div v-if="dependenciasPendientes.deps.length" style="background:#2a1f00; border:1px solid #f90; border-radius:6px; padding:10px; margin-top:10px">
      <strong style="color:#f90">⚠ Este mod requiere dependencias no instaladas:</strong>
      <span v-for="d in dependenciasPendientes.deps" :key="d.id"
            style="display:inline-block; background:#333; border-radius:10px; padding:2px 8px; margin:4px 4px 0; font-size:12px">{{ d.titulo }}</span>
      <div style="margin-top:8px; display:flex; gap:8px">
        <button @click="instalarConDependencias" style="font-size:12px; padding:3px 10px">Instalar todo</button>
        <button @click="instalarMod(dependenciasPendientes.modId, dependenciasPendientes.titulo); dependenciasPendientes = { modId: null, deps: [] }"
                style="font-size:12px; padding:3px 10px; background:transparent; border-color:#666; color:#aaa">
          Solo el mod
        </button>
      </div>
    </div>

    <div v-if="resultados.length" style="margin-top:10px">
      <h3>Resultados</h3>
      <div v-for="r in resultados" :key="r.project_id" style="display:flex; gap:10px; padding:8px; border-bottom:1px solid #444; align-items:center">
        <img v-if="r.icon_url" :src="r.icon_url" style="width:48px; height:48px; border-radius:4px" />
        <div style="flex:1">
          <strong>{{ r.title }}</strong>
          <div style="font-size:12px; color:#aaa">{{ r.description }}</div>
        </div>
        <button @click="instalar(r.project_id, r.title)"
                :disabled="verificando === r.project_id"
                style="min-width:90px">
          {{ verificando === r.project_id ? 'Verificando...' : 'Instalar' }}
        </button>
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

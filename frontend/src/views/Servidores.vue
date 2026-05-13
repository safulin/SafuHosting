<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../api.js'

const servidores = ref([])
const versiones = ref([])
const error = ref('')
const cargando = ref(false)

const nuevo = ref({
  nombre: '',
  version: 'LATEST',
  tipo: 'VANILLA',
  dificultad: 'normal',
  modoJuego: 'survival',
  pvp: true,
  modoOnline: false,
  usarWhitelist: false,
  listaBlanca: '',
  administradores: '',
  urlIcono: '',
  modIniciales: []
})

const busquedaMod = ref('')
const resultadosMods = ref([])
const buscandoMods = ref(false)

async function buscarModsParaCrear() {
  if (!busquedaMod.value.trim()) return
  buscandoMods.value = true
  try {
    const { data } = await api.get('/api/servidores/mods/buscar', { params: { query: busquedaMod.value } })
    resultadosMods.value = data.hits || []
  } finally {
    buscandoMods.value = false
  }
}

const verificando = ref(null)
const erroresCompatibilidad = ref({})
const dependenciasPendientes = ref([])

async function verificarYAñadir(modId, titulo) {
  const { data } = await api.get('/api/servidores/mods/verificar', {
    params: { modId, tipo: nuevo.value.tipo, version: nuevo.value.version }
  })
  if (!data.compatible) throw new Error(data.motivo)
  if (!nuevo.value.modIniciales.some(m => m.id === modId)) {
    const etiqueta = data.modpack ? `${titulo} (modpack)` : titulo
    nuevo.value.modIniciales.push({ id: modId, titulo: etiqueta })
  }
  return data.dependencias || []
}

async function añadirMod(mod) {
  if (nuevo.value.modIniciales.some(m => m.id === mod.project_id)) return

  verificando.value = mod.project_id
  delete erroresCompatibilidad.value[mod.project_id]
  dependenciasPendientes.value = []

  try {
    const deps = await verificarYAñadir(mod.project_id, mod.title)
    dependenciasPendientes.value = deps.filter(d => !nuevo.value.modIniciales.some(m => m.id === d.id))
  } catch (e) {
    erroresCompatibilidad.value[mod.project_id] = e.message || 'Error al verificar compatibilidad'
  } finally {
    verificando.value = null
  }
}

async function añadirDependencias() {
  for (const dep of dependenciasPendientes.value) {
    try {
      await verificarYAñadir(dep.id, dep.titulo)
    } catch (e) {
    }
  }
  dependenciasPendientes.value = []
}

function quitarMod(modId) {
  nuevo.value.modIniciales = nuevo.value.modIniciales.filter(m => m.id !== modId)
}

async function cargar() {
  try {
    const { data } = await api.get('/api/servidores/todos')
    servidores.value = data
  } catch (e) {
    error.value = e.response?.data || 'Error al cargar servidores'
  }
}

async function cargarVersiones() {
  try {
    const { data } = await api.get('/api/servidores/versiones')
    versiones.value = data
  } catch (e) {
    console.error('Error al cargar versiones', e)
    versiones.value = ['LATEST']
  }
}

async function crear() {
  error.value = ''
  cargando.value = true
  try {
    const payload = { ...nuevo.value, modIniciales: nuevo.value.modIniciales.map(m => m.id) }
    await api.post('/api/servidores/crear', payload)
    nuevo.value.nombre = ''
    nuevo.value.listaBlanca = ''
    nuevo.value.administradores = ''
    nuevo.value.modIniciales = []
    resultadosMods.value = []
    busquedaMod.value = ''
    await cargar()
  } catch (e) {
    error.value = e.response?.data || 'Error al crear servidor'
  } finally {
    cargando.value = false
  }
}

async function eliminar(id) {
  if (!confirm('¿Seguro que quieres eliminar este servidor? Se borrará todo.')) return
  try {
    await api.delete(`/api/servidores/${id}`)
    await cargar()
  } catch (e) {
    error.value = e.response?.data || 'Error al eliminar'
  }
}

onMounted(async () => {
  await cargarVersiones()
  await cargar()
})
</script>

<template>
  <h1>Mis servidores</h1>

  <div class="tarjeta">
    <h2>Crear servidor</h2>
    <form @submit.prevent="crear">
      <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px;">

        <label>
          Nombre del servidor
          <input v-model="nuevo.nombre" placeholder="MiMundo" required style="width:100%" />
        </label>

        <label>
          Versión de Minecraft
          <select v-model="nuevo.version" style="width:100%">
            <option v-for="v in versiones" :key="v" :value="v">{{ v }}</option>
          </select>
        </label>

        <label>
          Tipo de servidor
          <select v-model="nuevo.tipo" style="width:100%">
            <option value="VANILLA">VANILLA (sin mods)</option>
            <option value="FORGE">FORGE (mods)</option>
            <option value="FABRIC">FABRIC (mods)</option>
          </select>
        </label>

        <label>
          Dificultad
          <select v-model="nuevo.dificultad" style="width:100%">
            <option>peaceful</option>
            <option>easy</option>
            <option>normal</option>
            <option>hard</option>
          </select>
        </label>

        <label>
          Modo de juego
          <select v-model="nuevo.modoJuego" style="width:100%">
            <option>survival</option>
            <option>creative</option>
            <option>adventure</option>
          </select>
        </label>

        <label>
          Administradores (OPs)
          <input v-model="nuevo.administradores" placeholder="Safu,Pepe (separados por comas)" style="width:100%" />
        </label>

        <label>
          URL del icono (opcional)
          <input v-model="nuevo.urlIcono" placeholder="https://..." style="width:100%" />
        </label>

        <label>
          Lista blanca (jugadores permitidos)
          <input v-model="nuevo.listaBlanca" placeholder="Safu,Pepe" :disabled="!nuevo.usarWhitelist" style="width:100%" />
        </label>

        <label style="display:flex; gap:6px; align-items:center">
          <input type="checkbox" v-model="nuevo.pvp" /> PvP activado
        </label>

        <label style="display:flex; gap:6px; align-items:center">
          <input type="checkbox" v-model="nuevo.modoOnline" /> Modo Premium (online)
        </label>

        <label style="display:flex; gap:6px; align-items:center">
          <input type="checkbox" v-model="nuevo.usarWhitelist" /> Usar lista blanca
        </label>

      </div>

      <div v-if="nuevo.tipo === 'FORGE' || nuevo.tipo === 'FABRIC'" style="grid-column: 1 / -1; margin-top:8px; border-top: 1px solid #444; padding-top:12px">
        <h3 style="margin:0 0 8px">Mods iniciales (se instalan antes del primer arranque)</h3>

        <div style="display:flex; gap:6px; margin-bottom:8px">
          <input v-model="busquedaMod" placeholder="Buscar mod en Modrinth (ej: sodium, create)" style="flex:1" @keyup.enter="buscarModsParaCrear" />
          <button type="button" @click="buscarModsParaCrear" :disabled="buscandoMods">
            {{ buscandoMods ? 'Buscando...' : 'Buscar' }}
          </button>
        </div>

        <div v-if="resultadosMods.length" style="max-height:200px; overflow-y:auto; border:1px solid #444; border-radius:4px; margin-bottom:8px">
          <div v-for="r in resultadosMods" :key="r.project_id"
               style="display:flex; align-items:center; gap:8px; padding:6px 8px; border-bottom:1px solid #333">
            <img v-if="r.icon_url" :src="r.icon_url" style="width:32px; height:32px; border-radius:4px" />
            <div style="flex:1; font-size:13px">
              <strong>{{ r.title }}</strong>
              <div style="color:#aaa; font-size:11px">{{ r.description }}</div>
              <div v-if="erroresCompatibilidad[r.project_id]" style="color:#f66; font-size:11px; margin-top:2px">
                ✕ {{ erroresCompatibilidad[r.project_id] }}
              </div>
            </div>
            <button type="button" @click="añadirMod(r)"
                    :disabled="nuevo.modIniciales.some(m => m.id === r.project_id) || verificando === r.project_id"
                    style="font-size:12px; padding:2px 8px; min-width:80px">
              <span v-if="verificando === r.project_id">Verificando...</span>
              <span v-else-if="nuevo.modIniciales.some(m => m.id === r.project_id)">✓ Añadido</span>
              <span v-else>+ Añadir</span>
            </button>
          </div>
        </div>

        <div v-if="dependenciasPendientes.length" style="background:#2a1f00; border:1px solid #f90; border-radius:6px; padding:10px; margin-bottom:8px">
          <strong style="color:#f90">⚠ Este mod requiere dependencias no añadidas:</strong>
          <span v-for="d in dependenciasPendientes" :key="d.id" style="display:inline-block; background:#333; border-radius:10px; padding:2px 8px; margin:4px 4px 0; font-size:12px">{{ d.titulo }}</span>
          <div style="margin-top:8px; display:flex; gap:8px">
            <button type="button" @click="añadirDependencias" style="font-size:12px; padding:3px 10px">Añadir todas</button>
            <button type="button" @click="dependenciasPendientes = []" style="font-size:12px; padding:3px 10px; background:transparent; border-color:#666; color:#aaa">Ignorar</button>
          </div>
        </div>

        <div v-if="nuevo.modIniciales.length">
          <p style="margin:0 0 4px; font-size:13px; color:#aaa">Mods que se instalarán ({{ nuevo.modIniciales.length }}):</p>
          <div v-for="m in nuevo.modIniciales" :key="m.id"
               style="display:inline-flex; align-items:center; gap:4px; background:#333; border-radius:12px; padding:2px 10px; margin:2px; font-size:12px">
            {{ m.titulo }}
            <span @click="quitarMod(m.id)" style="cursor:pointer; color:#f66; margin-left:4px">✕</span>
          </div>
        </div>
        <p v-else style="color:#666; font-size:12px; margin:0">Ningún mod seleccionado. Puedes añadirlos después también.</p>
      </div>

      <button type="submit" :disabled="cargando" style="width:100%; margin-top:12px; padding:10px">
        {{ cargando ? 'Creando servidor...' : 'Crear servidor' }}
      </button>
    </form>
    <p v-if="error" class="error">{{ error }}</p>
  </div>

  <div class="tarjeta">
    <h2>Servidores ({{ servidores.length }})</h2>
    <table v-if="servidores.length">
      <thead>
        <tr><th>Nombre</th><th>Versión</th><th>Tipo</th><th>Puerto</th><th>Estado</th><th>Acciones</th></tr>
      </thead>
      <tbody>
        <tr v-for="s in servidores" :key="s.id">
          <td>{{ s.nombre }}</td>
          <td>{{ s.version }}</td>
          <td>{{ s.tipo }}</td>
          <td>{{ s.puerto }}</td>
          <td>{{ s.estado }}</td>
          <td>
            <router-link :to="`/servidores/${s.id}`"><button>Gestionar</button></router-link>
            <button class="peligro" @click="eliminar(s.id)">Eliminar</button>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-else>No tienes servidores. Crea uno con el formulario de arriba.</p>
  </div>
</template>

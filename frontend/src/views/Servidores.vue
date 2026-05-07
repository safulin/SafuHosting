<script setup>
// Pantalla principal de la app: listado de "Mis servidores" + formulario para crear uno nuevo.
//
// Aqui pasan tres cosas grandes:
//  1. Cargamos del backend la lista de servidores del usuario logueado y las versiones de MC disponibles
//  2. El formulario de creacion: rellenas datos, le das a crear y se manda POST /api/servidores/crear
//  3. Si el tipo es FORGE/FABRIC aparece un buscador de mods para añadir mods iniciales
//     (se descargan ANTES de arrancar el contenedor para que el mundo se genere ya con ellos cargados)
//
// La logica de mods de aqui es similar a la de Mods.vue (servidores existentes) pero adaptada:
// la lista de mods se va construyendo en memoria (modIniciales del formulario) en lugar de en disco.

import { ref, onMounted } from 'vue'
import { api } from '../api.js'

const servidores = ref([])         // lista de servidores del usuario logueado
const versiones = ref([])          // versiones de MC disponibles para el desplegable
const error = ref('')
const cargando = ref(false)        // controla el spinner del boton "Crear servidor" mientras se procesa

// Formulario de creación con valores por defecto. Estos campos se mandan tal cual al backend en el POST /crear,
// que los mapea al objeto Servidor (mas el modIniciales que es @Transient y no se guarda en BD)
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
  modIniciales: []  // IDs de Modrinth a instalar antes del primer arranque
})

// --- Logica del buscador de mods dentro del formulario de creacion ---
// Solo aparece si el tipo es FORGE o FABRIC (VANILLA no soporta mods).
// El usuario busca, le sale una lista de Modrinth, le da a "+ Añadir" y verificamos compatibilidad
// antes de meterlo en modIniciales. Si tiene dependencias requeridas, las ofrecemos añadir tambien.

const busquedaMod = ref('')
const resultadosMods = ref([])
const buscandoMods = ref(false)

async function buscarModsParaCrear() {
  if (!busquedaMod.value.trim()) return
  buscandoMods.value = true
  try {
    // El backend hace de proxy a Modrinth (evitamos CORS y centralizamos la API key si la pusieramos)
    const { data } = await api.get('/api/servidores/mods/buscar', { params: { query: busquedaMod.value } })
    resultadosMods.value = data.hits || []
  } finally {
    buscandoMods.value = false
  }
}

const verificando = ref(null)              // project_id del mod que se esta verificando ahora
const erroresCompatibilidad = ref({})      // { project_id: "motivo" } para mostrar errores junto a cada resultado
const dependenciasPendientes = ref([])     // deps del último mod añadido que aún no están en la lista

// Verifica un mod contra el backend y, si es compatible, lo añade a modIniciales.
// Devuelve las dependencias requeridas para que el llamador decida si las añade tambien.
async function verificarYAñadir(modId, titulo) {
  const { data } = await api.get('/api/servidores/mods/verificar', {
    params: { modId, tipo: nuevo.value.tipo, version: nuevo.value.version }
  })
  if (!data.compatible) throw new Error(data.motivo)
  if (!nuevo.value.modIniciales.some(m => m.id === modId)) {
    // Si es un modpack lo marcamos en la etiqueta para que se vea claro
    const etiqueta = data.modpack ? `${titulo} (modpack)` : titulo
    nuevo.value.modIniciales.push({ id: modId, titulo: etiqueta })
  }
  return data.dependencias || []
}

// Handler del boton "+ Añadir" de cada resultado de busqueda
async function añadirMod(mod) {
  // Si ya esta añadido no hacemos nada (botones duplicados)
  if (nuevo.value.modIniciales.some(m => m.id === mod.project_id)) return

  verificando.value = mod.project_id
  delete erroresCompatibilidad.value[mod.project_id]
  dependenciasPendientes.value = []

  try {
    const deps = await verificarYAñadir(mod.project_id, mod.title)
    // Filtramos las dependencias que el usuario aún no ha añadido (asi no le proponemos añadir lo que ya tiene)
    dependenciasPendientes.value = deps.filter(d => !nuevo.value.modIniciales.some(m => m.id === d.id))
  } catch (e) {
    erroresCompatibilidad.value[mod.project_id] = e.message || 'Error al verificar compatibilidad'
  } finally {
    verificando.value = null
  }
}

// Handler del boton "Añadir todas" del aviso de dependencias
async function añadirDependencias() {
  for (const dep of dependenciasPendientes.value) {
    try {
      await verificarYAñadir(dep.id, dep.titulo)
    } catch (e) {
      // Si una dependencia no es compatible la ignoramos (raro pero posible)
    }
  }
  dependenciasPendientes.value = []
}

function quitarMod(modId) {
  nuevo.value.modIniciales = nuevo.value.modIniciales.filter(m => m.id !== modId)
}

// --- Carga inicial: lista de servidores y versiones disponibles ---

async function cargar() {
  try {
    // GET /api/servidores/todos → solo los servidores del usuario logueado (lo filtra el backend por propietario)
    const { data } = await api.get('/api/servidores/todos')
    servidores.value = data
  } catch (e) {
    error.value = e.response?.data || 'Error al cargar servidores'
  }
}

async function cargarVersiones() {
  try {
    // El backend tiene una lista hardcoded de versiones que soporta itzg/minecraft-server
    const { data } = await api.get('/api/servidores/versiones')
    versiones.value = data
  } catch (e) {
    console.error('Error al cargar versiones', e)
    // Si falla la API, ponemos al menos LATEST para que el formulario sea usable
    versiones.value = ['LATEST']
  }
}

async function crear() {
  error.value = ''
  cargando.value = true
  try {
    // El backend espera modIniciales como array de strings (IDs de Modrinth), no como objetos.
    // Aqui hacemos el mapeo: { id, titulo } → solo id. El titulo era para mostrarlo en pantalla, no le sirve al backend.
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
    // El backend para el contenedor, lo borra de Docker y borra el registro de SQLite.
    // La carpeta C:/mc-servers/{nombre} se queda en disco por si quieres recuperar el mundo
    await api.delete(`/api/servidores/${id}`)
    await cargar()
  } catch (e) {
    error.value = e.response?.data || 'Error al eliminar'
  }
}

// Al entrar en la pantalla cargamos primero las versiones (para el desplegable) y luego la lista de servidores
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

      <!-- Sección de mods: solo visible para FORGE y FABRIC -->
      <div v-if="nuevo.tipo === 'FORGE' || nuevo.tipo === 'FABRIC'" style="grid-column: 1 / -1; margin-top:8px; border-top: 1px solid #444; padding-top:12px">
        <h3 style="margin:0 0 8px">Mods iniciales (se instalan antes del primer arranque)</h3>

        <div style="display:flex; gap:6px; margin-bottom:8px">
          <input v-model="busquedaMod" placeholder="Buscar mod en Modrinth (ej: sodium, create)" style="flex:1" @keyup.enter="buscarModsParaCrear" />
          <button type="button" @click="buscarModsParaCrear" :disabled="buscandoMods">
            {{ buscandoMods ? 'Buscando...' : 'Buscar' }}
          </button>
        </div>

        <!-- Resultados de búsqueda -->
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

        <!-- Aviso de dependencias requeridas -->
        <div v-if="dependenciasPendientes.length" style="background:#2a1f00; border:1px solid #f90; border-radius:6px; padding:10px; margin-bottom:8px">
          <strong style="color:#f90">⚠ Este mod requiere dependencias no añadidas:</strong>
          <span v-for="d in dependenciasPendientes" :key="d.id" style="display:inline-block; background:#333; border-radius:10px; padding:2px 8px; margin:4px 4px 0; font-size:12px">{{ d.titulo }}</span>
          <div style="margin-top:8px; display:flex; gap:8px">
            <button type="button" @click="añadirDependencias" style="font-size:12px; padding:3px 10px">Añadir todas</button>
            <button type="button" @click="dependenciasPendientes = []" style="font-size:12px; padding:3px 10px; background:transparent; border-color:#666; color:#aaa">Ignorar</button>
          </div>
        </div>

        <!-- Mods seleccionados -->
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

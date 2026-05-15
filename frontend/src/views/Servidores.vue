<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../api.js'

const servidores = ref([])
const versiones = ref([])
const ipPublica = ref('')
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
  levelType: '',
  modIniciales: []
})

// Mods que requieren level-type específico
const MOD_LEVEL_TYPES = {
  'skylands': 'skylands:skylands',
  'skylands genesis': 'skylands:skylands',
  'skyland': 'skylands:skylands',
  'sky villages': 'skyland:skyland',
}

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
    // Autodetectar level-type según el nombre del mod
    const tituloLower = mod.title.toLowerCase()
    for (const [clave, levelType] of Object.entries(MOD_LEVEL_TYPES)) {
      if (tituloLower.includes(clave)) {
        nuevo.value.levelType = levelType
        break
      }
    }
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

async function cargarIpPublica() {
  try {
    const { data } = await api.get('/api/servidores/ip-publica')
    ipPublica.value = data.ip || ''
    if (ipPublica.value && ipPublica.value.includes('.duckdns.org')) {
      setTimeout(cargarIpPublica, 5000)
    }
  } catch (e) {
    ipPublica.value = ''
  }
}

async function copiarIP(texto) {
  try {
    await navigator.clipboard.writeText(texto)
  } catch (e) {}
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
  await Promise.all([cargar(), cargarIpPublica()])
})
</script>

<template>
  <h1 style="color:var(--mc-text-light); text-shadow:3px 3px 0 rgba(0,0,0,0.4); margin-bottom:20px;">Mis servidores</h1>

  <div class="tarjeta">
    <h2 style="margin-bottom:18px;">Crear servidor</h2>
    <form @submit.prevent="crear">
      <div style="display:grid; grid-template-columns:1fr 1fr; gap:14px;">
        <div>
          <label>Nombre del servidor</label>
          <input v-model="nuevo.nombre" placeholder="MiMundo" required />
        </div>
        <div>
          <label>Versión de Minecraft</label>
          <select v-model="nuevo.version">
            <option v-for="v in versiones" :key="v" :value="v">{{ v }}</option>
          </select>
        </div>
        <div>
          <label>Tipo de servidor</label>
          <select v-model="nuevo.tipo">
            <option value="VANILLA">VANILLA (sin mods)</option>
            <option value="FORGE">FORGE (mods)</option>
            <option value="FABRIC">FABRIC (mods)</option>
          </select>
        </div>
        <div>
          <label>Dificultad</label>
          <select v-model="nuevo.dificultad">
            <option>peaceful</option>
            <option>easy</option>
            <option>normal</option>
            <option>hard</option>
          </select>
        </div>
        <div>
          <label>Modo de juego</label>
          <select v-model="nuevo.modoJuego">
            <option>survival</option>
            <option>creative</option>
            <option>adventure</option>
          </select>
        </div>
        <div>
          <label>Administradores (OPs)</label>
          <input v-model="nuevo.administradores" placeholder="Safu,Pepe (separados por comas)" />
        </div>
        <div>
          <label>URL del icono (opcional)</label>
          <input v-model="nuevo.urlIcono" placeholder="https://..." />
        </div>
        <div>
          <label>Lista blanca (jugadores)</label>
          <input v-model="nuevo.listaBlanca" placeholder="Safu,Pepe" :disabled="!nuevo.usarWhitelist" />
        </div>
        <label style="display:flex; gap:8px; align-items:center; cursor:pointer;">
          <input type="checkbox" v-model="nuevo.pvp" /> PvP activado
        </label>
        <label style="display:flex; gap:8px; align-items:center; cursor:pointer;">
          <input type="checkbox" v-model="nuevo.modoOnline" /> Modo Premium (online)
        </label>
        <label style="display:flex; gap:8px; align-items:center; cursor:pointer;">
          <input type="checkbox" v-model="nuevo.usarWhitelist" /> Usar lista blanca
        </label>
      </div>

      <div v-if="nuevo.tipo === 'FORGE' || nuevo.tipo === 'FABRIC'"
           style="margin-top:18px; border-top:2px solid var(--mc-tan-dark); padding-top:16px;">
        <div style="margin-bottom:16px;">
          <label>Level Type <span style="font-size:11px; color:var(--mc-text-muted); font-weight:normal;">(se rellena automáticamente con algunos mods)</span></label>
          <input v-model="nuevo.levelType" placeholder="Ej: skylands:skylands (dejar vacío para mundo normal)" />
        </div>
        <h3 style="margin-bottom:12px;">Mods iniciales</h3>
        <div style="display:flex; gap:8px; margin-bottom:10px;">
          <input v-model="busquedaMod" placeholder="Buscar mod en Modrinth (ej: sodium, create)" style="flex:1; margin:0;" @keyup.enter="buscarModsParaCrear" />
          <button type="button" @click="buscarModsParaCrear" :disabled="buscandoMods" style="margin:0; white-space:nowrap;">
            {{ buscandoMods ? 'Buscando...' : 'Buscar' }}
          </button>
        </div>
        <div v-if="resultadosMods.length"
             style="max-height:220px; overflow-y:auto; border:2px solid var(--mc-border-dark); border-radius:4px; margin-bottom:10px; background:white;">
          <div v-for="r in resultadosMods" :key="r.project_id"
               style="display:flex; align-items:center; gap:10px; padding:8px 10px; border-bottom:1px solid #eee;">
            <img v-if="r.icon_url" :src="r.icon_url" style="width:36px; height:36px; border-radius:4px; flex-shrink:0;" />
            <div style="flex:1; font-size:13px; color:var(--mc-text); min-width:0;">
              <strong>{{ r.title }}</strong>
              <div style="color:#888; font-size:11px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap;">{{ r.description }}</div>
              <div v-if="erroresCompatibilidad[r.project_id]" style="color:var(--mc-red); font-size:11px; margin-top:2px;">
                ✕ {{ erroresCompatibilidad[r.project_id] }}
              </div>
            </div>
            <button type="button" @click="añadirMod(r)"
                    :disabled="nuevo.modIniciales.some(m => m.id === r.project_id) || verificando === r.project_id"
                    style="font-size:12px; padding:4px 10px; min-width:90px; margin:0; flex-shrink:0;">
              <span v-if="verificando === r.project_id">Verificando...</span>
              <span v-else-if="nuevo.modIniciales.some(m => m.id === r.project_id)">✓ Añadido</span>
              <span v-else>+ Añadir</span>
            </button>
          </div>
        </div>
        <div v-if="dependenciasPendientes.length"
             style="background:#fff8e8; border:2px solid #c8851a; border-radius:4px; padding:12px; margin-bottom:10px;">
          <strong style="color:#c8851a;">⚠ Este mod requiere dependencias:</strong>
          <div style="margin:8px 0; display:flex; flex-wrap:wrap; gap:6px;">
            <span v-for="d in dependenciasPendientes" :key="d.id"
                  style="background:#f0e0b0; border-radius:12px; padding:3px 10px; font-size:12px; color:var(--mc-text);">
              {{ d.titulo }}
            </span>
          </div>
          <div style="display:flex; gap:8px;">
            <button type="button" @click="añadirDependencias" style="font-size:12px; padding:4px 12px; margin:0;">Añadir todas</button>
            <button type="button" @click="dependenciasPendientes = []"
                    style="font-size:12px; padding:4px 12px; margin:0; background:var(--mc-tan-dark); border-bottom-color:var(--mc-border-dark); color:var(--mc-text);">
              Ignorar
            </button>
          </div>
        </div>
        <div v-if="nuevo.modIniciales.length" style="display:flex; flex-wrap:wrap; gap:6px;">
          <div v-for="m in nuevo.modIniciales" :key="m.id"
               style="display:inline-flex; align-items:center; gap:6px; background:var(--mc-tan-dark); border-radius:12px; padding:4px 12px; font-size:12px; color:var(--mc-text);">
            {{ m.titulo }}
            <span @click="quitarMod(m.id)" style="cursor:pointer; color:var(--mc-red); font-weight:bold; line-height:1;">✕</span>
          </div>
        </div>
        <p v-else style="color:var(--mc-text-muted); font-size:12px; margin:0;">
          Ningún mod seleccionado. Puedes añadirlos después también.
        </p>
      </div>

      <button type="submit" :disabled="cargando" style="width:100%; margin-top:16px; padding:12px; font-size:14px;">
        {{ cargando ? 'Creando servidor...' : 'Crear servidor' }}
      </button>
    </form>
    <p v-if="error" class="error" style="margin-top:10px;">{{ error }}</p>
  </div>

  <div class="tarjeta">
    <h2 style="margin-bottom:18px;">Servidores ({{ servidores.length }})</h2>
    <div v-if="servidores.length"
         style="display:grid; grid-template-columns:repeat(auto-fill, minmax(300px, 1fr)); gap:14px;">
      <div v-for="s in servidores" :key="s.id"
           style="background:var(--mc-tan-light); border:2px solid var(--mc-border-dark); border-top-color:white; border-left-color:white; border-radius:4px; padding:16px; box-shadow:3px 3px 0 rgba(0,0,0,0.15);">
        <div style="display:flex; align-items:flex-start; gap:12px; margin-bottom:12px;">
          <img v-if="s.urlIcono" :src="s.urlIcono"
               style="width:48px; height:48px; border-radius:4px; border:2px solid var(--mc-border-dark); flex-shrink:0;" />
          <div v-else
               style="width:48px; height:48px; border-radius:4px; border:2px solid var(--mc-border-dark); flex-shrink:0; background:var(--mc-tan-dark); display:flex; align-items:center; justify-content:center; font-size:22px;">
            🌍
          </div>
          <div style="min-width:0; flex:1;">
            <div style="font-family:'Press Start 2P', monospace; font-size:11px; color:var(--mc-text); margin-bottom:4px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap;">
              {{ s.nombre }}
            </div>
            <div style="font-size:12px; color:var(--mc-text-muted);">{{ s.version }} · {{ s.tipo }}</div>
            <div v-if="ipPublica && s.puerto"
                 @click="copiarIP(`${ipPublica}:${s.puerto}`)"
                 title="Clic para copiar"
                 style="font-size:12px; color:var(--mc-text); font-family:'Courier New',monospace; background:var(--mc-tan-dark); border:1px solid var(--mc-border-dark); border-radius:3px; padding:2px 6px; margin-top:4px; cursor:pointer; display:inline-flex; align-items:center; gap:4px;">
              📋 {{ ipPublica }}:{{ s.puerto }}
            </div>
            <div v-else style="font-size:12px; color:var(--mc-text-muted);">Puerto: {{ s.puerto }}</div>
          </div>
        </div>
        <div style="display:flex; align-items:center; gap:6px; margin-bottom:12px;">
          <span style="display:inline-block; width:10px; height:10px; border-radius:50%;"
                :style="{ background: s.estado?.toLowerCase().includes('run') || s.estado?.toLowerCase().includes('activo') ? '#5AAB37' : s.estado?.toLowerCase().includes('stop') || s.estado?.toLowerCase().includes('parado') ? '#AA2222' : '#c8851a' }">
          </span>
          <span style="font-size:12px; font-weight:600; color:var(--mc-text);">{{ s.estado || 'Desconocido' }}</span>
        </div>
        <div style="display:flex; gap:6px;">
          <router-link :to="`/servidores/${s.id}`" style="flex:1;">
            <button style="width:100%; margin:0; font-size:12px; padding:7px;">Gestionar</button>
          </router-link>
          <button class="peligro" @click="eliminar(s.id)" style="margin:0; font-size:12px; padding:7px;">Eliminar</button>
        </div>
      </div>
    </div>
    <p v-else style="color:var(--mc-text-muted); text-align:center; padding:30px 0; font-size:14px;">
      No tienes servidores aún. ¡Crea uno con el formulario de arriba!
    </p>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../api.js'

const servidores = ref([])
const versiones = ref([])
const error = ref('')
const cargando = ref(false)

// Formulario de creación con valores por defecto
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
  urlIcono: ''
})

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
    // Si falla la API, ponemos al menos LATEST para que el formulario sea usable
    versiones.value = ['LATEST']
  }
}

async function crear() {
  error.value = ''
  cargando.value = true
  try {
    await api.post('/api/servidores/crear', nuevo.value)
    nuevo.value.nombre = ''
    nuevo.value.listaBlanca = ''
    nuevo.value.administradores = ''
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

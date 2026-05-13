<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../api.js'

const props = defineProps({ id: { type: [String, Number], required: true } })

const lista = ref([])
const nuevo = ref('')
const error = ref('')

async function cargar() {
  const { data } = await api.get(`/api/servidores/${props.id}/whitelist`)
  lista.value = data
}

async function añadir() {
  if (!nuevo.value.trim()) return
  try {
    await api.post(`/api/servidores/${props.id}/whitelist/${nuevo.value}`)
    nuevo.value = ''
    await cargar()
  } catch (e) {
    error.value = e.response?.data || 'Error'
  }
}

async function quitar(jugador) {
  try {
    await api.delete(`/api/servidores/${props.id}/whitelist/${jugador}`)
    await cargar()
  } catch (e) {
    error.value = e.response?.data || 'Error'
  }
}

onMounted(cargar)
</script>

<template>
  <div class="tarjeta">
    <h2>Whitelist</h2>
    <form @submit.prevent="añadir" style="display:flex; gap:6px">
      <input v-model="nuevo" placeholder="Nombre del jugador" style="flex:1" />
      <button type="submit">Añadir</button>
    </form>
    <ul style="margin-top:10px; list-style:none">
      <li v-for="j in lista" :key="j" style="padding:4px 0; display:flex; justify-content:space-between; align-items:center; border-bottom:1px solid #444">
        <span>{{ j }}</span>
        <button class="peligro" @click="quitar(j)">Quitar</button>
      </li>
    </ul>
    <p v-if="!lista.length" style="margin-top:10px; color:#888">Whitelist vacía</p>
    <p v-if="error" class="error">{{ error }}</p>
  </div>
</template>

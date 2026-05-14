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
    <h2 style="margin-bottom:16px;">Whitelist</h2>
    <form @submit.prevent="añadir" style="display:flex; gap:8px; margin-bottom:14px;">
      <input v-model="nuevo" placeholder="Nombre del jugador" style="flex:1; margin:0;" />
      <button type="submit" style="margin:0; white-space:nowrap;">+ Añadir</button>
    </form>
    <div v-if="lista.length"
         style="border:2px solid var(--mc-border-dark); border-radius:4px; overflow:hidden; background:var(--mc-tan-light);">
      <div v-for="j in lista" :key="j"
           style="display:flex; justify-content:space-between; align-items:center; padding:10px 14px; border-bottom:1px solid var(--mc-tan-dark);">
        <div style="display:flex; align-items:center; gap:10px;">
          <img :src="`https://mc-heads.net/avatar/${j}/32`"
               style="width:32px; height:32px; border-radius:3px; border:1px solid var(--mc-border-dark); image-rendering:pixelated;"
               :alt="j"
               @error="$event.target.style.display='none'" />
          <span style="font-weight:600; font-size:14px;">{{ j }}</span>
        </div>
        <button class="peligro" @click="quitar(j)" style="font-size:12px; padding:5px 10px; margin:0;">Quitar</button>
      </div>
    </div>
    <p v-if="!lista.length" style="color:var(--mc-text-muted); text-align:center; padding:20px; font-size:13px;">
      La whitelist está vacía
    </p>
    <p v-if="error" class="error" style="margin-top:10px;">{{ error }}</p>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'

type Status = 'checking' | 'ok' | 'unreachable'

const status = ref<Status>('checking')

async function checkHealth(): Promise<void> {
  status.value = 'checking'
  try {
    const res = await fetch('/api/health')
    status.value = res.ok ? 'ok' : 'unreachable'
  } catch {
    status.value = 'unreachable'
  }
}

onMounted(checkHealth)
</script>

<template>
  <main>
    <h1>Workstream</h1>
    <div class="status" :class="status">
      <span v-if="status === 'checking'">Checking backend…</span>
      <span v-else-if="status === 'ok'">Backend: OK</span>
      <span v-else>Backend: Unreachable</span>
    </div>
    <button @click="checkHealth">Refresh</button>
  </main>
</template>

<style scoped>
main {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1.5rem;
  padding: 4rem 2rem;
  font-family: sans-serif;
}

.status {
  padding: 0.75rem 1.5rem;
  border-radius: 8px;
  font-size: 1.1rem;
  font-weight: 600;
}

.status.checking     { background: #e5e7eb; color: #374151; }
.status.ok           { background: #d1fae5; color: #065f46; }
.status.unreachable  { background: #fee2e2; color: #991b1b; }

button {
  padding: 0.5rem 1.25rem;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  background: white;
  cursor: pointer;
  font-size: 0.9rem;
}

button:hover { background: #f3f4f6; }
</style>

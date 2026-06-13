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
  <div class="page">
    <h1>Health</h1>
    <div class="status" :class="status">
      <span v-if="status === 'checking'">Checking backend…</span>
      <span v-else-if="status === 'ok'">Backend: OK</span>
      <span v-else>Backend: Unreachable</span>
    </div>
    <button @click="checkHealth">Refresh</button>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1.5rem;
  padding: 3rem 2rem;
}

.status {
  padding: 0.75rem 1.5rem;
  border-radius: 8px;
  font-size: 1rem;
  font-weight: 600;
}

.status.checking    { background: #f3f4f6; color: #374151; }
.status.ok          { background: #d1fae5; color: #065f46; }
.status.unreachable { background: #fee2e2; color: #991b1b; }
</style>

<script setup lang="ts">
defineProps<{
  label: string
  placeholder: string
  addLabel: string
}>()

const items = defineModel<string[]>({ required: true })

function add() { items.value.push('') }
function remove(i: number) { items.value.splice(i, 1) }
</script>

<template>
  <div class="field">
    <label>{{ label }}</label>
    <div v-if="items.length === 0" class="muted small">No {{ label.toLowerCase() }} yet.</div>
    <div v-for="(_, i) in items" :key="i" class="row">
      <input v-model="items[i]" required :placeholder="placeholder" />
      <button type="button" class="btn-remove" @click="remove(i)">✕</button>
    </div>
    <button type="button" class="btn-add" @click="add">{{ addLabel }}</button>
  </div>
</template>

<style scoped>
.field {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

label {
  font-size: 0.8rem;
  font-weight: 500;
  color: #374151;
}

input {
  flex: 1;
  padding: 0.45rem 0.65rem;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 0.875rem;
  background: white;
  box-sizing: border-box;
  font-family: inherit;
}

input:focus {
  outline: 2px solid #6366f1;
  outline-offset: -1px;
  border-color: transparent;
}

.row {
  display: flex;
  gap: 0.5rem;
  align-items: center;
  margin-bottom: 0.4rem;
}

.btn-remove {
  background: none;
  border: none;
  color: #9ca3af;
  cursor: pointer;
  font-size: 0.75rem;
  padding: 0.15rem 0.35rem;
}
.btn-remove:hover { color: #ef4444; }

.btn-add {
  background: none;
  border: 1px dashed #d1d5db;
  border-radius: 6px;
  color: #6b7280;
  font-size: 0.8rem;
  padding: 0.3rem 0.75rem;
  cursor: pointer;
  margin-top: 0.25rem;
  width: fit-content;
}
.btn-add:hover { border-color: #6b7280; color: #374151; }
</style>

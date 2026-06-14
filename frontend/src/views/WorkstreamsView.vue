<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { workstreamsApi } from '../api/workstreams'
import { useWorkstreamsSocket } from '../composables/useWorkstreamSocket'
import { PRIORITY_CLASS, STATUS_CLASS } from '../utils/badges'
import { formatDate } from '../utils/format'
import Badge from '../components/Badge.vue'
import type { CreateWorkstreamRequest, Workstream } from '../types/workstream'

const workstreams = ref<Workstream[]>([])
const loading = ref(true)
const loadError = ref<string | null>(null)
const { activeWorkstreamIds } = useWorkstreamsSocket({
  onWorkstreamUpdated: (updated) => {
    const i = workstreams.value.findIndex(ws => ws.id === updated.id)
    if (i >= 0) {
      workstreams.value[i] = { ...updated, active: activeWorkstreamIds.value.includes(updated.id) }
    } else {
      workstreams.value.unshift({ ...updated, active: false })
    }
  },
})

const showForm = ref(false)
const submitting = ref(false)
const submitError = ref<string | null>(null)
const form = ref<CreateWorkstreamRequest>({
  title: '',
  description: '',
  requester: '',
  priority: 'MEDIUM',
})

async function load() {
  loading.value = true
  loadError.value = null
  try {
    workstreams.value = await workstreamsApi.listWorkstreams()
    activeWorkstreamIds.value = workstreams.value.filter(ws => ws.active).map(ws => ws.id)
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : 'Failed to load'
  } finally {
    loading.value = false
  }
}

async function submit() {
  submitting.value = true
  submitError.value = null
  try {
    const created = await workstreamsApi.createWorkstream(form.value)
    workstreams.value.unshift(created)
    showForm.value = false
    form.value = { title: '', description: '', requester: '', priority: 'MEDIUM' }
  } catch (e) {
    submitError.value = e instanceof Error ? e.message : 'Failed to create'
  } finally {
    submitting.value = false
  }
}

function cancelForm() {
  showForm.value = false
  submitError.value = null
  form.value = { title: '', description: '', requester: '', priority: 'MEDIUM' }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1>Workstreams</h1>
      <button class="btn-primary" @click="showForm = true" v-if="!showForm">
        + New Workstream
      </button>
    </div>

    <!-- Create form -->
    <form v-if="showForm" class="create-form" @submit.prevent="submit">
      <h2>New Workstream</h2>

      <div class="field">
        <label for="title">Title</label>
        <input id="title" v-model="form.title" required placeholder="e.g. Add JWT authentication" />
      </div>

      <div class="field">
        <label for="description">Description</label>
        <textarea id="description" v-model="form.description" required rows="2"
          placeholder="What needs to be done?" />
      </div>

      <div class="field-row">
        <div class="field">
          <label for="requester">Requester</label>
          <input id="requester" v-model="form.requester" required placeholder="e.g. alice" />
        </div>
        <div class="field">
          <label for="priority">Priority</label>
          <select id="priority" v-model="form.priority">
            <option value="LOW">Low</option>
            <option value="MEDIUM">Medium</option>
            <option value="HIGH">High</option>
          </select>
        </div>
      </div>

      <p v-if="submitError" class="error">{{ submitError }}</p>

      <div class="form-actions">
        <button type="button" class="btn-ghost" @click="cancelForm">Cancel</button>
        <button type="submit" class="btn-primary" :disabled="submitting">
          {{ submitting ? 'Creating…' : 'Create' }}
        </button>
      </div>
    </form>

    <!-- States -->
    <div v-if="loading" class="state-msg">Loading…</div>
    <div v-else-if="loadError" class="state-msg error">{{ loadError }}</div>
    <div v-else-if="workstreams.length === 0 && !showForm" class="state-msg muted">
      No workstreams yet. Create one to get started.
    </div>

    <!-- Table -->
    <table v-else-if="workstreams.length > 0">
      <thead>
        <tr>
          <th>Title</th>
          <th></th>
          <th>Requester</th>
          <th>Priority</th>
          <th>Status</th>
          <th>Created</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="ws in workstreams" :key="ws.id" class="clickable" @click="$router.push(`/workstreams/${ws.id}`)">
          <td class="cell-title">{{ ws.title }}</td>
          <td class="cell-presence">
            <Badge v-if="activeWorkstreamIds.includes(ws.id)" variant="badge-active" label="● Active" />
          </td>
          <td>{{ ws.requester }}</td>
          <td><Badge :variant="PRIORITY_CLASS[ws.priority]" :label="ws.priority" /></td>
          <td><Badge :variant="STATUS_CLASS[ws.status]" :label="ws.status" /></td>
          <td class="cell-date">{{ formatDate(ws.createdAt) }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.page {
  padding: 2rem;
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

h1 { font-size: 1.5rem; font-weight: 600; margin: 0; }
h2 { font-size: 1.1rem; font-weight: 600; margin: 0 0 1rem; }

/* Form */
.create-form {
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 1.5rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
  background: #f9fafb;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  flex: 1;
}

.field-row {
  display: flex;
  gap: 1rem;
}

label {
  font-size: 0.8rem;
  font-weight: 500;
  color: #374151;
}

input, textarea, select {
  padding: 0.45rem 0.65rem;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 0.9rem;
  background: white;
  width: 100%;
  box-sizing: border-box;
  font-family: inherit;
}

input:focus, textarea:focus, select:focus {
  outline: 2px solid #6366f1;
  outline-offset: -1px;
  border-color: transparent;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
}

/* Table */
table {
  width: auto;
  border-collapse: collapse;
  font-size: 0.875rem;
}

th {
  text-align: left;
  padding: 0.5rem 0.75rem;
  border-bottom: 2px solid #e5e7eb;
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: #6b7280;
  white-space: nowrap;
}

td {
  padding: 0.55rem 0.75rem;
  border-bottom: 1px solid #f3f4f6;
  color: #111827;
  white-space: nowrap;
}

tr:last-child td { border-bottom: none; }
tr.clickable { cursor: pointer; }
tr.clickable:hover td { background: #f9fafb; }

.cell-title { font-weight: 500; white-space: normal; min-width: 200px; }
.cell-date  { color: #9ca3af; }
</style>

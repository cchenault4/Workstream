<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { workstreamsApi } from '../api/workstreams'
import type { CreateWorkstreamRequest, Priority, Workstream } from '../types/workstream'

const workstreams = ref<Workstream[]>([])
const loading = ref(true)
const loadError = ref<string | null>(null)
const activeWorkstreamIds = ref<string[]>([])

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
    workstreams.value = await workstreamsApi.list()
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
    const created = await workstreamsApi.create(form.value)
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

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
}

const PRIORITY_CLASS: Record<Priority, string> = {
  LOW: 'badge-low',
  MEDIUM: 'badge-medium',
  HIGH: 'badge-high',
}

const STATUS_CLASS: Record<Workstream['status'], string> = {
  NEW: 'badge-new',
  PLANNING: 'badge-planning',
  EXECUTING: 'badge-executing',
  REVIEWING: 'badge-reviewing',
  VERIFIED: 'badge-verified',
  BLOCKED: 'badge-blocked',
}

let presenceSocket: WebSocket | null = null

onMounted(() => {
  load()
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  presenceSocket = new WebSocket(`${protocol}//${window.location.host}/ws`)
  presenceSocket.onopen = () => {
    presenceSocket!.send(JSON.stringify({ type: 'presence:subscribe' }))
  }
  presenceSocket.onmessage = (ev) => {
    try {
      const msg = JSON.parse(ev.data as string)
      if (msg.type === 'workstream:presence') {
        const { workstreamId, active } = msg.data as { workstreamId: string; active: boolean }
        if (active) {
          if (!activeWorkstreamIds.value.includes(workstreamId)) {
            activeWorkstreamIds.value = [...activeWorkstreamIds.value, workstreamId]
          }
        } else {
          activeWorkstreamIds.value = activeWorkstreamIds.value.filter(id => id !== workstreamId)
        }
      }
    } catch { /* ignore malformed frames */ }
  }
})

onUnmounted(() => {
  presenceSocket?.close()
})
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
            <span v-if="activeWorkstreamIds.includes(ws.id)" class="badge badge-active">● Active</span>
          </td>
          <td>{{ ws.requester }}</td>
          <td><span class="badge" :class="PRIORITY_CLASS[ws.priority]">{{ ws.priority }}</span></td>
          <td><span class="badge" :class="STATUS_CLASS[ws.status]">{{ ws.status }}</span></td>
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

/* Buttons */
.btn-primary {
  padding: 0.45rem 1rem;
  background: #4f46e5;
  color: white;
  border: none;
  border-radius: 6px;
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
}

.btn-primary:hover:not(:disabled) { background: #4338ca; }
.btn-primary:disabled { opacity: 0.6; cursor: default; }

.btn-ghost {
  padding: 0.45rem 1rem;
  background: transparent;
  color: #374151;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 0.875rem;
  cursor: pointer;
}

.btn-ghost:hover { background: #f3f4f6; }

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

.badge-active { background: #d1fae5; color: #065f46; }

/* Badges */
.badge {
  display: inline-block;
  padding: 0.2rem 0.55rem;
  border-radius: 4px;
  font-size: 0.7rem;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  white-space: nowrap;
}

.badge-low      { background: #f3f4f6; color: #6b7280; }
.badge-medium   { background: #fef3c7; color: #92400e; }
.badge-high     { background: #fee2e2; color: #991b1b; }

.badge-new       { background: #dbeafe; color: #1e40af; }
.badge-planning  { background: #ede9fe; color: #5b21b6; }
.badge-executing { background: #ffedd5; color: #9a3412; }
.badge-reviewing { background: #fef9c3; color: #854d0e; }
.badge-verified  { background: #d1fae5; color: #065f46; }
.badge-blocked   { background: #fee2e2; color: #991b1b; }

/* Misc */
.state-msg { color: #6b7280; text-align: center; padding: 2rem 0; font-size: 0.9rem; }
.error     { color: #991b1b; }
.muted     { color: #9ca3af; }
</style>

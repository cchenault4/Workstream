<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { workstreamsApi } from '../api/workstreams'
import { useWorkstreamSocket } from '../composables/useWorkstreamSocket'
import type {
  ActivityEvent,
  ActivityType,
  CreateActivityEventRequest,
  PhaseStatus,
  Plan,
  ReadinessState,
  UpsertPlanRequest,
  Workstream,
  WorkstreamStatus,
} from '../types/workstream'

const route = useRoute()
const router = useRouter()
const id = route.params.id as string

// ── Data ──────────────────────────────────────────────────────────────────────
const workstream  = ref<Workstream | null>(null)
const plan        = ref<Plan | null>(null)
const readiness   = ref<ReadinessState | null>(null)
const activities  = ref<ActivityEvent[]>([])
const loading     = ref(true)
const loadError   = ref<string | null>(null)

// ── Status update ─────────────────────────────────────────────────────────────
const statusSaving = ref(false)

async function updateStatus(status: WorkstreamStatus) {
  if (!workstream.value) return
  statusSaving.value = true
  try {
    workstream.value = await workstreamsApi.update(id, { status })
  } finally {
    statusSaving.value = false
  }
}

// ── Plan form ─────────────────────────────────────────────────────────────────
const showPlanForm  = ref(false)
const planSaving    = ref(false)
const planFormError = ref<string | null>(null)

function makePlanForm(existing?: Plan): UpsertPlanRequest {
  return {
    goal: existing?.goal ?? '',
    nonGoals: existing?.nonGoals ?? [],
    assumptions: existing?.assumptions ?? [],
    openQuestions: existing?.openQuestions ?? [],
    phases: existing?.phases ? existing.phases.map(p => ({ ...p })) : [],
    verificationPlan: existing?.verificationPlan ?? [],
  }
}

const planForm = ref<UpsertPlanRequest>(makePlanForm())

function openPlanForm() {
  planForm.value = makePlanForm(plan.value ?? undefined)
  planFormError.value = null
  showPlanForm.value = true
}

function addPhase() {
  planForm.value.phases.push({
    id: Math.random().toString(36).slice(2, 10),
    name: '',
    objective: '',
    status: 'PENDING',
  })
}

function removePhase(index: number) {
  planForm.value.phases.splice(index, 1)
}

async function savePlan() {
  planSaving.value = true
  planFormError.value = null
  try {
    plan.value = await workstreamsApi.upsertPlan(id, planForm.value)
  } catch (e) {
    planFormError.value = e instanceof Error ? e.message : 'Failed to save plan'
    planSaving.value = false
    return
  }
  // Plan saved — close form before awaiting readiness so the UI unblocks immediately.
  planSaving.value = false
  showPlanForm.value = false
  readiness.value = await workstreamsApi.getReadiness(id).catch(() => null)
}

// ── Activity form ─────────────────────────────────────────────────────────────
const showActivityForm  = ref(false)
const activitySaving    = ref(false)
const activityFormError = ref<string | null>(null)
const activityForm = ref<CreateActivityEventRequest>({
  agentName: '',
  type: 'PLANNING',
  message: '',
})

async function addActivity() {
  activitySaving.value = true
  activityFormError.value = null
  try {
    const event = await workstreamsApi.addActivity(id, activityForm.value)
    activities.value.unshift(event)
  } catch (e) {
    activityFormError.value = e instanceof Error ? e.message : 'Failed to add event'
    activitySaving.value = false
    return
  }
  // Event saved — reset form and close before refreshing readiness.
  activitySaving.value = false
  activityForm.value = { agentName: '', type: 'PLANNING', message: '' }
  showActivityForm.value = false
  if (plan.value) readiness.value = await workstreamsApi.getReadiness(id).catch(() => null)
}

// ── Load ──────────────────────────────────────────────────────────────────────
onMounted(async () => {
  try {
    workstream.value = await workstreamsApi.get(id)
    const [planRes, activitiesRes] = await Promise.allSettled([
      workstreamsApi.getPlan(id),
      workstreamsApi.getActivity(id),
    ])
    if (planRes.status === 'fulfilled') {
      plan.value = planRes.value
      readiness.value = await workstreamsApi.getReadiness(id).catch(() => null)
    }
    if (activitiesRes.status === 'fulfilled') {
      // Show newest first
      activities.value = [...activitiesRes.value].reverse()
    }
  } catch {
    loadError.value = 'Workstream not found'
  } finally {
    loading.value = false
  }
})

// ── Real-time ─────────────────────────────────────────────────────────────────
const { connected } = useWorkstreamSocket(id, {
  onActivity: (event) => {
    // Dedup: the user's own POST already adds the event immediately.
    if (!activities.value.some(a => a.id === event.id)) {
      activities.value.unshift(event)
    }
    // A VERIFICATION or REVIEW event may unlock readiness gates.
    if (plan.value && (event.type === 'VERIFICATION' || event.type === 'REVIEW')) {
      workstreamsApi.getReadiness(id).then(r => { readiness.value = r }).catch(() => {})
    }
  },
  onWorkstreamUpdated: (ws) => {
    workstream.value = ws
  },
  onPlanUpdated: (updatedPlan) => {
    plan.value = updatedPlan
    workstreamsApi.getReadiness(id).then(r => { readiness.value = r }).catch(() => {})
  },
})

// ── Helpers ───────────────────────────────────────────────────────────────────
const STATUSES: WorkstreamStatus[] = ['NEW', 'PLANNING', 'EXECUTING', 'REVIEWING', 'VERIFIED', 'BLOCKED']
const ACTIVITY_TYPES: ActivityType[] = ['CONTEXT_DISCOVERY', 'PLANNING', 'IMPLEMENTATION', 'REVIEW', 'VERIFICATION', 'HANDOFF']
const PHASE_STATUSES: PhaseStatus[] = ['PENDING', 'IN_PROGRESS', 'COMPLETE', 'BLOCKED']

const PRIORITY_CLASS: Record<string, string> = {
  LOW: 'badge-low', MEDIUM: 'badge-medium', HIGH: 'badge-high',
}
const STATUS_CLASS: Record<string, string> = {
  NEW: 'badge-new', PLANNING: 'badge-planning', EXECUTING: 'badge-executing',
  REVIEWING: 'badge-reviewing', VERIFIED: 'badge-verified', BLOCKED: 'badge-blocked',
}
const PHASE_CLASS: Record<PhaseStatus, string> = {
  PENDING: 'badge-low', IN_PROGRESS: 'badge-executing', COMPLETE: 'badge-verified', BLOCKED: 'badge-blocked',
}
const ACTIVITY_CLASS: Record<ActivityType, string> = {
  CONTEXT_DISCOVERY: 'badge-new', PLANNING: 'badge-planning', IMPLEMENTATION: 'badge-executing',
  REVIEW: 'badge-reviewing', VERIFICATION: 'badge-verified', HANDOFF: 'badge-low',
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
}

function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString('en-US', { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' })
}
</script>

<template>
  <div class="page">

    <!-- Loading / error -->
    <div v-if="loading"   class="state-msg">Loading…</div>
    <div v-else-if="loadError" class="state-msg error">{{ loadError }}</div>

    <template v-else-if="workstream">

      <!-- Header -->
      <div class="ws-header">
        <button class="btn-back" @click="router.push('/workstreams')">← Workstreams</button>
        <div class="ws-title-row">
          <h1>{{ workstream.title }}</h1>
          <span class="badge" :class="PRIORITY_CLASS[workstream.priority]">{{ workstream.priority }}</span>
          <select
            class="status-select"
            :class="STATUS_CLASS[workstream.status]"
            :value="workstream.status"
            :disabled="statusSaving"
            @change="updateStatus(($event.target as HTMLSelectElement).value as WorkstreamStatus)"
          >
            <option v-for="s in STATUSES" :key="s" :value="s">{{ s }}</option>
          </select>
        </div>
        <p class="ws-meta">
          {{ workstream.description }}
          <span class="sep">·</span>
          Requested by <strong>{{ workstream.requester }}</strong>
          <span class="sep">·</span>
          Created {{ formatDate(workstream.createdAt) }}
        </p>
      </div>

      <!-- ── Plan ─────────────────────────────────────────────────────────── -->
      <section>
        <div class="section-header">
          <h2>Implementation Plan</h2>
          <button class="btn-secondary" @click="openPlanForm">
            {{ plan ? 'Edit Plan' : 'Add Plan' }}
          </button>
        </div>

        <!-- Plan form -->
        <form v-if="showPlanForm" class="card form-card" @submit.prevent="savePlan">
          <div class="field">
            <label>Goal</label>
            <input v-model="planForm.goal" required placeholder="What are we trying to achieve?" />
          </div>

          <div class="field">
            <label>Phases</label>
            <div v-if="planForm.phases.length === 0" class="muted small">No phases yet.</div>
            <div v-for="(phase, i) in planForm.phases" :key="phase.id" class="phase-card">
              <div class="phase-card-header">
                <span class="phase-num">Phase {{ i + 1 }}</span>
                <button type="button" class="btn-remove" @click="removePhase(i)">✕ Remove</button>
              </div>
              <div class="field-row">
                <div class="field">
                  <label>Name</label>
                  <input v-model="phase.name" required placeholder="e.g. Implementation" />
                </div>
                <div class="field field-status">
                  <label>Status</label>
                  <select v-model="phase.status">
                    <option v-for="s in PHASE_STATUSES" :key="s" :value="s">{{ s }}</option>
                  </select>
                </div>
              </div>
              <div class="field">
                <label>Objective</label>
                <input v-model="phase.objective" required placeholder="e.g. Write the auth middleware" />
              </div>
            </div>
            <button type="button" class="btn-add-phase" @click="addPhase">+ Add Phase</button>
          </div>

          <p v-if="planFormError" class="error">{{ planFormError }}</p>

          <div class="form-actions">
            <button type="button" class="btn-ghost" @click="showPlanForm = false">Cancel</button>
            <button type="submit" class="btn-primary" :disabled="planSaving">
              {{ planSaving ? 'Saving…' : 'Save Plan' }}
            </button>
          </div>
        </form>

        <!-- Plan display -->
        <div v-else-if="plan" class="card">
          <p class="plan-goal">{{ plan.goal }}</p>

          <div v-if="plan.phases.length > 0" class="phases-list">
            <div v-for="phase in plan.phases" :key="phase.id" class="phase-item">
              <span class="badge" :class="PHASE_CLASS[phase.status]">{{ phase.status }}</span>
              <span class="phase-name-text"><strong>{{ phase.name }}</strong> — {{ phase.objective }}</span>
            </div>
          </div>

          <!-- Readiness -->
          <div v-if="readiness" class="readiness">
            <h3>Readiness</h3>
            <div class="readiness-grid">
              <div class="readiness-item">
                <span class="gate-icon" :class="readiness.blockingQuestionsResolved ? 'ok' : 'pending'">
                  {{ readiness.blockingQuestionsResolved ? '✓' : '○' }}
                </span>
                Blocking questions resolved
              </div>
              <div class="readiness-item">
                <span class="gate-icon" :class="readiness.allPhasesComplete ? 'ok' : 'pending'">
                  {{ readiness.allPhasesComplete ? '✓' : '○' }}
                </span>
                All phases complete
              </div>
              <div class="readiness-item">
                <span class="gate-icon" :class="readiness.verificationReady ? 'ok' : 'pending'">
                  {{ readiness.verificationReady ? '✓' : '○' }}
                </span>
                Verification ready
              </div>
              <div class="readiness-item">
                <span class="gate-icon" :class="readiness.readyForReview ? 'ok' : 'pending'">
                  {{ readiness.readyForReview ? '✓' : '○' }}
                </span>
                Ready for review
              </div>
              <div class="readiness-item">
                <span class="gate-icon" :class="readiness.readyForPR ? 'ok' : 'pending'">
                  {{ readiness.readyForPR ? '✓' : '○' }}
                </span>
                Ready for PR
              </div>
            </div>
          </div>
        </div>

        <div v-else class="state-msg muted">No implementation plan yet.</div>
      </section>

      <!-- ── Activity ──────────────────────────────────────────────────────── -->
      <section>
        <div class="section-header">
          <div class="section-title-row">
            <h2>Activity</h2>
            <span class="live-indicator" :class="connected ? 'live' : 'offline'">
              {{ connected ? '● Live' : '○ Offline' }}
            </span>
          </div>
          <button class="btn-secondary" @click="showActivityForm = !showActivityForm">
            {{ showActivityForm ? 'Cancel' : '+ Add Event' }}
          </button>
        </div>

        <!-- Activity form -->
        <form v-if="showActivityForm" class="card form-card" @submit.prevent="addActivity">
          <div class="field-row">
            <div class="field">
              <label>Agent Name</label>
              <input v-model="activityForm.agentName" required placeholder="e.g. Context Scout" />
            </div>
            <div class="field">
              <label>Type</label>
              <select v-model="activityForm.type">
                <option v-for="t in ACTIVITY_TYPES" :key="t" :value="t">{{ t.replace('_', ' ') }}</option>
              </select>
            </div>
          </div>
          <div class="field">
            <label>Message</label>
            <textarea v-model="activityForm.message" required rows="2" placeholder="What did this agent do?" />
          </div>
          <p v-if="activityFormError" class="error">{{ activityFormError }}</p>
          <div class="form-actions">
            <button type="submit" class="btn-primary" :disabled="activitySaving">
              {{ activitySaving ? 'Adding…' : 'Add Event' }}
            </button>
          </div>
        </form>

        <!-- Activity feed -->
        <div v-if="activities.length > 0" class="activity-feed">
          <div v-for="event in activities" :key="event.id" class="activity-item">
            <span class="badge" :class="ACTIVITY_CLASS[event.type]">{{ event.type.replace('_', ' ') }}</span>
            <div class="activity-body">
              <span class="activity-agent">{{ event.agentName }}</span>
              <span class="activity-message">{{ event.message }}</span>
            </div>
            <span class="activity-date">{{ formatDateTime(event.createdAt) }}</span>
          </div>
        </div>
        <div v-else-if="!showActivityForm" class="state-msg muted">No activity yet.</div>
      </section>

    </template>
  </div>
</template>

<style scoped>
.page {
  padding: 2rem;
  max-width: 860px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 2rem;
}

/* Header */
.btn-back {
  background: none;
  border: none;
  color: #6b7280;
  font-size: 0.8rem;
  cursor: pointer;
  padding: 0;
  margin-bottom: 0.75rem;
}
.btn-back:hover { color: #111827; }

.ws-title-row {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  flex-wrap: wrap;
}

h1 { font-size: 1.5rem; font-weight: 600; margin: 0; }
h2 { font-size: 1rem; font-weight: 600; margin: 0; }
h3 { font-size: 0.8rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em; color: #6b7280; margin: 0 0 0.5rem; }

.ws-meta {
  font-size: 0.875rem;
  color: #6b7280;
  margin-top: 0.4rem;
  line-height: 1.6;
}
.sep { margin: 0 0.4rem; }

.status-select {
  padding: 0.2rem 0.5rem;
  border-radius: 4px;
  font-size: 0.7rem;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  border: none;
  cursor: pointer;
}

/* Section */
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0.75rem;
}

.section-title-row {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.live-indicator {
  font-size: 0.7rem;
  font-weight: 600;
  letter-spacing: 0.03em;
}
.live-indicator.live    { color: #059669; }
.live-indicator.offline { color: #9ca3af; }

.card {
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 1.25rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.form-card { background: #f9fafb; }

/* Plan */
.plan-goal {
  font-size: 0.95rem;
  color: #111827;
}

.phases-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.phase-item {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  font-size: 0.875rem;
}

.phase-name-text { color: #374151; }

/* Plan form — phase cards */
.phase-card {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 0.75rem;
  background: white;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
}

.phase-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.phase-num {
  font-size: 0.7rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: #6b7280;
}

.field-status { max-width: 160px; }

.btn-remove {
  background: none;
  border: none;
  color: #9ca3af;
  cursor: pointer;
  font-size: 0.75rem;
  padding: 0.15rem 0.35rem;
}
.btn-remove:hover { color: #ef4444; }

.btn-add-phase {
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
.btn-add-phase:hover { border-color: #6b7280; color: #374151; }

/* Readiness */
.readiness {
  border-top: 1px solid #f3f4f6;
  padding-top: 1rem;
}

.readiness-grid {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.readiness-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.85rem;
  color: #374151;
}

.gate-icon {
  font-size: 0.85rem;
  font-weight: 700;
  width: 1.25rem;
  text-align: center;
}
.gate-icon.ok      { color: #059669; }
.gate-icon.pending { color: #d1d5db; }

/* Activity */
.activity-feed {
  display: flex;
  flex-direction: column;
  gap: 0;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  overflow: hidden;
}

.activity-item {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  padding: 0.75rem 1rem;
  border-bottom: 1px solid #f3f4f6;
  font-size: 0.875rem;
}

.activity-item:last-child { border-bottom: none; }

.activity-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.activity-agent   { font-weight: 600; color: #111827; font-size: 0.8rem; }
.activity-message { color: #374151; }
.activity-date    { color: #9ca3af; white-space: nowrap; font-size: 0.75rem; margin-top: 0.1rem; }

/* Shared form elements */
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

label { font-size: 0.8rem; font-weight: 500; color: #374151; }

input, textarea, select {
  padding: 0.45rem 0.65rem;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 0.875rem;
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

.btn-secondary {
  padding: 0.35rem 0.85rem;
  background: white;
  color: #374151;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 0.8rem;
  font-weight: 500;
  cursor: pointer;
}
.btn-secondary:hover { background: #f3f4f6; }

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

/* Badges (duplicated here so this view is self-contained) */
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

.badge-low       { background: #f3f4f6; color: #6b7280; }
.badge-medium    { background: #fef3c7; color: #92400e; }
.badge-high      { background: #fee2e2; color: #991b1b; }
.badge-new       { background: #dbeafe; color: #1e40af; }
.badge-planning  { background: #ede9fe; color: #5b21b6; }
.badge-executing { background: #ffedd5; color: #9a3412; }
.badge-reviewing { background: #fef9c3; color: #854d0e; }
.badge-verified  { background: #d1fae5; color: #065f46; }
.badge-blocked   { background: #fee2e2; color: #991b1b; }

/* Misc */
.state-msg { color: #6b7280; text-align: center; padding: 2rem 0; font-size: 0.9rem; }
.error     { color: #991b1b; font-size: 0.85rem; }
.muted     { color: #9ca3af; }
.small     { font-size: 0.8rem; }
</style>

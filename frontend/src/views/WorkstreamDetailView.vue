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
  Priority,
  QuestionType,
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

// ── Edit workstream (in-place) ────────────────────────────────────────────────
const editing     = ref(false)
const editSaving  = ref(false)
const editError   = ref<string | null>(null)
const editTitle   = ref('')
const editDesc    = ref('')

function startEdit() {
  editTitle.value = workstream.value!.title
  editDesc.value  = workstream.value!.description
  editError.value = null
  editing.value   = true
}

async function saveEdit() {
  editSaving.value = true
  editError.value  = null
  try {
    workstream.value = await workstreamsApi.update(id, {
      title: editTitle.value,
      description: editDesc.value,
    })
    editing.value = false
  } catch (e) {
    editError.value = e instanceof Error ? e.message : 'Failed to save'
  } finally {
    editSaving.value = false
  }
}

async function updatePriority(priority: Priority) {
  if (!workstream.value) return
  workstream.value = await workstreamsApi.update(id, { priority })
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

function addNonGoal() {
  planForm.value.nonGoals.push('')
}

function removeNonGoal(index: number) {
  planForm.value.nonGoals.splice(index, 1)
}

function addAssumption() {
  planForm.value.assumptions.push('')
}

function removeAssumption(index: number) {
  planForm.value.assumptions.splice(index, 1)
}

function addQuestion() {
  planForm.value.openQuestions.push({
    id: Math.random().toString(36).slice(2, 10),
    question: '',
    type: 'BLOCKING',
    resolution: undefined,
  })
}

function removeQuestion(index: number) {
  planForm.value.openQuestions.splice(index, 1)
}

function addVerificationStep() {
  planForm.value.verificationPlan.push('')
}

function removeVerificationStep(index: number) {
  planForm.value.verificationPlan.splice(index, 1)
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
    if (!activities.value.some(a => a.id === event.id)) {
      activities.value.unshift(event)
    }
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
const PRIORITIES: Priority[]        = ['LOW', 'MEDIUM', 'HIGH']
const STATUSES: WorkstreamStatus[] = ['NEW', 'PLANNING', 'EXECUTING', 'REVIEWING', 'VERIFIED', 'BLOCKED']
const ACTIVITY_TYPES: ActivityType[] = ['CONTEXT_DISCOVERY', 'PLANNING', 'IMPLEMENTATION', 'REVIEW', 'VERIFICATION', 'HANDOFF']
const PHASE_STATUSES: PhaseStatus[]    = ['PENDING', 'IN_PROGRESS', 'COMPLETE', 'BLOCKED']
const QUESTION_TYPES: QuestionType[]   = ['BLOCKING', 'ASSUMABLE', 'DEFERRABLE']

const PRIORITY_CLASS: Record<string, string> = {
  LOW: 'badge-low', MEDIUM: 'badge-medium', HIGH: 'badge-high',
}
const STATUS_CLASS: Record<string, string> = {
  NEW: 'badge-new', PLANNING: 'badge-planning', EXECUTING: 'badge-executing',
  REVIEWING: 'badge-reviewing', VERIFIED: 'badge-verified', BLOCKED: 'badge-blocked',
}
const QUESTION_TYPE_CLASS: Record<QuestionType, string> = {
  BLOCKING: 'badge-blocked', ASSUMABLE: 'badge-planning', DEFERRABLE: 'badge-low',
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

        <!-- Line 1: title (or input) · requester · created + Edit/Save/Cancel -->
        <div class="ws-line1">
          <h1 v-if="!editing">{{ workstream.title }}</h1>
          <input v-else v-model="editTitle" class="edit-title" required />
          <span v-if="!editing" class="ws-meta-inline">
            <span class="sep">·</span>{{ workstream.requester }}<span class="sep">·</span>{{ formatDate(workstream.createdAt) }}
          </span>
          <div class="ws-edit-actions">
            <template v-if="!editing">
              <button class="btn-secondary btn-sm" @click="startEdit">Edit</button>
            </template>
            <template v-else>
              <button type="button" class="btn-ghost btn-sm" @click="editing = false">Cancel</button>
              <button type="button" class="btn-primary btn-sm" :disabled="editSaving" @click="saveEdit">
                {{ editSaving ? 'Saving…' : 'Save' }}
              </button>
            </template>
          </div>
        </div>

        <!-- Line 2: description (or textarea) -->
        <p v-if="!editing" class="ws-description">{{ workstream.description }}</p>
        <textarea v-else v-model="editDesc" class="edit-desc" rows="2" />
        <p v-if="editError" class="error small">{{ editError }}</p>

        <!-- Line 3: priority + status with labels -->
        <div class="ws-line3">
          <span class="inline-label">Priority</span>
          <select
            class="status-select"
            :class="PRIORITY_CLASS[workstream.priority]"
            :value="workstream.priority"
            @change="updatePriority(($event.target as HTMLSelectElement).value as Priority)"
          >
            <option v-for="p in PRIORITIES" :key="p" :value="p">{{ p }}</option>
          </select>
          <span class="inline-label">Status</span>
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
            <label>Non-Goals</label>
            <div v-if="planForm.nonGoals.length === 0" class="muted small">No non-goals yet.</div>
            <div v-for="(_, i) in planForm.nonGoals" :key="i" class="assumption-row">
              <input v-model="planForm.nonGoals[i]" required placeholder="e.g. We are not supporting SSO" />
              <button type="button" class="btn-remove" @click="removeNonGoal(i)">✕</button>
            </div>
            <button type="button" class="btn-add-phase" @click="addNonGoal">+ Add Non-Goal</button>
          </div>

          <div class="field">
            <label>Assumptions</label>
            <div v-if="planForm.assumptions.length === 0" class="muted small">No assumptions yet.</div>
            <div v-for="(_, i) in planForm.assumptions" :key="i" class="assumption-row">
              <input v-model="planForm.assumptions[i]" required placeholder="e.g. Postgres is available" />
              <button type="button" class="btn-remove" @click="removeAssumption(i)">✕</button>
            </div>
            <button type="button" class="btn-add-phase" @click="addAssumption">+ Add Assumption</button>
          </div>

          <div class="field">
            <label>Open Questions</label>
            <div v-if="planForm.openQuestions.length === 0" class="muted small">No open questions yet.</div>
            <div v-for="(q, i) in planForm.openQuestions" :key="q.id" class="phase-card">
              <div class="phase-card-header">
                <span class="phase-num">Question {{ i + 1 }}</span>
                <button type="button" class="btn-remove" @click="removeQuestion(i)">✕ Remove</button>
              </div>
              <div class="field">
                <label>Question</label>
                <input v-model="q.question" required placeholder="e.g. What should the token expiry be?" />
              </div>
              <div class="field-row">
                <div class="field field-status">
                  <label>Type</label>
                  <select v-model="q.type">
                    <option v-for="t in QUESTION_TYPES" :key="t" :value="t">{{ t }}</option>
                  </select>
                </div>
                <div class="field">
                  <label>Resolution (optional)</label>
                  <input v-model="q.resolution" placeholder="e.g. 24 hours per security policy" />
                </div>
              </div>
            </div>
            <button type="button" class="btn-add-phase" @click="addQuestion">+ Add Question</button>
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

          <div class="field">
            <label>Verification Plan</label>
            <div v-if="planForm.verificationPlan.length === 0" class="muted small">No verification steps yet.</div>
            <div v-for="(_, i) in planForm.verificationPlan" :key="i" class="assumption-row">
              <input v-model="planForm.verificationPlan[i]" required placeholder="e.g. Run integration tests against staging" />
              <button type="button" class="btn-remove" @click="removeVerificationStep(i)">✕</button>
            </div>
            <button type="button" class="btn-add-phase" @click="addVerificationStep">+ Add Step</button>
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
          <div class="plan-subsection">
            <h3>Goal</h3>
            <p class="plan-goal">{{ plan.goal }}</p>
          </div>

          <div v-if="plan.nonGoals.length > 0" class="plan-subsection">
            <h3>Non-Goals</h3>
            <ul class="assumptions-list">
              <li v-for="(ng, i) in plan.nonGoals" :key="i">{{ ng }}</li>
            </ul>
          </div>

          <div v-if="plan.assumptions.length > 0" class="plan-subsection">
            <h3>Assumptions</h3>
            <ul class="assumptions-list">
              <li v-for="(a, i) in plan.assumptions" :key="i">{{ a }}</li>
            </ul>
          </div>

          <div v-if="plan.openQuestions.length > 0" class="plan-subsection">
            <h3>Open Questions</h3>
            <div class="questions-list">
              <div v-for="q in plan.openQuestions" :key="q.id" class="question-item">
                <span class="badge" :class="QUESTION_TYPE_CLASS[q.type]">{{ q.type }}</span>
                <div class="question-body">
                  <span class="question-text">{{ q.question }}</span>
                  <span v-if="q.resolution" class="question-resolution">→ {{ q.resolution }}</span>
                  <span v-else-if="q.type === 'BLOCKING'" class="question-unresolved">⚠ Unresolved</span>
                </div>
              </div>
            </div>
          </div>

          <div v-if="plan.phases.length > 0" class="plan-subsection">
            <h3>Phases</h3>
            <div class="phases-list">
              <div v-for="phase in plan.phases" :key="phase.id" class="phase-item">
                <span class="badge" :class="PHASE_CLASS[phase.status]">{{ phase.status }}</span>
                <span class="phase-name-text"><strong>{{ phase.name }}</strong> — {{ phase.objective }}</span>
              </div>
            </div>
          </div>

          <div v-if="plan.verificationPlan.length > 0" class="plan-subsection">
            <h3>Verification Plan</h3>
            <ul class="assumptions-list">
              <li v-for="(step, i) in plan.verificationPlan" :key="i">{{ step }}</li>
            </ul>
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
          <h2>Activity</h2>
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
  max-width: 1400px;
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

h1 { font-size: 1.5rem; font-weight: 600; margin: 0; }
h2 { font-size: 1rem; font-weight: 600; margin: 0; }
h3 { font-size: 0.8rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em; color: #6b7280; margin: 0 0 0.5rem; }

.ws-line1 {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  flex-wrap: wrap;
}

.ws-meta-inline {
  font-size: 0.8rem;
  color: #9ca3af;
  white-space: nowrap;
}

.ws-edit-actions {
  margin-left: auto;
  display: flex;
  gap: 0.4rem;
  align-items: center;
}

.ws-description {
  font-size: 0.875rem;
  color: #374151;
  margin: 0.35rem 0 0;
  line-height: 1.5;
}

.ws-line3 {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: 0.6rem;
}

.inline-label {
  font-size: 0.75rem;
  font-weight: 500;
  color: #6b7280;
}

.edit-title {
  font-size: 1.5rem;
  font-weight: 600;
  border: none;
  border-bottom: 2px solid #6366f1;
  outline: none;
  background: transparent;
  padding: 0;
  font-family: inherit;
  color: #111827;
  flex: 1;
  min-width: 0;
}

.edit-desc {
  margin-top: 0.35rem;
  width: 100%;
  box-sizing: border-box;
  font-size: 0.875rem;
  font-family: inherit;
  color: #374151;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  padding: 0.45rem 0.65rem;
  resize: vertical;
}

.edit-desc:focus { outline: 2px solid #6366f1; outline-offset: -1px; border-color: transparent; }

.btn-sm { padding: 0.25rem 0.65rem; font-size: 0.8rem; }

.sep { margin: 0 0.3rem; }

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

.card {
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 1.25rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.form-card { background: #f9fafb; }

/* Assumptions form row */
.assumption-row {
  display: flex;
  gap: 0.5rem;
  align-items: center;
  margin-bottom: 0.4rem;
}

.assumption-row input { flex: 1; }

/* Plan sub-sections (assumptions, etc.) */
.plan-subsection { display: flex; flex-direction: column; gap: 0.4rem; }

.assumptions-list {
  margin: 0;
  padding-left: 1.25rem;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.875rem;
  color: #374151;
}

/* Open questions display */
.questions-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.question-item {
  display: flex;
  align-items: flex-start;
  gap: 0.6rem;
  font-size: 0.875rem;
}

.question-body {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.question-text       { color: #111827; }
.question-resolution { color: #059669; font-size: 0.8rem; }
.question-unresolved { color: #b45309; font-size: 0.8rem; }

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

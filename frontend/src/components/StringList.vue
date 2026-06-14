<script setup lang="ts">
defineProps<{
  heading: string
  items: string[]
}>()
</script>

<template>
  <div v-if="items.length > 0" class="subsection">
    <h3>{{ heading }}</h3>
    <ul class="list">
      <li v-for="(item, i) in items" :key="i">{{ item }}</li>
    </ul>
  </div>
</template>

<style scoped>
.subsection { display: flex; flex-direction: column; gap: 0.4rem; }

h3 {
  font-size: 0.8rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: #6b7280;
  margin: 0 0 0.5rem;
}

.list {
  margin: 0;
  padding-left: 1.25rem;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.875rem;
  color: #374151;
}
</style>

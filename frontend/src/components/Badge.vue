<script setup lang="ts">
defineProps<{
  variant: string
  label: string
}>()
</script>

<template>
  <span class="badge" :class="variant">{{ label }}</span>
</template>

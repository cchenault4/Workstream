<template>
  <header>
    <span class="app-name">Workstream</span>
    <nav>
      <RouterLink to="/workstreams">Workstreams</RouterLink>
      <RouterLink to="/health">Health</RouterLink>
    </nav>
  </header>
  <RouterView />
</template>

<style scoped>
header {
  display: flex;
  align-items: center;
  gap: 2rem;
  padding: 0 2rem;
  height: 52px;
  border-bottom: 1px solid #e5e7eb;
  background: white;
}

.app-name {
  font-weight: 600;
  font-size: 0.95rem;
  color: #111827;
}

nav {
  display: flex;
  gap: 0.25rem;
}

nav a {
  padding: 0.3rem 0.75rem;
  border-radius: 6px;
  font-size: 0.875rem;
  font-weight: 500;
  color: #6b7280;
  text-decoration: none;
  transition: background 0.15s, color 0.15s;
}

nav a:hover { background: #f3f4f6; color: #111827; }
nav a.router-link-active { background: #ede9fe; color: #4f46e5; }
</style>

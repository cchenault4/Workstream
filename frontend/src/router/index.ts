import { createRouter, createWebHistory } from 'vue-router'
import WorkstreamsView from '../views/WorkstreamsView.vue'
import HealthView from '../views/HealthView.vue'

export default createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/workstreams' },
    { path: '/workstreams', component: WorkstreamsView },
    { path: '/health', component: HealthView },
  ],
})

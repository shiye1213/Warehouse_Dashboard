import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', redirect: '/overview' },
  { path: '/overview', name: 'overview', component: () => import('../views/ExecutiveDashboard.vue'), meta: { title: '经营总览', eyebrow: 'EXECUTIVE OVERVIEW' } },
  { path: '/raw-material', name: 'raw-material', component: () => import('../views/RawMaterialDashboard.vue'), meta: { title: '原料库看板', eyebrow: 'RAW MATERIAL WAREHOUSE' } },
  { path: '/operations', name: 'operations', component: () => import('../views/OperationsView.vue'), meta: { title: '作业运营', eyebrow: 'OPERATIONS' } },
  { path: '/performance', name: 'performance', component: () => import('../views/PerformanceView.vue'), meta: { title: '履约质量', eyebrow: 'SERVICE PERFORMANCE' } },
  { path: '/zones', name: 'zones', component: () => import('../views/ZonesView.vue'), meta: { title: '空间与库存', eyebrow: 'SPACE & INVENTORY' } },
  { path: '/zones/:code', name: 'zone-detail', component: () => import('../views/ZoneDetailView.vue'), meta: { title: '库区详情', eyebrow: 'ZONE DETAIL' } },
  { path: '/exceptions', name: 'exceptions', component: () => import('../views/ExceptionsView.vue'), meta: { title: '风险与异常', eyebrow: 'RISK CONTROL' } },
  { path: '/resources', name: 'resources', component: () => import('../views/ResourcesView.vue'), meta: { title: '资源调度', eyebrow: 'RESOURCE DISPATCH' } },
  { path: '/data-center', name: 'data-center', component: () => import('../views/DataCenterView.vue'), meta: { title: '数据中心', eyebrow: 'DATA EXCHANGE' } },
  { path: '/:pathMatch(.*)*', redirect: '/overview' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

export default router

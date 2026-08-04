<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Activity,
  AlertTriangle,
  Boxes,
  ChevronLeft,
  CircleGauge,
  Database,
  Download,
  Menu,
  PanelLeftClose,
  PanelLeftOpen,
  RefreshCw,
  Route,
  ShieldCheck,
  Truck,
  Wifi,
  X,
} from 'lucide-vue-next'
import { useDashboard } from '../composables/useDashboard'

const route = useRoute()
const router = useRouter()
const collapsed = ref(localStorage.getItem('warehouse.sidebar.collapsed') === 'true')
const mobileOpen = ref(false)
const now = ref(new Date())
const { snapshot, loading, refresh } = useDashboard()

const navItems = [
  { to: '/overview', label: '经营总览', note: '客户与高管视图', icon: CircleGauge },
  { to: '/operations', label: '作业运营', note: '入库 · 拣货 · 出库', icon: Route },
  { to: '/performance', label: '履约质量', note: '时效与准确率', icon: ShieldCheck },
  { to: '/zones', label: '空间与库存', note: '库容与库区状态', icon: Boxes },
  { to: '/exceptions', label: '风险与异常', note: '预警与处置闭环', icon: AlertTriangle },
  { to: '/resources', label: '资源调度', note: '叉车与任务负荷', icon: Truck },
  { to: '/data-center', label: '数据中心', note: '导入与导出', icon: Database },
]

const currentLabel = computed(() => route.meta?.title || '仓储运营指挥中心')
const latestDate = computed(() => snapshot.value?.meta?.latestDate || '—')
let timer

function toggleSidebar() {
  collapsed.value = !collapsed.value
  localStorage.setItem('warehouse.sidebar.collapsed', String(collapsed.value))
}

function navigate(path) {
  router.push(path)
  mobileOpen.value = false
}

function openMobileNavigation() {
  mobileOpen.value = true
}

watch(() => route.fullPath, () => { mobileOpen.value = false })
onMounted(() => {
  timer = window.setInterval(() => { now.value = new Date() }, 1000)
  window.addEventListener('warehouse:open-navigation', openMobileNavigation)
})
onBeforeUnmount(() => {
  window.clearInterval(timer)
  window.removeEventListener('warehouse:open-navigation', openMobileNavigation)
})
</script>

<template>
  <div class="app-shell" :class="{ 'is-collapsed': collapsed, 'is-mobile-open': mobileOpen, 'is-board-route': route.name === 'overview' }">
    <button v-if="mobileOpen" class="mobile-scrim" aria-label="关闭导航" @click="mobileOpen = false" />

    <aside class="sidebar" aria-label="主导航">
      <div class="brand">
        <div class="brand-mark" aria-hidden="true"><span>IW</span></div>
        <div class="brand-copy">
          <strong>INTCO WAREHOUSE</strong>
          <span>运营指挥中心</span>
        </div>
        <button class="mobile-close" aria-label="关闭导航" @click="mobileOpen = false"><X :size="18" /></button>
      </div>

      <nav class="side-nav">
        <p class="nav-caption">决策工作台</p>
        <button
          v-for="item in navItems"
          :key="item.to"
          class="nav-item"
          :class="{ active: route.path === item.to || (item.to === '/zones' && route.path.startsWith('/zones/')) }"
          :title="collapsed ? item.label : undefined"
          @click="navigate(item.to)"
        >
          <span class="nav-icon"><component :is="item.icon" :size="19" :stroke-width="1.8" /></span>
          <span class="nav-copy"><strong>{{ item.label }}</strong><small>{{ item.note }}</small></span>
        </button>
      </nav>

      <div class="sidebar-foot">
        <div class="sync-state">
          <span class="live-dot" />
          <div><strong>数据服务在线</strong><small>基准日 {{ latestDate }}</small></div>
        </div>
        <button class="collapse-button" :aria-label="collapsed ? '展开侧边栏' : '收起侧边栏'" @click="toggleSidebar">
          <PanelLeftOpen v-if="collapsed" :size="19" />
          <PanelLeftClose v-else :size="19" />
          <span>{{ collapsed ? '' : '收起侧边栏' }}</span>
        </button>
      </div>
    </aside>

    <main class="main-stage">
      <header class="topbar">
        <div class="topbar-left">
          <button class="mobile-menu" aria-label="打开导航" @click="mobileOpen = true"><Menu :size="21" /></button>
          <button v-if="route.name === 'zone-detail'" class="back-button" @click="router.push('/zones')"><ChevronLeft :size="18" /> 返回库区</button>
          <div v-else>
            <p>{{ route.meta?.eyebrow }}</p>
            <h1>{{ currentLabel }}</h1>
          </div>
        </div>
        <div class="topbar-actions">
          <div class="connection"><Wifi :size="15" /><span>实时服务</span></div>
          <div class="clock"><strong>{{ now.toLocaleTimeString('zh-CN', { hour12: false }) }}</strong><span>{{ now.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric', weekday: 'short' }) }}</span></div>
          <button class="icon-button" :class="{ spinning: loading }" aria-label="刷新数据" title="刷新数据" @click="refresh"><RefreshCw :size="18" /></button>
          <button class="primary-mini" @click="navigate('/data-center')"><Download :size="16" /><span>数据交换</span></button>
        </div>
      </header>

      <router-view v-slot="{ Component }">
        <Transition name="page" mode="out-in">
          <component :is="Component" />
        </Transition>
      </router-view>
    </main>
  </div>
</template>

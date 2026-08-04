import { computed, onMounted, ref } from 'vue'
import { dashboardApi } from '../services/api'

const snapshot = ref(null)
const loading = ref(false)
const error = ref('')
let pendingRequest

async function load(force = false) {
  if (snapshot.value && !force) return snapshot.value
  if (pendingRequest && !force) return pendingRequest
  loading.value = true
  error.value = ''
  pendingRequest = dashboardApi.getSnapshot(31)
    .then((data) => {
      snapshot.value = data
      return data
    })
    .catch((cause) => {
      error.value = cause.message || '数据加载失败'
      throw cause
    })
    .finally(() => {
      loading.value = false
      pendingRequest = null
    })
  return pendingRequest
}

export function useDashboard() {
  onMounted(() => load().catch(() => {}))
  return {
    snapshot,
    loading: computed(() => loading.value),
    error: computed(() => error.value),
    refresh: () => load(true),
  }
}

export const formatNumber = (value) => new Intl.NumberFormat('zh-CN').format(Number(value || 0))
export const formatPercent = (value, digits = 1) => `${(Number(value || 0) * 100).toFixed(digits)}%`
export const formatDate = (value) => {
  if (!value) return '—'
  const date = new Date(`${value}T00:00:00`)
  return Number.isNaN(date.getTime()) ? value : `${date.getMonth() + 1}月${date.getDate()}日`
}

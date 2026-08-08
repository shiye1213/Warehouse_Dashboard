import { computed, onMounted, ref } from 'vue'
import { dashboardApi } from '../services/api'
import { registerProjectRefresh } from './useProjectRefresh'

const snapshot = ref(null)
const loading = ref(false)
const error = ref('')
let pendingRefresh

function refresh() {
  if (pendingRefresh) return pendingRefresh

  loading.value = true
  error.value = ''
  pendingRefresh = dashboardApi.getWarehouseSnapshot('WH-RM01', 31).then((data) => {
    snapshot.value = data
    return data
  }).catch((cause) => {
    error.value = cause.message || '原料库数据加载失败'
    throw cause
  }).finally(() => {
    loading.value = false
    pendingRefresh = null
  })

  return pendingRefresh
}

registerProjectRefresh('raw-material-dashboard', refresh)

export function useRawMaterialDashboard() {
  onMounted(() => refresh().catch(() => {}))
  return {
    snapshot,
    loading: computed(() => loading.value),
    error: computed(() => error.value),
    refresh,
  }
}

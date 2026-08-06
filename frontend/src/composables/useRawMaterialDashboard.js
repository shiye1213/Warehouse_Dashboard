import { computed, ref } from 'vue'
import rawMaterialSnapshot from '../data/raw-material-dashboard.json'
import { registerProjectRefresh } from './useProjectRefresh'

const snapshot = ref(rawMaterialSnapshot)
const loading = ref(false)
const error = ref('')
let pendingRefresh

function refresh() {
  if (pendingRefresh) return pendingRefresh

  loading.value = true
  error.value = ''
  pendingRefresh = Promise.resolve().then(() => {
    snapshot.value = {
      ...rawMaterialSnapshot,
      meta: { ...rawMaterialSnapshot.meta, refreshedAt: new Date().toISOString() },
    }
    return snapshot.value
  }).finally(() => {
    loading.value = false
    pendingRefresh = null
  })

  return pendingRefresh
}

registerProjectRefresh('raw-material-dashboard', refresh)

export function useRawMaterialDashboard() {
  return {
    snapshot,
    loading: computed(() => loading.value),
    error: computed(() => error.value),
    refresh,
  }
}

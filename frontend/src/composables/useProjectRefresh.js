import { computed, ref } from 'vue'

export const PROJECT_REFRESH_INTERVAL_MS = 30_000

const MINIMUM_LOADING_MS = 1650
const refreshHandlers = new Map()
const refreshing = ref(true)
const refreshVersion = ref(0)
const lastUpdatedAt = ref(null)
let pendingRefresh

function wait(duration) {
  return new Promise((resolve) => window.setTimeout(resolve, duration))
}

export function registerProjectRefresh(key, handler) {
  refreshHandlers.set(key, handler)
  return () => {
    if (refreshHandlers.get(key) === handler) refreshHandlers.delete(key)
  }
}

export function refreshProject(reason = 'manual') {
  if (pendingRefresh) return pendingRefresh

  const startedAt = Date.now()
  refreshing.value = true
  refreshVersion.value += 1

  pendingRefresh = Promise.allSettled(
    [...refreshHandlers.values()].map((handler) => Promise.resolve().then(() => handler(reason))),
  )
    .then(async (results) => {
      const remaining = MINIMUM_LOADING_MS - (Date.now() - startedAt)
      if (remaining > 0) await wait(remaining)
      lastUpdatedAt.value = new Date()
      return results
    })
    .finally(() => {
      refreshing.value = false
      pendingRefresh = null
    })

  return pendingRefresh
}

export function useProjectRefresh() {
  return {
    refreshing: computed(() => refreshing.value),
    refreshVersion: computed(() => refreshVersion.value),
    lastUpdatedAt: computed(() => lastUpdatedAt.value),
    refreshProject,
  }
}

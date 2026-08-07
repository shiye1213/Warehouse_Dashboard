<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import PageState from '../components/PageState.vue'
import WarehouseBlueprint from '../components/WarehouseBlueprint.vue'
import { mergeWarehouseMapZones } from '../data/warehouseMapZones'
import { useScopedDashboard } from '../composables/useWarehouseScope'

const router = useRouter()
const { snapshot, loading, error, refresh, selectedWarehouse } = useScopedDashboard()
const zones = computed(() => mergeWarehouseMapZones(snapshot.value?.zones || [], selectedWarehouse.value))

function openZone(zone) {
  router.push({ path: `/zones/${encodeURIComponent(zone.code)}`, query: { warehouse: zone.warehouse } })
}
</script>

<template>
  <div class="page space-inventory-page">
    <PageState :loading="loading && !snapshot" :error="error" @retry="refresh">
      <WarehouseBlueprint :zones="zones" :selected-warehouse="selectedWarehouse" @open-zone="openZone" />
    </PageState>
  </div>
</template>

<style scoped>
.space-inventory-page { width: min(1820px, 100%); padding: 18px 22px 42px; }
@media (max-width: 760px) { .space-inventory-page { padding: 12px 9px 30px; } }
</style>
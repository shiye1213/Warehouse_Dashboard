import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useDashboard } from './useDashboard'

export const ALL_WAREHOUSES = '全部仓库'
export const WAREHOUSE_OPTIONS = [
  { value: ALL_WAREHOUSES, label: '全部' },
  { value: '成品库', label: '成品库' },
  { value: '原料库', label: '原料库' },
  { value: '箱盒库', label: '箱盒库' },
]

const sum = (rows, key) => rows.reduce((total, row) => total + Number(row[key] || 0), 0)
const average = (rows, key) => rows.length ? sum(rows, key) / rows.length : 0
const relative = (current, previous) => previous ? (current - previous) / previous * 100 : 0

function volumeFields(warehouse) {
  if (warehouse === '原料库') return { inbound: 'rawInboundTon', outbound: 'rawOutboundTon', unit: '吨' }
  if (warehouse === '箱盒库') return { inbound: 'packagingInboundPiece', outbound: 'packagingOutboundPiece', unit: '个' }
  return { inbound: 'finishedInboundCarton', outbound: 'finishedOutboundCarton', unit: '箱' }
}

function calculateDeltas(rows) {
  const window = Math.min(7, Math.floor(rows.length / 2))
  if (!window) return {}
  const current = rows.slice(-window)
  const previous = rows.slice(-window * 2, -window)
  return {
    inbound: relative(sum(current, 'inbound'), sum(previous, 'inbound')),
    outbound: relative(sum(current, 'outbound'), sum(previous, 'outbound')),
    inventoryAccuracy: relative(average(current, 'inventoryAccuracy'), average(previous, 'inventoryAccuracy')),
    receivingTimely: relative(average(current, 'receivingTimely'), average(previous, 'receivingTimely')),
    deliveryTimely: relative(average(current, 'deliveryTimely'), average(previous, 'deliveryTimely')),
    exceptions: relative(average(current, 'exceptions'), average(previous, 'exceptions')),
    occupancy: 0,
  }
}

export function useWarehouseScope() {
  const route = useRoute()
  const router = useRouter()
  const selectedWarehouse = computed(() => {
    const value = String(route.query.warehouse || '')
    return WAREHOUSE_OPTIONS.some((item) => item.value === value) ? value : ALL_WAREHOUSES
  })

  function selectWarehouse(value) {
    const query = { ...route.query }
    if (value === ALL_WAREHOUSES) delete query.warehouse
    else query.warehouse = value
    router.replace({ path: route.path, query, hash: route.hash })
  }

  return { selectedWarehouse, warehouseOptions: WAREHOUSE_OPTIONS, selectWarehouse }
}

export function useScopedDashboard() {
  const base = useDashboard()
  const { selectedWarehouse, warehouseOptions, selectWarehouse } = useWarehouseScope()
  const volume = computed(() => volumeFields(selectedWarehouse.value))
  const snapshot = computed(() => {
    const source = base.snapshot.value
    if (!source || selectedWarehouse.value === ALL_WAREHOUSES) return source

    const daily = (source.warehouseDaily || [])
      .filter((row) => row.warehouseName === selectedWarehouse.value)
      .map((row) => ({
        ...row,
        inbound: Number(row[volume.value.inbound] || 0),
        outbound: Number(row[volume.value.outbound] || 0),
      }))
    const zones = (source.zones || []).filter((item) => item.warehouse === selectedWarehouse.value)
    const alerts = (source.alerts || []).filter((item) => item.warehouse === selectedWarehouse.value)
    const forklifts = (source.forklifts || []).filter((item) => item.zone === selectedWarehouse.value)
    const latest = daily.at(-1) || {}
    const capacity = sum(zones, 'capacity')
    const occupied = sum(zones, 'occupied')
    const closedAlerts = alerts.filter((item) => item.status === '已关闭').length

    return {
      ...source,
      meta: { ...source.meta, activeWarehouse: selectedWarehouse.value },
      trend: daily,
      zones,
      alerts,
      forklifts,
      summary: {
        ...source.summary,
        latestDate: latest.date || source.summary?.latestDate,
        todayInbound: Number(latest.inbound || 0),
        todayOutbound: Number(latest.outbound || 0),
        todayPicking: Number(latest.picking || 0),
        monthInbound: sum(daily, 'inbound'),
        monthOutbound: sum(daily, 'outbound'),
        monthPicking: sum(daily, 'picking'),
        monthForkliftTasks: sum(daily, 'forkliftTasks'),
        inventoryAccuracy: average(daily, 'inventoryAccuracy'),
        receivingTimely: average(daily, 'receivingTimely'),
        deliveryTimely: average(daily, 'deliveryTimely'),
        dockUtilization: average(daily, 'dockUtilization'),
        avgReceiptMinutes: Math.round(average(daily, 'receiptMinutes')),
        avgPickingMinutes: Math.round(average(daily, 'pickingMinutes')),
        overtimeHours: sum(daily, 'overtimeHours'),
        totalLocations: capacity,
        occupiedLocations: occupied,
        availableLocations: sum(zones, 'available'),
        frozenLocations: sum(zones, 'frozen'),
        occupancy: capacity ? occupied / capacity : 0,
        openAlerts: alerts.filter((item) => item.status !== '已关闭').length,
        criticalAlerts: alerts.filter((item) => item.status !== '已关闭' && item.severity === '紧急').length,
        exceptionCloseRate: alerts.length ? closedAlerts / alerts.length : 1,
        deltas: calculateDeltas(daily),
      },
    }
  })

  return {
    ...base,
    snapshot,
    selectedWarehouse,
    warehouseOptions,
    selectWarehouse,
    volumeUnit: computed(() => volume.value.unit),
  }
}


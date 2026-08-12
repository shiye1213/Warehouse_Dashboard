<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  Activity, AlertTriangle, Box, ChevronRight, ClipboardCheck, Clock3,
  Layers3, Menu, PackageCheck, RefreshCw, ScanLine, ShieldCheck, Truck, Users,
  Warehouse,
} from 'lucide-vue-next'
import TrendChart from '../components/TrendChart.vue'
import { dashboardApi } from '../services/api'

const router = useRouter()
const finishedGoodsData = reactive({ meta: {}, daily: [], zones: [], alerts: [], inventory: [], targets: [] })
const daily = finishedGoodsData.daily
const zones = finishedGoodsData.zones
const animatedZoneOccupancy = ref(zones.map(() => 0))
let zoneAnimationFrame
const refreshVersion = ref(0)
const refreshing = ref(false)
const showUpdated = ref(false)
const loadError = ref('')
let refreshTimer
let updatedTimer

const alerts = finishedGoodsData.alerts
const inventory = finishedGoodsData.inventory
const targets = finishedGoodsData.targets
const meta = finishedGoodsData.meta
const latest = reactive({})

function syncRows(target, source) {
  source.forEach((row, index) => {
    if (target[index]) Object.assign(target[index], row)
    else target.push(row)
  })
  if (target.length > source.length) target.splice(source.length)
}

async function loadFinishedGoods() {
  const data = await dashboardApi.getWarehouseSnapshot('WH-FG03', 31)
  Object.assign(meta, data.meta || {})
  syncRows(daily, data.daily || [])
  syncRows(weekRows, daily.slice(-7))
  syncRows(previousWeekRows, daily.slice(-14, -7))
  Object.assign(yesterday, daily.at(-2) || {})
  syncRows(zones, data.zones || [])
  syncRows(alerts, data.alerts || [])
  syncRows(inventory, data.inventory || [])
  syncRows(targets, data.targets || [])
  if (daily.length) Object.assign(latest, daily.at(-1))
}

async function triggerRefresh(initial = false) {
  if (refreshing.value) return

  window.clearTimeout(refreshTimer)
  window.clearTimeout(updatedTimer)
  refreshing.value = true
  showUpdated.value = false
  loadError.value = ''

  try {
    await loadFinishedGoods()
    refreshVersion.value += 1
    animateZoneOccupancy()
    startAlertCarousel()

    if (!initial) {
      showUpdated.value = true
      updatedTimer = window.setTimeout(() => { showUpdated.value = false }, 2200)
    }
  } catch (error) {
    console.error('加载成品库看板失败', error)
    loadError.value = error instanceof Error ? error.message : String(error)
  } finally {
    refreshTimer = window.setTimeout(() => { refreshing.value = false }, initial ? 0 : 520)
  }
}

const trendSeries = [
  { name: '成品入库量', key: 'inbound', color: '#28c8ff', area: true },
  { name: '成品出库量', key: 'outbound', color: '#c5d8ff', symbol: 'diamond' },
]

const sum = (rows, key) => rows.reduce((total, row) => total + Number(row[key] || 0), 0)
const average = (rows, key) => rows.length ? sum(rows, key) / rows.length : 0
const formatNumber = (value, digits = 0) => new Intl.NumberFormat('zh-CN', { maximumFractionDigits: digits }).format(Number(value || 0))
const formatPercent = (value, digits = 1) => (Number(value || 0) * 100).toFixed(digits) + '%'

const countAnimationFrames = new WeakMap()
function playCountUp(el, displayValue) {
  const targetText = String(displayValue ?? '0')
  const normalized = targetText.replace(/,/g, '')
  const match = normalized.match(/-?[0-9]+(?:\.[0-9]+)?/)
  if (!match) {
    el.textContent = targetText
    return
  }

  const target = Number(match[0])
  const decimals = (match[0].split('.')[1] || '').length
  const suffix = normalized.slice((match.index || 0) + match[0].length)
  const prefix = normalized.slice(0, match.index || 0)
  const reducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  cancelAnimationFrame(countAnimationFrames.get(el))
  if (reducedMotion) {
    el.textContent = targetText
    return
  }

  const duration = 1150
  let startedAt
  const tick = (timestamp) => {
    startedAt ??= timestamp
    const progress = Math.min((timestamp - startedAt) / duration, 1)
    const eased = 1 - Math.pow(1 - progress, 3)
    const current = target * eased
    const number = new Intl.NumberFormat('zh-CN', {
      minimumFractionDigits: decimals,
      maximumFractionDigits: decimals,
    }).format(current)
    el.textContent = prefix + number + suffix
    if (progress < 1) countAnimationFrames.set(el, requestAnimationFrame(tick))
  }
  countAnimationFrames.set(el, requestAnimationFrame(tick))
}

const vCountUp = {
  mounted: (el, binding) => playCountUp(el, binding.value),
  updated: (el, binding) => {
    if (binding.value !== binding.oldValue) playCountUp(el, binding.value)
  },
}

const zoneCapacity = computed(() => sum(zones, 'capacity'))
const zoneOccupied = computed(() => sum(zones, 'occupied'))
const zoneOccupancy = computed(() => zoneCapacity.value ? zoneOccupied.value / zoneCapacity.value : 0)
const openAlerts = computed(() => alerts.filter((item) => item.status !== '已关闭'))
const closedAlerts = computed(() => alerts.filter((item) => item.status === '已关闭'))
const closeRate = computed(() => alerts.length ? closedAlerts.value.length / alerts.length : 1)
const carouselAlerts = computed(() => alerts.slice().reverse().slice(0, 5))
const activeAlertIndex = ref(0)
const alertCarouselPaused = ref(false)
let alertCarouselTimer

const inventorySummary = computed(() => ({
  onHand: sum(inventory, 'onHand'),
  reserved: sum(inventory, 'reserved'),
  frozen: sum(inventory, 'frozen'),
  skuCount: inventory.length,
  projectCount: new Set(inventory.map((item) => item.projectNo)).size,
}))

const metricCards = computed(() => [
  { label: '当日成品入库量', value: latest.inbound, unit: '箱', note: latest.inboundOrders + ' 张入库单', icon: PackageCheck, tone: 'cyan', path: '/operations' },
  { label: '当日成品出库量', value: latest.outbound, unit: '箱', note: latest.outboundOrders + ' 张出库单', icon: Truck, tone: 'blue', path: '/operations' },
  { label: '平均库存准确率', value: formatPercent(average(daily, 'inventoryAccuracy')), unit: '', note: '目标 ' + formatPercent(target('库存准确率')), icon: ShieldCheck, tone: 'cyan', path: '/performance' },
  { label: '平均出库及时率', value: formatPercent(average(daily, 'deliveryTimely')), unit: '', note: '目标 ' + formatPercent(target('出库及时率')), icon: Clock3, tone: 'blue', path: '/performance' },
  { label: '最新库区占用率', value: formatPercent(zoneOccupancy.value), unit: '', note: zoneOccupied.value + ' / ' + zoneCapacity.value + ' 库位', icon: Layers3, tone: zoneOccupancy.value >= Number(target('库区占用率') || 1) ? 'danger' : 'cyan', path: '/zones' },
  { label: '未关闭异常', value: openAlerts.value.length, unit: '条', note: '异常关闭率 ' + formatPercent(closeRate.value, 0), icon: AlertTriangle, tone: openAlerts.value.length ? 'danger' : 'cyan', path: '/exceptions' },
])

const todayOperations = computed(() => [
  { label: '拣货任务', value: latest.picking, unit: '项', icon: ScanLine },
  { label: '叉车任务', value: latest.forkliftTasks, unit: '项', icon: Truck },
  { label: '平均收货时长', value: latest.receiptMinutes, unit: '分钟', icon: Clock3 },
  { label: '平均拣货时长', value: latest.pickingMinutes, unit: '分钟', icon: ClipboardCheck },
  { label: '月台利用率', value: formatPercent(latest.dockUtilization), unit: '', icon: Activity },
  { label: '加班工时', value: latest.overtimeHours, unit: '小时', icon: Users },
])

const weekRows = reactive([])
const previousWeekRows = reactive([])
const yesterday = reactive({})
const relative = (current, previous) => previous ? (current - previous) / previous : 0

const businessSteps = computed(() => {
  if (!weekRows.length || !targets.length) return []
  const weekPeak = (key) => Math.max(...weekRows.map((row) => Number(row[key] || 0)), 1)
  const receivingTarget = target('入库及时率')
  const pickingTarget = target('平均拣货时长')
  const deliveryTarget = target('出库及时率')

  return [
    {
      code: '01', label: '成品入库', value: formatNumber(latest.inbound) + ' 箱', note: latest.inboundOrders + ' 张入库单',
      icon: PackageCheck, tone: 'blue', color: '#55b7ff', status: latest.receivingTimely >= receivingTarget ? '正常' : '关注',
      statusTone: latest.receivingTimely >= receivingTarget ? 'good' : 'warning', path: '/operations',
      trendLabel: '入库趋势', trendRows: weekRows, trendSeries: [{ name: '入库量', key: 'inbound', color: '#55b7ff' }],
      metrics: [
        { label: '今日入库箱数', value: formatNumber(latest.inbound) + ' 箱' },
        { label: '近7日均值', value: formatNumber(average(weekRows, 'inbound')) + ' 箱' },
        { label: '较昨日', value: formatDelta(relative(latest.inbound, yesterday.inbound)) },
        { label: '入库及时率', value: formatPercent(latest.receivingTimely) },
      ],
      progressLabel: '入库及时率', progressValue: formatPercent(latest.receivingTimely), progress: latest.receivingTimely * 100,
      detailLabel: '查看入库详情',
    },
    {
      code: '02', label: '叉车调度', value: formatNumber(latest.forkliftTasks) + ' 项', note: '当日叉车任务',
      icon: Truck, tone: 'cyan', color: '#55d9df', status: latest.exceptions === 0 ? '正常' : '关注',
      statusTone: latest.exceptions === 0 ? 'good' : 'warning', path: '/resources',
      trendLabel: '任务趋势', trendRows: weekRows, trendSeries: [{ name: '叉车任务', key: 'forkliftTasks', color: '#55d9df' }],
      metrics: [
        { label: '今日任务', value: formatNumber(latest.forkliftTasks) + ' 项' },
        { label: '近7日均值', value: formatNumber(average(weekRows, 'forkliftTasks'), 1) + ' 项' },
        { label: '较昨日', value: formatDelta(relative(latest.forkliftTasks, yesterday.forkliftTasks)) },
        { label: '加班工时', value: formatNumber(latest.overtimeHours, 1) + ' 小时' },
      ],
      progressLabel: '近7日峰值占比', progressValue: formatPercent(latest.forkliftTasks / weekPeak('forkliftTasks'), 0),
      progress: latest.forkliftTasks / weekPeak('forkliftTasks') * 100, detailLabel: '查看调度详情',
    },
    {
      code: '03', label: '拣货作业', value: formatNumber(latest.picking) + ' 项', note: '平均 ' + latest.pickingMinutes + ' 分钟',
      icon: ScanLine, tone: 'green', color: '#83d963', status: latest.pickingMinutes <= pickingTarget ? '正常' : '关注',
      statusTone: latest.pickingMinutes <= pickingTarget ? 'good' : 'warning', path: '/operations',
      trendLabel: '拣货趋势', trendRows: weekRows, trendSeries: [{ name: '拣货任务', key: 'picking', color: '#83d963' }],
      metrics: [
        { label: '今日任务', value: formatNumber(latest.picking) + ' 项' },
        { label: '平均时长', value: formatNumber(latest.pickingMinutes) + ' 分钟' },
        { label: '较昨日', value: formatDelta(relative(latest.picking, yesterday.picking)) },
        { label: '库存准确率', value: formatPercent(latest.inventoryAccuracy) },
      ],
      progressLabel: '时长目标达成', progressValue: latest.pickingMinutes <= pickingTarget ? '达标' : '预警',
      progress: Math.min(100, pickingTarget / Math.max(latest.pickingMinutes, 1) * 100), detailLabel: '查看拣货详情',
    },
    {
      code: '04', label: '成品出库', value: formatNumber(latest.outbound) + ' 箱', note: latest.outboundOrders + ' 张出库单',
      icon: Box, tone: 'amber', color: '#ffad3b', status: latest.deliveryTimely >= deliveryTarget ? '正常' : '关注',
      statusTone: latest.deliveryTimely >= deliveryTarget ? 'good' : 'warning', path: '/operations',
      trendLabel: '出库趋势', trendRows: weekRows, trendSeries: [{ name: '出库量', key: 'outbound', color: '#ffad3b' }],
      metrics: [
        { label: '今日出库箱数', value: formatNumber(latest.outbound) + ' 箱' },
        { label: '近7日均值', value: formatNumber(average(weekRows, 'outbound')) + ' 箱' },
        { label: '较昨日', value: formatDelta(relative(latest.outbound, yesterday.outbound)) },
        { label: '出库及时率', value: formatPercent(latest.deliveryTimely) },
      ],
      progressLabel: '出库及时率', progressValue: formatPercent(latest.deliveryTimely), progress: latest.deliveryTimely * 100,
      detailLabel: '查看出库详情',
    },
    {
      code: '05', label: '异常闭环', value: formatPercent(closeRate.value, 0), note: alerts.length + ' 条月度事件',
      icon: ShieldCheck, tone: 'violet', color: '#ad6dff', status: openAlerts.value.length === 0 ? '达成' : '关注',
      statusTone: openAlerts.value.length === 0 ? 'good' : 'warning', path: '/exceptions',
      trendLabel: '异常趋势', trendRows: weekRows, trendSeries: [{ name: '异常数', key: 'exceptions', color: '#ad6dff', smooth: false }],
      metrics: [
        { label: '未关闭异常', value: openAlerts.value.length + ' 条' },
        { label: '已关闭异常', value: closedAlerts.value.length + ' 条' },
        { label: '超 SLA', value: alerts.filter((item) => item.slaBreached).length + ' 条' },
        { label: '闭环及时率', value: formatPercent(closeRate.value, 0) },
      ],
      progressLabel: '异常关闭率', progressValue: formatPercent(closeRate.value, 0), progress: closeRate.value * 100,
      detailLabel: '查看异常详情',
    },
  ]
})
const weeklyReview = computed(() => {
  const inbound = sum(weekRows, 'inbound')
  const outbound = sum(weekRows, 'outbound')
  return [
    { label: '近7日入库', value: formatNumber(inbound), unit: '箱', delta: relative(inbound, sum(previousWeekRows, 'inbound')) },
    { label: '近7日出库', value: formatNumber(outbound), unit: '箱', delta: relative(outbound, sum(previousWeekRows, 'outbound')) },
    { label: '平均库存准确率', value: formatPercent(average(weekRows, 'inventoryAccuracy')), unit: '', delta: relative(average(weekRows, 'inventoryAccuracy'), average(previousWeekRows, 'inventoryAccuracy')) },
    { label: '平均出库及时率', value: formatPercent(average(weekRows, 'deliveryTimely')), unit: '', delta: relative(average(weekRows, 'deliveryTimely'), average(previousWeekRows, 'deliveryTimely')) },
  ]
})

function target(name) {
  return targets.find((item) => item.name === name)?.target
}

const kpis = computed(() => {
  if (!daily.length || !targets.length) return []
  const rows = [
    { name: '库存准确率', value: average(daily, 'inventoryAccuracy'), target: target('库存准确率'), unit: '%', lowerBetter: false },
    { name: '入库及时率', value: average(daily, 'receivingTimely'), target: target('入库及时率'), unit: '%', lowerBetter: false },
    { name: '出库及时率', value: average(daily, 'deliveryTimely'), target: target('出库及时率'), unit: '%', lowerBetter: false },
    { name: '库区占用率', value: zoneOccupancy.value, target: target('库区占用率'), unit: '%', lowerBetter: true },
    { name: '未关闭异常数', value: openAlerts.value.length, target: target('未关闭异常数'), unit: '条', lowerBetter: true },
    { name: '异常关闭率', value: closeRate.value, target: target('异常关闭率'), unit: '%', lowerBetter: false },
    { name: '平均拣货时长', value: average(daily, 'pickingMinutes'), target: target('平均拣货时长'), unit: '分钟', lowerBetter: true },
  ]
  return rows.map((item) => {
    const achieved = item.lowerBetter ? item.value <= item.target : item.value >= item.target
    return {
      ...item,
      achieved,
      display: item.unit === '%' ? formatPercent(item.value) : formatNumber(item.value, 1) + item.unit,
      targetDisplay: item.unit === '%' ? formatPercent(item.target, 0) : formatNumber(item.target) + item.unit,
      progress: achieved ? 100 : Math.max(8, Math.min(100, item.lowerBetter ? item.target / item.value * 100 : item.value / item.target * 100)),
    }
  })
})

const typeGroups = computed(() => Object.entries(alerts.reduce((groups, item) => {
  groups[item.type] = (groups[item.type] || 0) + 1
  return groups
}, {})).map(([label, value]) => ({ label, value })))

const ownerGroups = computed(() => Object.entries(alerts.reduce((groups, item) => {
  groups[item.owner] = (groups[item.owner] || 0) + 1
  return groups
}, {})).map(([label, value]) => ({ label, value })))

function zoneTone(zone) {
  if (zone.occupancy >= .85 || zone.abnormal > 0) return 'danger'
  if (zone.occupancy >= .75) return 'warning'
  return 'normal'
}

function formatDelta(value) {
  const number = Number(value || 0) * 100
  return (number >= 0 ? '+' : '') + number.toFixed(1) + '%'
}

function animateZoneOccupancy() {
  cancelAnimationFrame(zoneAnimationFrame)
  animatedZoneOccupancy.value = zones.map(() => 0)
  const reducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  if (reducedMotion) {
    animatedZoneOccupancy.value = zones.map((zone) => Number(zone.occupancy || 0))
    return
  }

  const duration = 1400
  let startedAt
  const tick = (timestamp) => {
    startedAt ??= timestamp
    const progress = Math.min((timestamp - startedAt) / duration, 1)
    const easedProgress = 1 - Math.pow(1 - progress, 3)
    animatedZoneOccupancy.value = zones.map((zone) => Number(zone.occupancy || 0) * easedProgress)
    if (progress < 1) zoneAnimationFrame = requestAnimationFrame(tick)
  }
  zoneAnimationFrame = requestAnimationFrame(tick)
}

function alertCarouselOffset(index) {
  const count = carouselAlerts.value.length
  if (!count) return 0
  let offset = index - activeAlertIndex.value
  if (offset > count / 2) offset -= count
  if (offset < -count / 2) offset += count
  return offset
}

function alertCarouselStyle(index) {
  const offset = alertCarouselOffset(index)
  const distance = Math.abs(offset)
  const translateY = offset * 43
  const translateZ = distance * -72
  const scale = Math.max(.7, 1 - distance * .11)
  return {
    transform: 'translate3d(0, calc(-50% + ' + translateY + 'px), ' + translateZ + 'px) scale(' + scale + ')',
    opacity: String(Math.max(.3, 1 - distance * .27)),
    zIndex: String(10 - distance),
    filter: 'brightness(' + Math.max(.62, 1 - distance * .16) + ') blur(' + Math.max(0, distance - 1) * .35 + 'px)',
  }
}

function startAlertCarousel() {
  clearInterval(alertCarouselTimer)
  const reducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  if (reducedMotion || carouselAlerts.value.length < 2) return
  alertCarouselTimer = setInterval(() => {
    if (!alertCarouselPaused.value) {
      activeAlertIndex.value = (activeAlertIndex.value + 1) % carouselAlerts.value.length
    }
  }, 2800)
}

function rotateAlerts(direction) {
  const count = carouselAlerts.value.length
  if (!count) return
  activeAlertIndex.value = (activeAlertIndex.value + direction + count) % count
  startAlertCarousel()
}

function selectAlert(index) {
  activeAlertIndex.value = index
  startAlertCarousel()
}

onMounted(() => triggerRefresh(true))
onBeforeUnmount(() => {
  cancelAnimationFrame(zoneAnimationFrame)
  clearInterval(alertCarouselTimer)
  window.clearTimeout(refreshTimer)
  window.clearTimeout(updatedTimer)
})

function severityTone(severity) {
  if (severity === '紧急') return 'urgent'
  if (severity === '重要') return 'important'
  return 'normal'
}

function open(path) {
  router.push({ path, query: { warehouse: meta.warehouseName || '成品库' } })
}

function openNavigation() {
  window.dispatchEvent(new CustomEvent('warehouse:open-navigation'))
}
</script>

<template>
  <div class="page finished-board-page">
    <section class="finished-board verified-board" :class="{ 'is-refreshing': refreshing }" aria-label="成品库运营信息看板">
      <header class="finished-header">
        <div class="finished-brand">
          <button class="finished-menu" type="button" aria-label="打开主导航" @click="openNavigation"><Menu :size="19" /></button>
          <div class="finished-logo"><Warehouse :size="26" /></div>
          <div><strong>{{ meta.warehouseId || '—' }}</strong><span>{{ meta.warehouseName || 'FINISHED GOODS' }}</span></div>
        </div>
        <div class="finished-title">
          <p>FINISHED GOODS OPERATION DASHBOARD</p>
          <h1>成品库运营信息看板</h1>
          <span>入库 · 库存 · 拣货 · 出库 · 异常</span>
        </div>
        <div class="source-header">
          <div><span>数据周期</span><strong>{{ meta.period }}</strong></div>
          <i />
          <div><span>最新快照</span><strong>{{ meta.latestDate }}</strong></div>
          <button class="source-sync" type="button" :disabled="refreshing" aria-label="刷新成品库看板数据" @click="triggerRefresh()">
            <i class="source-live-dot" /><RefreshCw :size="13" /><span>{{ loadError ? '数据库连接失败' : refreshing ? '正在刷新' : '数据已同步' }}</span>
          </button>
        </div>
      </header>

      <div class="metric-ribbon">
        <button v-for="(card, cardIndex) in metricCards" :key="card.label + refreshVersion" class="metric-tile" :class="'is-' + card.tone" :style="{ '--tile-index': cardIndex }" type="button" @click="open(card.path)">
          <span class="metric-icon"><component :is="card.icon" :size="31" :stroke-width="1.6" /></span>
          <span class="metric-copy">
            <small>{{ card.label }}</small>
            <strong><span v-count-up="typeof card.value === 'number' ? formatNumber(card.value) : card.value">{{ typeof card.value === 'number' ? formatNumber(card.value) : card.value }}</span><em>{{ card.unit }}</em></strong>
            <span>{{ card.note }}</span>
          </span>
          <span v-if="showUpdated" class="metric-updated">已更新</span>
        </button>
      </div>

      <div class="finished-grid verified-grid">
        <aside class="board-column verified-left">
          <article class="blue-panel source-trend-card" @click="open('/operations')">
            <header class="panel-heading"><div><span>01</span><h2>{{ daily.length }} 天成品入出库趋势</h2></div><small>单位：箱</small></header>
            <TrendChart :key="'main-trend-' + refreshVersion" class="finished-trend" :rows="daily" :series="trendSeries" :height="190" large-text draw-animation latest-pulse />
          </article>

          <article class="blue-panel source-operation-card" @click="open('/operations')">
            <header class="panel-heading"><div><span>02</span><h2>当日作业指标</h2></div><small>{{ meta.latestDate }}</small></header>
            <div :key="'operations-' + refreshVersion" class="source-operation-grid">
              <div v-for="(item, itemIndex) in todayOperations" :key="item.label" :style="{ '--item-index': itemIndex }">
                <component :is="item.icon" :size="17" />
                <span>{{ item.label }}</span>
                <strong><span v-count-up="typeof item.value === 'number' ? formatNumber(item.value, 1) : item.value">{{ typeof item.value === 'number' ? formatNumber(item.value, 1) : item.value }}</span><small>{{ item.unit }}</small></strong>
              </div>
            </div>
          </article>

          <article class="blue-panel source-week-card">
            <header class="panel-heading"><div><span>03</span><h2>近 7 日运营复盘</h2></div><small>对比前 7 日</small></header>
            <div :key="'week-' + refreshVersion" class="source-week-list">
              <div v-for="(item, itemIndex) in weeklyReview" :key="item.label" :style="{ '--item-index': itemIndex }">
                <span>{{ item.label }}</span>
                <strong><span v-count-up="item.value">{{ item.value }}</span><small>{{ item.unit }}</small></strong>
                <em :class="{ negative: item.delta < 0 }">{{ formatDelta(item.delta) }}</em>
              </div>
            </div>
          </article>
        </aside>

        <main class="zone-command verified-zone-command">
          <div class="zone-command-title"><span /><div><small>FINISHED GOODS BUSINESS OVERVIEW</small><h2>成品库业务总览</h2></div><span /></div>
          <div class="source-zone-body">
            <section class="business-overview">
              <div class="business-process">
                <template v-for="(step, index) in businessSteps" :key="step.code">
                  <button type="button" class="business-step is-detailed" :class="'is-' + step.tone" :style="{ '--process-index': index }" @click="open(step.path)">
                    <header class="business-card-head">
                      <span>{{ step.code }}</span><strong>{{ step.label }}</strong>
                      <em :class="'is-' + step.statusTone">{{ step.status }}</em>
                    </header>
                    <div class="business-card-summary">
                      <span class="business-icon"><component :is="step.icon" :size="24" /></span>
                      <div><strong>{{ step.value }}</strong><small>{{ step.note }}</small></div>
                    </div>
                    <div class="business-mini-chart">
                      <span>{{ step.trendLabel }}（近7日）</span>
                      <TrendChart :key="step.code + '-' + refreshVersion" :rows="step.trendRows" :series="step.trendSeries" :height="64" :show-legend="false" compact draw-animation />
                      <small>
                        <i>{{ step.trendRows[0]?.date?.slice(5).replace('-', '/') || '—' }}</i>
                        <i>{{ step.trendRows.at(-1)?.date?.slice(5).replace('-', '/') || '—' }}</i>
                      </small>
                    </div>
                    <div class="business-stat-grid">
                      <div v-for="metric in step.metrics" :key="metric.label"><span>{{ metric.label }}</span><strong>{{ metric.value }}</strong></div>
                    </div>
                    <div class="business-progress">
                      <div><span>{{ step.progressLabel }}</span><strong>{{ step.progressValue }}</strong></div>
                      <i><em :style="{ width: Math.min(step.progress, 100) + '%' }" /></i>
                    </div>
                    <b>{{ step.detailLabel }} <ChevronRight :size="12" /></b>
                  </button>
                  <ChevronRight v-if="index < businessSteps.length - 1" class="business-connector" :style="{ '--process-index': index }" :size="22" />
                </template>
              </div>
            </section>

            <section class="source-zone-section">
              <header><div><Layers3 :size="16" /><h3>库区负荷</h3></div><span>最新快照 {{ meta.latestDate }}</span></header>
              <div class="actual-zone-list">
                <button v-for="(zone, zoneIndex) in zones" :key="zone.code" type="button" class="actual-zone-card" :class="'is-' + zoneTone(zone)" @click="open('/zones/' + zone.code)">
                  <header><div><span>{{ zone.name }}</span><strong>{{ zone.code }}</strong></div><em>{{ zone.status }}</em></header>
                  <div class="zone-card-main">
                    <div class="actual-zone-ring" :style="{ '--zone-rate': formatPercent(animatedZoneOccupancy[zoneIndex]) }">
                      <div><strong>{{ formatPercent(animatedZoneOccupancy[zoneIndex], 0) }}</strong><span>占用率</span></div>
                    </div>
                    <dl>
                      <div><dt>可用库位</dt><dd>{{ formatNumber(zone.available) }} 个</dd></div>
                      <div><dt>已占库位</dt><dd>{{ formatNumber(zone.occupied) }} 个</dd></div>
                      <div><dt>容量库位</dt><dd>{{ formatNumber(zone.capacity) }} 个</dd></div>
                      <div><dt>冻结 / 异常</dt><dd>{{ zone.frozen }} / {{ zone.abnormal }}</dd></div>
                    </dl>
                  </div>
                  <footer><span><Users :size="13" />负责人 {{ zone.owner }}</span><span>进入区域详情 <ChevronRight :size="14" /></span></footer>
                </button>
              </div>
            </section>

            <section class="inventory-snapshot">
              <header><div><Box :size="18" /><h3>现存量快照</h3></div><span>{{ inventory[0]?.stockDate }}</span></header>
              <div class="inventory-summary is-five">
                <div><span>结存主数量</span><strong>{{ formatNumber(inventorySummary.onHand) }}</strong><small>箱</small></div>
                <div><span>预留主数量</span><strong>{{ formatNumber(inventorySummary.reserved) }}</strong><small>箱</small></div>
                <div><span>冻结主数量</span><strong>{{ formatNumber(inventorySummary.frozen) }}</strong><small>箱</small></div>
                <div><span>项目物料 SKU</span><strong>{{ inventorySummary.skuCount }}</strong><small>条</small></div>
                <div><span>项目数量</span><strong>{{ inventorySummary.projectCount }}</strong><small>个</small></div>
              </div>
            </section>
          </div>
        </main>

        <aside class="board-column verified-right">
          <article class="blue-panel source-kpi-card" @click="open('/performance')">
            <header class="panel-heading"><div><span>04</span><h2>KPI 目标达成</h2></div><small>月度均值 / 最新快照</small></header>
            <div :key="'kpi-' + refreshVersion" class="source-kpi-list">
              <div v-for="kpi in kpis" :key="kpi.name">
                <ShieldCheck :size="15" /><span>{{ kpi.name }}</span>
                <div><i :class="{ warning: !kpi.achieved }" :style="{ width: kpi.progress + '%' }" /></div>
                <strong>{{ kpi.display }}</strong><small>目标 {{ kpi.targetDisplay }}</small>
                <em :class="{ warning: !kpi.achieved }">{{ kpi.achieved ? '达标' : '预警' }}</em>
              </div>
            </div>
          </article>

          <article class="blue-panel source-exception-card" @click="open('/exceptions')">
            <header class="panel-heading"><div><span>05</span><h2>异常闭环概览</h2></div><small>共 {{ alerts.length }} 条</small></header>
            <div :key="'exception-' + refreshVersion" class="exception-summary is-five">
              <div><span>异常总数</span><strong v-count-up="alerts.length">{{ alerts.length }}</strong><small>条</small></div>
              <div><span>已关闭</span><strong v-count-up="closedAlerts.length">{{ closedAlerts.length }}</strong><small>条</small></div>
              <div :class="{ 'has-open-alerts': openAlerts.length > 0 }"><span>未关闭</span><strong v-count-up="openAlerts.length">{{ openAlerts.length }}</strong><small>条</small></div>
              <div class="is-complete"><span>关闭率</span><strong v-count-up="formatPercent(closeRate, 0)">{{ formatPercent(closeRate, 0) }}</strong></div>
              <div><span>超 SLA</span><strong v-count-up="alerts.filter((item) => item.slaBreached).length">{{ alerts.filter((item) => item.slaBreached).length }}</strong><small>条</small></div>
            </div>
            <div :key="'types-' + refreshVersion" class="source-type-list">
              <div v-for="item in typeGroups" :key="item.label"><span>{{ item.label }}</span><i><em :style="{ width: item.value / alerts.length * 100 + '%' }" /></i><strong>{{ item.value }} 条</strong></div>
            </div>
          </article>

          <article class="blue-panel source-alert-list" @click="open('/exceptions')">
            <header class="panel-heading"><div><span>06</span><h2>异常明细与责任人</h2></div><small>{{ ownerGroups.map((item) => item.label + ' ' + item.value + '条').join(' · ') }}</small></header>
            <div class="actual-alert-head"><span>级别</span><span>预警内容</span><span>发现时间</span><span>责任人</span><span>状态</span></div>
            <div
              class="actual-alert-rows is-carousel"
              @wheel.prevent.stop="rotateAlerts($event.deltaY >= 0 ? 1 : -1)"
              @mouseenter="alertCarouselPaused = true"
              @mouseleave="alertCarouselPaused = false"
            >
              <div
                v-for="(alert, alertIndex) in carouselAlerts"
                :key="alert.id"
                :class="['severity-' + severityTone(alert.severity), { 'is-current': alertIndex === activeAlertIndex }]"
                :style="alertCarouselStyle(alertIndex)"
                :aria-current="alertIndex === activeAlertIndex ? 'true' : undefined"
                @click.stop="selectAlert(alertIndex)"
              >
                <span class="alert-severity"><AlertTriangle :size="11" />{{ alert.severity }}</span>
                <strong>{{ alert.type }} · {{ alert.zone }}</strong>
                <small>{{ alert.time.slice(5) }}</small>
                <b>{{ alert.owner }}</b>
                <em>{{ alert.status }}</em>
              </div>
            </div>
          </article>
        </aside>
      </div>


    </section>
  </div>
</template>

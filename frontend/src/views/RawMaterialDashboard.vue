<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  AlertTriangle,
  ArrowDownToLine,
  ArrowUpFromLine,
  Box,
  Boxes,
  ChevronRight,
  CircleHelp,
  ClipboardList,
  Clock3,
  Forklift,
  Layers3,
  Maximize2,
  Menu,
  PackageCheck,
  RefreshCw,
  ShieldCheck,
  Timer,
} from 'lucide-vue-next'
import { useRouter } from 'vue-router'
import PageState from '../components/PageState.vue'
import IndustrialTank from '../components/IndustrialTank.vue'
import TrendChart from '../components/RawMaterialTrendChart.vue'
import { formatNumber, formatPercent } from '../composables/useDashboard'
import { useProjectRefresh } from '../composables/useProjectRefresh'
import { useRawMaterialDashboard } from '../composables/useRawMaterialDashboard'

const { snapshot, loading, error, refresh } = useRawMaterialDashboard()
const { refreshing, refreshVersion, refreshProject } = useProjectRefresh()
const router = useRouter()
const now = ref(new Date())
const alertViewport = ref(null)
const visibleAlertIndex = ref(0)
const alertScrollPaused = ref(false)
let clockTimer
let alertScrollTimer
let reducedMotionQuery

const summary = computed(() => snapshot.value?.summary || {})
const trend = computed(() => snapshot.value?.trend || [])
const zones = computed(() => snapshot.value?.zones || [])
const stocks = computed(() => snapshot.value?.stocks || [])
const rankedZones = computed(() => [...zones.value].sort((a, b) => Number(b.occupancy || 0) - Number(a.occupancy || 0)))
const severityRank = { '紧急': 3, '重要': 2, '一般': 1 }
const alerts = computed(() => [...(snapshot.value?.openExceptions || [])]
  .sort((a, b) => {
    if (a.slaBreached !== b.slaBreached) return a.slaBreached ? -1 : 1
    if (a.severity !== b.severity) return Number(severityRank[b.severity] || 0) - Number(severityRank[a.severity] || 0)
    return Number(b.durationHours || 0) - Number(a.durationHours || 0)
  }))
const breachedAlertCount = computed(() => alerts.value.filter((alert) => alert.slaBreached).length)
const alertSeverityCounts = computed(() => ({
  high: alerts.value.filter((alert) => alert.severity === '紧急').length,
  medium: alerts.value.filter((alert) => alert.severity === '重要').length,
  low: alerts.value.filter((alert) => alert.severity === '一般').length,
}))
const netInventoryChange = computed(() => Number(summary.value.monthRawInbound || 0) - Number(summary.value.monthRawOutbound || 0))
const todayFlowBalance = computed(() => Number(summary.value.todayRawInbound || 0) - Number(summary.value.todayRawOutbound || 0))
const monthOrderTotal = computed(() => Number(summary.value.monthInboundOrders || 0) + Number(summary.value.monthOutboundOrders || 0))
const monthHandlingTotal = computed(() => Number(summary.value.monthPickingTasks || 0) + Number(summary.value.monthForkliftTasks || 0))
const monthDailyOrders = computed(() => Math.round(monthOrderTotal.value / 31))
const monthDailyHandling = computed(() => Math.round(monthHandlingTotal.value / 31))
const avgAlertAgeHours = computed(() => alerts.value.length
  ? alerts.value.reduce((total, alert) => total + Number(alert.durationHours || 0), 0) / alerts.value.length
  : 0)
const latestFlowComparison = computed(() => {
  const current = trend.value.at(-1) || {}
  const previous = trend.value.at(-2) || {}
  const change = (value, baseline) => baseline ? ((Number(value || 0) - Number(baseline)) / Number(baseline)) * 100 : 0
  return {
    inbound: change(current.rawInbound, previous.rawInbound),
    outbound: change(current.rawOutbound, previous.rawOutbound),
  }
})

const tankProfiles = {
  '210300000771': { id: 'RM-T01', capacityTon: 160, density: 0.86, materialForm: 'powder', materialFormLabel: '粉料' },
  '210100000792': { id: 'RM-T02', capacityTon: 120, density: 0.72, materialForm: 'granule', materialFormLabel: '颗粒' },
  '210100000793': { id: 'RM-T03', capacityTon: 120, density: 0.65, materialForm: 'roll', materialFormLabel: '卷材' },
  '210100000781': { id: 'RM-T04', capacityTon: 100, density: 1.02, materialForm: 'liquid', materialFormLabel: '液体' },
}

const materialTanks = computed(() => stocks.value.map((stock, index) => {
  const profile = tankProfiles[stock.code] || {
    id: `RM-T${String(index + 1).padStart(2, '0')}`,
    capacityTon: Math.ceil(Number(stock.onHand || 0) / 10) * 10,
    density: 0.75,
    materialForm: 'granule',
    materialFormLabel: '固体',
  }
  const fillRate = Math.min(Number(stock.onHand || 0) / profile.capacityTon, 1)
  return {
    ...stock,
    ...profile,
    fillRate,
    state: fillRate >= 0.85 ? '高位' : fillRate <= 0.25 ? '低位' : '正常',
  }
}))

const flowSeries = computed(() => {
  return [
    { name: '原料入库', key: 'rawInbound', color: '#14d8d0', area: true, symbol: 'circle', lineWidth: 2.8 },
    { name: '生产领用', key: 'rawOutbound', color: '#f7b942', lineStyle: 'dashed', symbol: 'diamond', lineWidth: 2.4 },
  ]
})

const riskTargetRows = computed(() => [
  {
    key: 'occupancy',
    name: '库区占用率',
    display: formatPercent(summary.value.occupancy),
    target: '目标 ≤ 85%',
    met: Number(summary.value.occupancy || 0) <= 0.85,
  },
  {
    key: 'openExceptions',
    name: '未关闭异常',
    display: `${formatNumber(summary.value.openExceptions)} 项`,
    target: '目标 ≤ 10 项',
    met: Number(summary.value.openExceptions || 0) <= 10,
  },
  {
    key: 'exceptionCloseRate',
    name: '异常关闭率',
    display: formatPercent(summary.value.exceptionCloseRate),
    target: '目标 ≥ 90%',
    met: Number(summary.value.exceptionCloseRate || 0) >= 0.9,
  },
  {
    key: 'avgAlertAge',
    name: '平均未闭环时长',
    display: `${avgAlertAgeHours.value.toFixed(1)} 小时`,
    target: '目标 ≤ 48 小时',
    met: avgAlertAgeHours.value <= 48,
  },
])

function formatTon(value, digits = 3) {
  return Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: digits, maximumFractionDigits: digits })
}

function formatSignedTon(value) {
  const amount = Number(value || 0)
  return `${amount >= 0 ? '+' : ''}${formatTon(amount)}`
}

function formatSignedPercent(value) {
  const amount = Number(value || 0)
  return `${amount >= 0 ? '+' : ''}${amount.toFixed(1)}%`
}

function statusClass(status) {
  if (status === '高风险') return 'danger'
  if (status === '偏高') return 'warning'
  return 'normal'
}

function alertLevelClass(alert) {
  if (alert.slaBreached || alert.severity === '紧急') return 'danger'
  if (alert.severity === '重要') return 'warning'
  return 'normal'
}

function alertLevelLabel(alert) {
  if (alert.slaBreached || alert.severity === '紧急') return '高风险'
  if (alert.severity === '重要') return '中风险'
  return '低风险'
}

function openNavigation() {
  window.dispatchEvent(new CustomEvent('warehouse:open-navigation'))
}

function navigateTo(path) {
  router.push(path)
}

async function toggleFullscreen() {
  try {
    if (document.fullscreenElement) await document.exitFullscreen()
    else await document.querySelector('.raw-material-page')?.requestFullscreen?.()
  } catch {}
}

function updateAlertPosition() {
  const viewport = alertViewport.value
  const row = viewport?.querySelector('.raw-alert-list-item')
  if (!viewport || !row) return
  visibleAlertIndex.value = Math.min(alerts.value.length - 1, Math.max(0, Math.round(viewport.scrollTop / (row.offsetHeight + 5))))
}

function advanceAlertList() {
  const viewport = alertViewport.value
  const row = viewport?.querySelector('.raw-alert-list-item')
  if (!viewport || !row) return
  const step = row.offsetHeight + 5
  const reachedEnd = viewport.scrollTop + viewport.clientHeight >= viewport.scrollHeight - step
  viewport.scrollTo({ top: reachedEnd ? 0 : viewport.scrollTop + step, behavior: 'smooth' })
}

function stopAlertScroll() {
  window.clearInterval(alertScrollTimer)
  alertScrollTimer = undefined
}

function startAlertScroll() {
  stopAlertScroll()
  if (alerts.value.length < 2 || alertScrollPaused.value || reducedMotionQuery?.matches) return
  alertScrollTimer = window.setInterval(advanceAlertList, 2400)
}

function pauseAlertScroll() {
  alertScrollPaused.value = true
  stopAlertScroll()
}

function resumeAlertScroll() {
  alertScrollPaused.value = false
  startAlertScroll()
}

function handleMotionPreference() {
  if (reducedMotionQuery?.matches) stopAlertScroll()
  else startAlertScroll()
}

onMounted(() => {
  clockTimer = window.setInterval(() => { now.value = new Date() }, 1000)
  reducedMotionQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
  reducedMotionQuery.addEventListener('change', handleMotionPreference)
  startAlertScroll()
})

onBeforeUnmount(() => {
  window.clearInterval(clockTimer)
  stopAlertScroll()
  reducedMotionQuery?.removeEventListener('change', handleMotionPreference)
})
</script>

<template>
  <div class="page raw-material-page">
    <PageState :loading="loading && !snapshot" :error="error" @retry="refresh">
      <section class="raw-board" aria-label="原料库运营看板">
        <header class="raw-masthead">
          <div class="raw-brand">
            <button class="raw-mobile-menu" type="button" aria-label="打开主导航" @click="openNavigation"><Menu :size="20" /></button>
            <div class="raw-brand-mark" aria-hidden="true"><Box :size="31" :stroke-width="1.7" /></div>
            <div><p>RAW MATERIAL WAREHOUSE · {{ snapshot?.meta?.warehouseId }}</p><strong>生产保障 · 稳定供应 · 高效协同</strong></div>
          </div>
          <div class="raw-title">
            <h1>原料库运营看板</h1>
            <span>{{ snapshot?.meta?.period }}</span>
          </div>
          <div class="raw-time">
            <div class="raw-clock"><strong>{{ now.toLocaleTimeString('zh-CN', { hour12: false }) }}</strong><span>{{ now.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', weekday: 'short' }) }}</span></div>
            <span class="raw-data-stamp"><i />数据截至 {{ snapshot?.meta?.latestDate }}<small>每 30 秒自动刷新</small></span>
            <button class="raw-control" type="button" :class="{ spinning: refreshing || loading }" :disabled="refreshing || loading" aria-label="刷新全部数据" @click="refreshProject('manual')"><RefreshCw :size="17" /><span>刷新</span></button>
            <button class="raw-control" type="button" aria-label="切换全屏显示" @click="toggleFullscreen"><Maximize2 :size="17" /><span>全屏</span></button>
          </div>
        </header>

        <div class="raw-kpi-strip">
          <article class="raw-kpi-card">
            <span class="raw-kpi-icon mint"><Boxes /></span>
            <div><small>原料库存量</small><strong>{{ formatTon(summary.stockOnHandTon) }}<em>吨</em></strong><p>可用库存 {{ formatTon(summary.stockAvailableTon) }} 吨</p></div>
            <button class="raw-kpi-arrow" type="button" aria-label="查看空间与库存" @click="navigateTo('/zones')"><ChevronRight /></button>
          </article>
          <article class="raw-kpi-card">
            <span class="raw-kpi-icon mint round"><ArrowDownToLine /></span>
            <div><small>今日原料入库</small><strong>{{ formatTon(summary.todayRawInbound) }}<em>吨</em></strong><p>较昨日 <b class="positive">{{ formatSignedPercent(latestFlowComparison.inbound) }}</b></p></div>
            <button class="raw-kpi-arrow" type="button" aria-label="查看入库作业" @click="navigateTo('/operations')"><ChevronRight /></button>
          </article>
          <article class="raw-kpi-card">
            <span class="raw-kpi-icon mint round"><ArrowUpFromLine /></span>
            <div><small>今日生产领用</small><strong>{{ formatTon(summary.todayRawOutbound) }}<em>吨</em></strong><p>较昨日 <b class="positive">{{ formatSignedPercent(latestFlowComparison.outbound) }}</b></p></div>
            <button class="raw-kpi-arrow" type="button" aria-label="查看领用作业" @click="navigateTo('/operations')"><ChevronRight /></button>
          </article>
          <article class="raw-kpi-card" :class="{ 'is-warning': Number(summary.occupancy || 0) > 0.85 }">
            <span class="raw-kpi-icon amber"><Layers3 /></span>
            <div><small>库区占用率</small><strong>{{ formatPercent(summary.occupancy) }}</strong><p>较警戒线 <b class="warning">+{{ ((Number(summary.occupancy || 0) - 0.85) * 100).toFixed(1) }} 个百分点</b></p></div>
            <button class="raw-kpi-arrow" type="button" aria-label="查看库区占用明细" @click="navigateTo('/zones')"><ChevronRight /></button>
          </article>
          <article class="raw-kpi-card" :class="{ 'is-danger': Number(summary.openExceptions || 0) > 10 }">
            <span class="raw-kpi-icon rose"><AlertTriangle /></span>
            <div><small>未关闭异常</small><strong>{{ formatNumber(summary.openExceptions) }}<em>项</em></strong><p>SLA 超时 <b class="danger">{{ formatNumber(summary.slaBreached) }} 条</b></p></div>
            <button class="raw-kpi-arrow" type="button" aria-label="查看异常明细" @click="navigateTo('/exceptions')"><ChevronRight /></button>
          </article>
        </div>

        <div class="raw-board-grid">
          <article class="raw-panel raw-flow-panel">
            <span class="raw-panel-accent" />
            <header class="raw-panel-title split"><h2>近 31 日原料收发趋势 <CircleHelp /></h2><p>单位：吨</p></header>
            <div class="raw-today-pair">
              <div><span>7 月累计入库</span><strong>{{ formatTon(summary.monthRawInbound) }}</strong><small>吨</small></div>
              <div><span>7 月累计领用</span><strong>{{ formatTon(summary.monthRawOutbound) }}</strong><small>吨</small></div>
              <div><span>净变化</span><strong>{{ formatSignedTon(netInventoryChange) }}</strong><small>吨</small><em>较昨日</em></div>
            </div>
            <TrendChart :key="refreshVersion" class="raw-trend-chart" :rows="trend" :series="flowSeries" :height="210" :show-axis-unit="false" category-boundary-gap nice-y-axis :y-axis-split-number="5" :axis-bottom="15" :x-axis-label-margin="10" unit="吨" />
            <div class="raw-flow-foot"><span>月度净补库</span><strong>{{ formatSignedTon(netInventoryChange) }} 吨</strong><em>今日净变化 {{ formatSignedTon(todayFlowBalance) }} 吨</em><button class="raw-panel-link" type="button" aria-label="查看原料收发详情" @click="navigateTo('/operations')">查看详情<ChevronRight /></button></div>
          </article>

          <article class="raw-panel raw-posture-panel">
            <span class="raw-panel-accent centered-accent" />
            <header class="raw-panel-title split"><h2>原料库实时储罐状态 <CircleHelp /></h2><div class="raw-panel-head-actions"><p>{{ snapshot?.meta?.areaCount }} 个罐区 · {{ materialTanks.length }} 筒物料 · <b>在线 {{ materialTanks.length }} 台</b></p><button class="raw-panel-link" type="button" aria-label="查看全部储罐" @click="navigateTo('/zones')">查看全部<ChevronRight /></button></div></header>
            <div class="raw-silo-overview" :aria-label="`${materialTanks.length} 个原料筒实时存量概况`">
              <article
                v-for="(tank, index) in materialTanks"
                :key="`${tank.id}-${refreshVersion}`"
                class="raw-silo-card"
                :class="[`is-${tank.materialForm}`, { 'is-high': tank.state === '高位', 'is-low': tank.state === '低位' }]"
                :style="{ '--tank-delay': `${index * 120}ms` }"
                :aria-label="`${tank.id} ${tank.name}，当前 ${formatTon(tank.onHand)} 吨，容量 ${tank.capacityTon} 吨，装填率 ${formatPercent(tank.fillRate, 0)}，${tank.state}`"
              >
                <header class="raw-silo-card-head">
                  <span><b>{{ tank.id }}</b><em>· {{ tank.name }}</em></span>
                  <small>{{ tank.state === '正常' ? '● 正常' : tank.state }}</small>
                </header>
                <div class="raw-silo-card-body">
                  <div class="raw-silo-model"><IndustrialTank :fill-rate="tank.fillRate" :material-form="tank.materialForm" :animation-key="refreshVersion" /></div>
                  <div class="raw-silo-copy">
                    <strong class="raw-silo-rate">{{ formatPercent(tank.fillRate, 0) }}<em>占用率</em></strong>
                    <strong class="raw-silo-quantity">{{ formatTon(tank.onHand) }}<em>吨</em></strong>
                    <p>可用 {{ formatTon(tank.available) }} 吨</p>
                    <dl><div><dt>罐容</dt><dd>{{ tank.capacityTon }} 吨</dd></div><div><dt>物料密度</dt><dd>{{ tank.density.toFixed(2) }} t/m³</dd></div></dl>
                  </div>
                </div>
                <div class="raw-silo-progress" aria-hidden="true"><i :style="{ width: formatPercent(tank.fillRate, 0) }" /></div>
              </article>
            </div>
            <div class="raw-posture-metrics">
              <div><span>筒内总量</span><strong>{{ formatTon(summary.stockOnHandTon) }}</strong><small>吨</small></div>
              <div><span>可用库存</span><strong>{{ formatTon(summary.stockAvailableTon) }}</strong><small>吨</small></div>
              <div><span>预留库存</span><strong>{{ formatTon(summary.stockReservedTon) }}</strong><small>吨</small></div>
              <div><span>冻结库存</span><strong>{{ formatTon(summary.stockFrozenTon) }}</strong><small>吨</small></div>
              <button class="raw-posture-link" type="button" aria-label="查看罐区详情" @click="navigateTo('/zones')">查看罐区详情<ChevronRight /></button>
            </div>
          </article>

          <article class="raw-panel raw-month-panel">
            <span class="raw-panel-accent" />
            <header class="raw-panel-title split"><h2>本月保障能力</h2><div class="raw-panel-head-actions"><p>31 天历史口径</p><button class="raw-panel-link" type="button" aria-label="查看本月保障详情" @click="navigateTo('/performance')">查看详情<ChevronRight /></button></div></header>
            <div class="raw-month-section-label"><span>今日运营概况</span><em>实时保障节奏</em></div>
            <div class="raw-month-overview">
              <div class="is-orders">
                <span class="raw-month-icon"><ClipboardList /></span>
                <div class="raw-month-primary"><span>收发单量</span><strong>{{ formatNumber(monthOrderTotal) }}<small>单</small></strong><em>日均 {{ formatNumber(monthDailyOrders) }}</em></div>
                <div class="raw-month-breakdown"><span><i>入库</i><b>{{ formatNumber(summary.monthInboundOrders) }}</b></span><span><i>出库</i><b>{{ formatNumber(summary.monthOutboundOrders) }}</b></span></div>
              </div>
              <div class="is-tasks">
                <span class="raw-month-icon"><Forklift /></span>
                <div class="raw-month-primary"><span>搬运任务</span><strong>{{ formatNumber(monthHandlingTotal) }}<small>项</small></strong><em>日均 {{ formatNumber(monthDailyHandling) }}</em></div>
                <div class="raw-month-breakdown"><span><i>拣货</i><b>{{ formatNumber(summary.monthPickingTasks) }}</b></span><span><i>叉车</i><b>{{ formatNumber(summary.monthForkliftTasks) }}</b></span></div>
              </div>
            </div>
            <div class="raw-month-quality">
              <div><ShieldCheck /><span>库存准确</span><strong>{{ formatPercent(summary.inventoryAccuracy) }}</strong></div>
              <div><ArrowDownToLine /><span>收货及时</span><strong>{{ formatPercent(summary.receivingTimely) }}</strong></div>
              <div><ArrowUpFromLine /><span>发运及时</span><strong>{{ formatPercent(summary.deliveryTimely) }}</strong></div>
              <div><PackageCheck /><span>拣货耗时</span><strong>{{ summary.avgPickingMinutes }}<small>分</small></strong></div>
            </div>
            <div class="raw-month-foot">
              <span><Timer /><em>收货耗时</em><strong>{{ summary.avgReceiptMinutes }} 分</strong></span>
              <span><ClipboardList /><em>日均单量</em><strong>{{ formatNumber(monthDailyOrders) }} 单</strong></span>
              <span><Clock3 /><em>累计加班</em><strong>{{ summary.overtimeHours }}h</strong></span>
            </div>
          </article>

          <article class="raw-panel raw-zone-panel">
            <span class="raw-panel-accent centered-accent" />
            <header class="raw-panel-title split"><h2>库区空间压力</h2><div class="raw-panel-head-actions"><p>容量 · 责任人</p><button class="raw-panel-link" type="button" aria-label="查看全部库区" @click="navigateTo('/zones')">查看全部<ChevronRight /></button></div></header>
            <div class="raw-zone-overview">
              <div><span>加权占用率</span><strong>{{ formatPercent(summary.occupancy) }}</strong><small>{{ formatNumber(summary.occupiedLocations) }} / {{ formatNumber(snapshot?.meta?.capacityLocations) }} 库位</small></div>
              <p><i class="danger" />高风险 {{ zones.filter((zone) => zone.status === '高风险').length }} 区<i class="warning" />偏高 {{ zones.filter((zone) => zone.status === '偏高').length }} 区</p>
            </div>
            <div class="raw-zone-grid">
              <div v-for="zone in rankedZones" :key="zone.code" class="raw-zone-card" :class="statusClass(zone.status)">
                <header><span><strong>{{ zone.code }}</strong><small>{{ zone.name }}</small></span><em :class="statusClass(zone.status)">{{ formatPercent(zone.occupancy) }}</em></header>
                <div><i :class="statusClass(zone.status)" :style="{ width: formatPercent(zone.occupancy) }" /></div>
                <p><span>已用 {{ zone.occupied }}/{{ zone.capacity }}</span><b>可用 {{ zone.available }}</b></p>
                <footer><span>{{ zone.owner }}</span><span>{{ zone.materialTypes }} 类物料</span><strong v-if="zone.abnormalLocations">异常 {{ zone.abnormalLocations }}</strong><em v-else-if="zone.frozenQty">冻结 {{ zone.frozenQty }}</em><small v-else>状态正常</small></footer>
              </div>
            </div>
            <div class="raw-zone-foot"><button class="raw-panel-link" type="button" aria-label="查看库位明细" @click="navigateTo('/zones')">库位明细<ChevronRight /></button></div>
          </article>

          <article class="raw-panel raw-risk-panel">
            <span class="raw-panel-accent" />
            <header class="raw-panel-title split"><h2>服务质量与风险</h2><div class="raw-panel-head-actions"><p>目标达成与异常明细</p><button class="raw-panel-link" type="button" aria-label="查看全部风险异常" @click="navigateTo('/exceptions')">查看全部<ChevronRight /></button></div></header>
            <div class="raw-target-strip">
              <div v-for="target in riskTargetRows" :key="target.key"><span>{{ target.name }}</span><strong :class="{ warning: !target.met }">{{ target.display }}</strong><small>{{ target.target }}</small></div>
            </div>
            <div class="raw-alert-subhead"><span>异常事件（按时间降序）</span><em>共 {{ alerts.length }} 条</em></div>
            <div
              ref="alertViewport"
              class="raw-alert-viewport"
              tabindex="0"
              :aria-label="`全部 ${alerts.length} 条异常明细，普通纵向滚动播放；滚动条可拖动，聚焦或悬停可暂停`"
              @scroll.passive="updateAlertPosition"
              @mouseenter="pauseAlertScroll"
              @mouseleave="resumeAlertScroll"
              @focusin="pauseAlertScroll"
              @focusout="resumeAlertScroll"
              @pointerdown="pauseAlertScroll"
              @pointerup="resumeAlertScroll"
            >
              <div class="raw-alert-list" role="list">
                <div v-for="alert in alerts" :key="alert.id" class="raw-alert-list-item" :class="{ 'is-breached': alert.slaBreached }" role="listitem">
                  <span :class="alert.slaBreached || alert.severity === '紧急' || alert.severity === '重要' ? 'danger' : 'warning'"><AlertTriangle :size="12" /></span>
                  <div><strong>{{ alert.type }} · {{ alert.material }}</strong><small><b>{{ alert.slaBreached ? 'SLA 超时' : alert.severity }}</b>{{ alert.areaCode }} · {{ alert.action }} · {{ alert.owner }}</small></div>
                  <div class="raw-alert-end"><em>{{ alert.durationHours }}h</em><span :class="alertLevelClass(alert)">{{ alertLevelLabel(alert) }}</span></div>
                </div>
              </div>
            </div>
            <div class="raw-risk-foot">
              <div class="raw-risk-summary"><strong>全部异常 {{ alerts.length }} 条</strong><span class="danger">高风险 {{ alertSeverityCounts.high }} 条</span><span class="warning">中风险 {{ alertSeverityCounts.medium }} 条</span><span>低风险 {{ alertSeverityCounts.low }} 条</span></div>
              <p><span>{{ Math.min(visibleAlertIndex + 1, alerts.length) }}/{{ alerts.length }}</span><button class="raw-risk-sla" type="button" aria-label="查看 SLA 超时异常" @click="navigateTo('/exceptions')">SLA 超时 <strong>{{ breachedAlertCount }}</strong></button></p>
            </div>
          </article>
        </div>
      </section>
    </PageState>
  </div>
</template>

<style scoped>
.raw-material-page {
  --rm-page-surface:
    radial-gradient(circle at 12% 8%, rgba(35, 211, 193, .18), transparent 28%),
    radial-gradient(circle at 88% 14%, rgba(79, 139, 255, .2), transparent 30%),
    radial-gradient(circle at 52% 106%, rgba(20, 93, 130, .24), transparent 42%),
    linear-gradient(145deg, #02070d 0%, #071522 48%, #030a12 100%);
  --rm-page-haze:
    radial-gradient(ellipse at 18% 34%, rgba(45, 212, 191, .2) 0 8%, transparent 26%),
    radial-gradient(ellipse at 78% 22%, rgba(93, 164, 255, .2) 0 9%, transparent 28%),
    radial-gradient(ellipse at 66% 78%, rgba(38, 104, 146, .22) 0 11%, transparent 30%);
  --rm-noise-opacity: .055;
  position: relative;
  display: grid;
  width: 100%;
  height: 100dvh;
  min-height: 100dvh;
  place-items: center;
  overflow: hidden;
  padding: 0;
  isolation: isolate;
  background: var(--rm-page-surface);
}
.raw-material-page::before {
  position: absolute;
  z-index: -2;
  inset: -18%;
  content: "";
  pointer-events: none;
  background: var(--rm-page-haze);
  filter: blur(42px);
}
.raw-material-page::after {
  position: absolute;
  z-index: 20;
  inset: 0;
  content: "";
  pointer-events: none;
  opacity: var(--rm-noise-opacity);
  background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 180 180' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noise'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='.88' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noise)' opacity='.75'/%3E%3C/svg%3E");
  mix-blend-mode: soft-light;
}
.raw-board {
  --rm-accent: #35d3c7;
  --rm-cyan: #7db7ff;
  --rm-flow-out: #f2b84b;
  --rm-warning: #f2b84b;
  --rm-danger: #ff7b8b;
  --rm-bg: rgba(5, 17, 28, .7);
  --rm-panel: rgba(12, 31, 45, .62);
  --rm-panel-strong: rgba(15, 39, 56, .7);
  --rm-text: #f2f7fb;
  --rm-muted: #9cb0c1;
  --rm-dim: #71889a;
  --rm-line: rgba(126, 166, 191, .2);
  --rm-board-border: rgba(171, 216, 238, .2);
  --rm-board-surface:
    linear-gradient(rgba(181, 224, 243, .026) 1px, transparent 1px),
    linear-gradient(90deg, rgba(181, 224, 243, .026) 1px, transparent 1px),
    radial-gradient(circle at 50% -18%, rgba(53, 211, 199, .15), transparent 38%),
    radial-gradient(circle at 100% 8%, rgba(125, 183, 255, .11), transparent 34%),
    var(--rm-bg);
  --rm-board-shadow: 0 28px 80px rgba(0, 0, 0, .42), inset 0 1px rgba(255, 255, 255, .1);
  --rm-board-blur: 28px;
  --rm-board-saturation: 132%;
  --rm-panel-border: rgba(157, 203, 226, .19);
  --rm-panel-surface: linear-gradient(145deg, rgba(23, 50, 67, .58), rgba(6, 21, 34, .62));
  --rm-panel-shadow: inset 0 1px rgba(255,255,255,.1), 0 14px 32px rgba(0,0,0,.14);
  --rm-panel-sheen: linear-gradient(130deg, rgba(255,255,255,.045), transparent 28%), radial-gradient(circle at 50% 0, rgba(125,183,255,.08), transparent 38%);
  --rm-panel-sheen-opacity: .74;
  --rm-panel-blur: 18px;
  --rm-panel-saturation: 126%;
  --rm-kpi-border: rgba(167, 211, 232, .2);
  --rm-kpi-surface: linear-gradient(145deg, rgba(29, 59, 78, .56), rgba(8, 24, 37, .5));
  --rm-kpi-shadow: inset 0 1px rgba(255,255,255,.09), 0 12px 24px rgba(0,0,0,.12);
  --rm-inner-surface: linear-gradient(145deg, rgba(22, 51, 69, .56), rgba(8, 28, 42, .43));
  --rm-deep-surface: linear-gradient(145deg, rgba(13,39,55,.42), rgba(6,24,37,.5));
  --rm-soft-surface: rgba(7,27,41,.25);
  position: relative;
  z-index: 1;
  display: flex;
  width: min(100%, calc(100dvh * 16 / 9));
  height: auto;
  max-height: 100dvh;
  aspect-ratio: 16 / 9;
  flex-direction: column;
  overflow: hidden;
  padding: 0 clamp(9px, .9cqw, 16px) clamp(9px, .9cqw, 16px);
  border: 1px solid var(--rm-board-border);
  border-radius: 18px;
  color: var(--rm-text);
  container-type: inline-size;
  font-family: "Noto Sans SC", "Microsoft YaHei UI", "Microsoft YaHei", sans-serif;
  background: var(--rm-board-surface);
  background-size: 34px 34px, 34px 34px, auto, auto, auto;
  box-shadow: var(--rm-board-shadow);
  backdrop-filter: blur(var(--rm-board-blur)) saturate(var(--rm-board-saturation));
  -webkit-backdrop-filter: blur(var(--rm-board-blur)) saturate(var(--rm-board-saturation));
}
.raw-board strong, .raw-board em { font-variant-numeric: tabular-nums; }
.raw-masthead { display: grid; height: clamp(52px, 4.7cqw, 80px); flex: 0 0 auto; grid-template-columns: minmax(0, 1fr) minmax(280px, 1.1fr) minmax(0, 1fr); align-items: center; gap: clamp(8px, 1cqw, 18px); border-bottom: 1px solid var(--rm-line); }
.raw-brand { display: flex; align-items: center; gap: 11px; }
.raw-brand-mark { display: grid; width: 44px; height: 44px; flex: 0 0 44px; place-items: center; border: 1px solid color-mix(in srgb, var(--rm-accent) 40%, transparent); border-radius: 12px; color: var(--rm-accent); background: color-mix(in srgb, var(--rm-accent) 9%, transparent); box-shadow: inset 0 0 22px color-mix(in srgb, var(--rm-accent) 7%, transparent); }
.raw-brand p, .raw-title p { margin: 0; color: var(--rm-accent); font: 700 clamp(7px, .55cqw, 10px)/1.2 "Bahnschrift", "Segoe UI", sans-serif; letter-spacing: .2em; }
.raw-brand strong { display: block; margin-top: 7px; font-size: clamp(10px, .72cqw, 13px); letter-spacing: .03em; }
.raw-mobile-menu { display: none; width: 40px; height: 40px; align-items: center; justify-content: center; cursor: pointer; border: 1px solid color-mix(in srgb, var(--rm-accent) 28%, transparent); border-radius: 11px; color: var(--rm-accent); background: color-mix(in srgb, var(--rm-accent) 8%, transparent); }
.raw-title { text-align: center; }
.raw-title p { color: var(--rm-cyan); font-size: clamp(8px, .5cqw, 10px); }
.raw-title h1 { margin: 7px 0 4px; font-size: clamp(23px, 1.9cqw, 34px); line-height: 1; letter-spacing: .08em; text-shadow: 0 0 26px rgba(56, 189, 248, .11); }
.raw-title span { color: var(--rm-muted); font-size: clamp(9px, .6cqw, 11px); }
.raw-title-meta { display: flex; min-height: 22px; align-items: center; justify-content: center; gap: 8px; }
.raw-theme-picker { display: inline-flex; height: 22px; align-items: center; gap: 4px; padding: 0 6px; border: 1px solid var(--rm-line); border-radius: 8px; color: var(--rm-accent); background: rgba(255,255,255,.035); transition: border-color .2s ease, background-color .2s ease, box-shadow .2s ease; }
.raw-theme-picker:focus-within { border-color: var(--rm-accent); box-shadow: 0 0 0 2px color-mix(in srgb, var(--rm-accent) 16%, transparent); }
.raw-theme-picker svg { width: 11px; height: 11px; flex: 0 0 11px; stroke-width: 1.8; }
.raw-theme-picker select { max-width: 92px; cursor: pointer; border: 0; outline: 0; color: var(--rm-text); color-scheme: dark; font: 600 clamp(7px, .48cqw, 9px) "Noto Sans SC", "Microsoft YaHei UI", sans-serif; background: transparent; }
.raw-theme-picker option { color: #eef5fa; background: #102537; }
.raw-time { display: flex; align-items: center; justify-content: flex-end; gap: 12px; }
.raw-time > div { display: flex; flex-direction: column; align-items: flex-end; }
.raw-time > div strong { font: 700 clamp(16px, 1.15cqw, 21px)/1 "Bahnschrift", "Segoe UI", sans-serif; }
.raw-time > div span { margin-top: 5px; color: var(--rm-muted); font-size: clamp(8px, .55cqw, 10px); }
.raw-data-stamp { display: inline-flex; align-items: center; gap: 7px; padding: 8px 11px; border: 1px solid color-mix(in srgb, var(--rm-accent) 24%, transparent); border-radius: 20px; color: color-mix(in srgb, var(--rm-accent) 34%, white); font-size: clamp(8px, .55cqw, 10px); background: color-mix(in srgb, var(--rm-accent) 8%, transparent); }
.raw-data-stamp i { width: 6px; height: 6px; border-radius: 50%; background: var(--rm-accent); box-shadow: 0 0 0 4px color-mix(in srgb, var(--rm-accent) 10%, transparent); }
.raw-refresh { display: grid; width: 40px; height: 40px; flex: 0 0 40px; place-items: center; cursor: pointer; border: 1px solid rgba(126,177,175,.18); border-radius: 10px; color: #8aa3a2; background: rgba(255,255,255,.025); }
.raw-refresh:hover { color: var(--rm-accent); border-color: color-mix(in srgb, var(--rm-accent) 40%, transparent); }
.raw-refresh:disabled { cursor: wait; opacity: .72; }

.raw-kpi-strip { display: grid; height: clamp(66px, 5.4cqw, 92px); flex: 0 0 auto; grid-template-columns: repeat(5, 1fr); gap: clamp(6px, .6cqw, 11px); padding: clamp(7px, .55cqw, 9px) 0; }
.raw-kpi-strip article { display: flex; min-width: 0; align-items: center; gap: clamp(7px, .6cqw, 11px); padding: clamp(7px, .6cqw, 11px); border: 1px solid var(--rm-kpi-border); border-radius: 12px; background: var(--rm-kpi-surface); box-shadow: var(--rm-kpi-shadow); backdrop-filter: blur(16px) saturate(125%); -webkit-backdrop-filter: blur(16px) saturate(125%); }
.raw-kpi-card { position: relative; overflow: hidden; }
.raw-kpi-card::after { position: absolute; right: 0; bottom: 0; left: 0; height: 2px; content: ""; opacity: .65; background: var(--rm-accent); }
.raw-kpi-card.is-warning { border-color: rgba(244,184,74,.42); background: linear-gradient(145deg, rgba(76,61,38,.5), rgba(14,29,42,.54)); }
.raw-kpi-card.is-warning::after { background: var(--rm-warning); }
.raw-kpi-card.is-danger { border-color: rgba(251,113,133,.48); background: linear-gradient(145deg, rgba(74,34,51,.54), rgba(17,27,43,.56)); }
.raw-kpi-card.is-danger::after { height: 3px; background: var(--rm-danger); }
.raw-kpi-icon { display: grid; width: 34px; height: 34px; flex: 0 0 34px; place-items: center; border-radius: 10px; }
.raw-kpi-icon.mint { color: var(--rm-accent); background: color-mix(in srgb, var(--rm-accent) 10%, transparent); }
.raw-kpi-icon.blue { color: var(--rm-cyan); background: rgba(56,189,248,.1); }
.raw-kpi-icon.amber { color: var(--rm-warning); background: rgba(244,184,74,.1); }
.raw-kpi-icon.rose { color: var(--rm-danger); background: rgba(251,113,133,.1); }
.raw-kpi-strip article > div { min-width: 0; }
.raw-kpi-strip small { display: block; color: var(--rm-muted); font-size: clamp(9px, .6cqw, 11px); }
.raw-kpi-strip strong { display: block; margin-top: 3px; overflow: hidden; font: 720 clamp(19px, 1.45cqw, 26px)/1.02 "Bahnschrift", "Segoe UI", sans-serif; text-overflow: ellipsis; white-space: nowrap; }
.raw-kpi-strip em { margin-left: 4px; color: var(--rm-muted); font-size: clamp(8px, .55cqw, 10px); font-style: normal; }
.raw-kpi-strip p { margin: 4px 0 0; color: var(--rm-dim); font-size: clamp(8px, .54cqw, 10px); }

.raw-board-grid { display: grid; min-height: 0; flex: 1; grid-template-columns: repeat(12, minmax(0, 1fr)); grid-template-rows: minmax(0, .98fr) minmax(0, 1.02fr); gap: clamp(7px, .65cqw, 12px); }
.raw-flow-panel { grid-column: span 5; }
.raw-posture-panel { grid-column: span 7; }
.raw-month-panel { grid-column: span 3; }
.raw-zone-panel { grid-column: span 5; }
.raw-risk-panel { grid-column: span 4; }
.raw-panel { position: relative; min-width: 0; overflow: hidden; border: 1px solid var(--rm-panel-border); border-radius: 13px; background: var(--rm-panel-surface); box-shadow: var(--rm-panel-shadow); backdrop-filter: blur(var(--rm-panel-blur)) saturate(var(--rm-panel-saturation)); -webkit-backdrop-filter: blur(var(--rm-panel-blur)) saturate(var(--rm-panel-saturation)); }
.raw-panel::before { position: absolute; inset: 0; content: ""; pointer-events: none; opacity: var(--rm-panel-sheen-opacity); background: var(--rm-panel-sheen); }
.raw-panel-accent { position: absolute; top: -1px; left: 27px; width: 46px; height: 2px; background: var(--rm-accent); }
.raw-panel-accent.centered-accent { left: 44%; width: 88px; }
.raw-panel-title { position: relative; z-index: 2; padding: 11px 14px 5px; }
.raw-panel-title.centered { text-align: center; }
.raw-panel-title.split { display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid var(--rm-line); }
.raw-panel-title h2 { display: flex; align-items: center; justify-content: center; gap: 9px; margin: 0; font-size: clamp(13px, .95cqw, 17px); font-weight: 650; }
.raw-panel-title h2 span { width: 14px; height: 1px; background: var(--rm-cyan); }
.raw-panel-title p { margin: 5px 0 0; color: var(--rm-muted); font-size: clamp(8px, .56cqw, 10px); }
.raw-panel-title.split p { margin: 0; }

.raw-flow-panel { display: flex; flex-direction: column; }
.raw-today-pair { position: relative; z-index: 2; display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px; margin: 1px 12px 0; }
.raw-today-pair > div { padding: 8px 10px; border: 1px solid var(--rm-line); border-radius: 10px; background: var(--rm-inner-surface); box-shadow: inset 0 1px rgba(255,255,255,.045); }
.raw-today-pair > div:first-child { border-left: 2px solid var(--rm-accent); }
.raw-today-pair > div:last-child { border-left: 2px dashed var(--rm-flow-out); }
.raw-today-pair > div:first-child strong { color: #bdfbf5; }
.raw-today-pair > div:last-child strong { color: #ffd98b; }
.raw-today-pair span { display: block; color: var(--rm-muted); font-size: clamp(9px, .58cqw, 10px); }
.raw-today-pair strong { display: inline-block; margin-top: 3px; font: 720 clamp(21px, 1.5cqw, 27px)/1 "Bahnschrift", "Segoe UI", sans-serif; }
.raw-today-pair small { margin-left: 4px; color: var(--rm-muted); font-size: clamp(8px, .55cqw, 10px); }
.raw-trend-chart { position: relative; z-index: 2; width: calc(100% - 8px) !important; max-width: calc(100% - 8px); height: clamp(150px, 13cqw, 235px) !important; overflow: hidden; margin: 0 4px; }
.raw-flow-foot { position: relative; z-index: 2; display: flex; align-items: center; gap: 8px; margin-top: auto; padding: 5px 13px 9px; border-top: 1px solid var(--rm-line); color: var(--rm-muted); font-size: clamp(8px, .55cqw, 10px); }
.raw-flow-foot strong { color: var(--rm-text); font: 700 clamp(10px, .65cqw, 12px) "Bahnschrift", "Segoe UI", sans-serif; }
.raw-flow-foot em { margin-left: auto; color: var(--rm-accent); font-size: clamp(8px, .55cqw, 10px); font-style: normal; }

.raw-posture-panel { display: flex; flex-direction: column; padding-bottom: 6px; }
.raw-silo-overview { position: relative; z-index: 2; display: grid; min-height: 0; flex: 1; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 2px; margin: 4px 9px 5px; padding: 5px 3px; border: 1px solid rgba(126,166,191,.14); border-radius: 11px; background: var(--rm-deep-surface); }
.raw-silo-card { --material-top: #d9f1e9; --material-mid: #8bcabd; --material-bottom: #477f7d; --material-highlight: #f3fffb; --material-glow: rgba(123,211,195,.2); position: relative; display: flex; min-width: 0; flex-direction: column; padding: 4px 7px 5px; overflow: visible; perspective: 360px; }
.raw-silo-card.is-powder { --material-top: #f6ecd6; --material-mid: #d8c093; --material-bottom: #a88656; --material-highlight: #fffaf0; --material-glow: rgba(226,199,143,.2); }
.raw-silo-card.is-granule { --material-top: #d9f1e9; --material-mid: #8bcabd; --material-bottom: #477f7d; --material-highlight: #f3fffb; --material-glow: rgba(123,211,195,.2); }
.raw-silo-card.is-roll { --material-top: #c4e7f2; --material-mid: #76b8d0; --material-bottom: #386f91; --material-highlight: #effbff; --material-glow: rgba(105,184,213,.21); }
.raw-silo-card.is-liquid { --material-top: #dbddf5; --material-mid: #929ed3; --material-bottom: #58659d; --material-highlight: #f3f3ff; --material-glow: rgba(145,156,218,.22); }
.raw-silo-card:not(:last-child)::after { position: absolute; top: 9px; right: -1px; bottom: 7px; width: 1px; content: ""; background: linear-gradient(transparent, rgba(126,166,191,.17) 18%, rgba(126,166,191,.17) 82%, transparent); }
.raw-silo-card-head { display: flex; min-height: 20px; align-items: flex-start; justify-content: space-between; gap: 5px; }
.raw-silo-card-head > span { display: flex; min-width: 0; align-items: baseline; gap: 5px; }
.raw-silo-card-head b { color: #cfe1ec; font: 720 clamp(8px, .52cqw, 10px) "Bahnschrift", "Segoe UI", sans-serif; letter-spacing: .035em; }
.raw-silo-card-head small { color: var(--rm-accent); font-size: clamp(7px, .44cqw, 8px); white-space: nowrap; }
.raw-silo-card.is-high .raw-silo-card-head small, .raw-silo-card.is-high .raw-silo-card-head > strong { color: var(--rm-warning); }
.raw-silo-card.is-low .raw-silo-card-head small, .raw-silo-card.is-low .raw-silo-card-head > strong { color: var(--rm-cyan); }
.raw-silo-card-head > strong { color: var(--rm-accent); font: 740 clamp(13px, .82cqw, 16px)/1 "Bahnschrift", "Segoe UI", sans-serif; }
.raw-silo-card-body { display: grid; min-height: 0; flex: 1; grid-template-rows: minmax(78px, 1fr) auto; justify-items: center; align-items: center; gap: 2px; padding: 1px 2px 0; }
.raw-silo-vessel { position: relative; width: 50px; height: 88px; align-self: center; justify-self: center; overflow: visible; filter: drop-shadow(0 9px 9px rgba(0,0,0,.25)) drop-shadow(0 0 6px var(--material-glow)); transform-style: preserve-3d; animation: raw-silo-vessel-settle .95s cubic-bezier(.16,1,.3,1) calc(var(--tank-delay) + .08s) both; }
.raw-silo-card.is-high .raw-silo-vessel { filter: drop-shadow(0 8px 8px rgba(0,0,0,.22)) drop-shadow(0 0 5px rgba(242,184,75,.25)); }
.raw-silo-card.is-low .raw-silo-vessel { filter: drop-shadow(0 8px 8px rgba(0,0,0,.22)) drop-shadow(0 0 5px rgba(125,183,255,.22)); }
.raw-silo-vessel::before, .raw-silo-vessel::after { position: absolute; z-index: 1; bottom: 1px; width: 4px; height: 13px; border-radius: 0 0 2px 2px; content: ""; background: linear-gradient(90deg, #0b2839, rgba(172,211,220,.48), #0b2839); }
.raw-silo-vessel::before { left: 10px; transform: rotate(4deg); }
.raw-silo-vessel::after { right: 10px; transform: rotate(-4deg); }
.raw-silo-shell { position: absolute; z-index: 2; top: 9px; left: 50%; width: 46px; height: 66px; overflow: hidden; border: 1px solid rgba(190,225,233,.62); border-radius: 18px 18px 8px 8px / 9px 9px 7px 7px; background: repeating-linear-gradient(0deg, transparent 0 20px, rgba(218,239,244,.075) 20px 21px), linear-gradient(90deg, rgba(5,29,42,.72) 0%, rgba(76,127,145,.42) 12%, rgba(223,243,246,.2) 31%, rgba(255,255,255,.32) 44%, rgba(113,161,177,.15) 61%, rgba(14,50,66,.53) 83%, rgba(3,24,36,.75) 100%); box-shadow: inset 8px 0 12px rgba(0,13,24,.25), inset -8px 0 12px rgba(0,10,19,.34), inset 0 1px rgba(255,255,255,.16), 0 0 0 1px rgba(3,20,31,.42), 3px 5px 8px rgba(0,0,0,.2); transform: translateX(-50%); transform-style: preserve-3d; }
.raw-silo-shell::before { position: absolute; z-index: 9; top: -4px; right: -1px; left: -1px; height: 13px; border: 1px solid rgba(204,233,238,.56); border-radius: 50%; content: ""; background: radial-gradient(ellipse at 50% 28%, rgba(222,243,246,.24), transparent 28%), linear-gradient(180deg, rgba(66,112,130,.98), rgba(9,36,51,.98)); box-shadow: inset 0 2px rgba(255,255,255,.16), inset 0 -3px 5px rgba(0,9,18,.45), 0 3px 6px rgba(0,0,0,.3); }
.raw-silo-shell::after { position: absolute; z-index: 6; top: 25px; right: 2px; left: 2px; height: 1px; content: ""; opacity: .44; background: linear-gradient(90deg, transparent, rgba(226,245,248,.52) 38%, rgba(226,245,248,.28) 62%, transparent); box-shadow: 0 20px rgba(220,241,245,.28); }
.raw-silo-neck { position: absolute; z-index: 10; top: 3px; left: 50%; width: 17px; height: 8px; border: 1px solid rgba(186,220,228,.52); border-bottom: 0; border-radius: 50% 50% 2px 2px / 35% 35% 2px 2px; background: linear-gradient(90deg, #071e2d, #315b6f 38%, #527b8c 52%, #173d50 70%, #061b29); box-shadow: inset 0 2px rgba(255,255,255,.11), 2px 2px 4px rgba(0,0,0,.25); transform: translateX(-50%); }
.raw-silo-cap { position: absolute; z-index: 11; top: 0; left: 50%; width: 23px; height: 6px; border: 1px solid rgba(194,227,234,.34); border-radius: 50%; background: radial-gradient(ellipse at 50% 28%, rgba(239,252,253,.72), rgba(102,153,170,.74) 38%, rgba(17,55,72,.92) 76%); box-shadow: 0 3px 5px rgba(0,0,0,.27), inset 0 1px rgba(255,255,255,.28); transform: translateX(-50%); }
.raw-silo-outlet { position: absolute; z-index: 4; bottom: 5px; left: 50%; width: 16px; height: 18px; background: linear-gradient(90deg, #061d2b, #294f62 28%, #668391 49%, #21475a 70%, #061a28); clip-path: polygon(0 0, 100% 0, 69% 64%, 69% 100%, 31% 100%, 31% 64%); filter: drop-shadow(2px 3px 2px rgba(0,0,0,.22)); transform: translateX(-50%) translateZ(-2px); }
.raw-silo-outlet::after { position: absolute; right: 4px; bottom: 0; left: 4px; height: 4px; border-radius: 0 0 2px 2px; content: ""; background: rgba(168,207,217,.55); }
.raw-silo-floor-shadow { position: absolute; z-index: 0; bottom: -2px; left: 50%; width: 56px; height: 12px; border-radius: 50%; opacity: .72; background: radial-gradient(ellipse, rgba(0,0,0,.55) 0 18%, rgba(0,0,0,.26) 45%, transparent 72%); filter: blur(1.5px); transform: translateX(-50%) rotateX(68deg); transform-origin: center; }
.raw-silo-threshold { position: absolute; z-index: 7; right: -4px; width: 9px; border-top: 1px dashed currentColor; opacity: .76; }
.raw-silo-threshold.is-high-mark { top: 22%; color: var(--rm-warning); }
.raw-silo-threshold.is-low-mark { bottom: 31%; color: var(--rm-cyan); }
.raw-silo-fill { position: absolute; z-index: 2; right: 1px; bottom: 1px; left: 1px; height: var(--fill); max-height: calc(100% - 2px); overflow: hidden; border-radius: 0 0 7px 7px; background: linear-gradient(180deg, var(--material-top), var(--material-mid) 42%, var(--material-bottom)); box-shadow: inset 7px 0 10px rgba(255,255,255,.08), inset -7px 0 10px rgba(3,20,29,.17); transform-origin: center bottom; animation: raw-silo-fill-rise 1.6s cubic-bezier(.4,0,.2,1) var(--tank-delay) both; }
.raw-silo-fill::before { position: absolute; z-index: 3; top: -4px; right: -4px; left: -4px; height: 10px; border-radius: 50%; content: ""; background: var(--material-top); box-shadow: 0 2px 7px var(--material-glow); }
.raw-silo-fill i { position: absolute; inset: -8px -10px; opacity: .45; }
.raw-silo-card.is-powder .raw-silo-fill::before { top: -7px; height: 15px; border-radius: 45% 55% 28% 32%; background: radial-gradient(ellipse at 50% 105%, var(--material-top) 0 61%, transparent 63%); box-shadow: none; }
.raw-silo-card.is-powder .raw-silo-fill i { opacity: .34; background: radial-gradient(circle, var(--material-highlight) 0 1px, transparent 1.4px), radial-gradient(circle, rgba(109,84,48,.35) 0 .7px, transparent 1.1px); background-position: 0 0, 3px 4px; background-size: 7px 7px, 8px 8px; }
.raw-silo-card.is-granule .raw-silo-fill::before { top: -5px; height: 11px; background: radial-gradient(circle at 20% 60%, var(--material-highlight) 0 2px, transparent 2.4px), radial-gradient(circle at 48% 38%, var(--material-top) 0 2.2px, transparent 2.6px), radial-gradient(circle at 76% 61%, var(--material-highlight) 0 2px, transparent 2.4px), var(--material-mid); background-size: 12px 8px; }
.raw-silo-card.is-granule .raw-silo-fill i { opacity: .62; background: radial-gradient(circle at 35% 35%, var(--material-highlight) 0 1.4px, var(--material-mid) 1.7px 2.6px, transparent 2.9px); background-size: 8px 8px; }
.raw-silo-card.is-roll .raw-silo-fill { background: repeating-linear-gradient(0deg, transparent 0 11px, rgba(239,251,255,.26) 11px 12px), linear-gradient(90deg, var(--material-bottom), var(--material-top) 49%, var(--material-bottom)); }
.raw-silo-card.is-roll .raw-silo-fill::before { top: -3px; height: 8px; background: linear-gradient(90deg, var(--material-bottom), var(--material-highlight) 50%, var(--material-bottom)); }
.raw-silo-card.is-roll .raw-silo-fill i { inset: 0 5px; opacity: .68; background: radial-gradient(ellipse at center, transparent 0 4px, rgba(238,250,255,.75) 4.7px 5.6px, rgba(49,96,127,.34) 6px 7.4px, transparent 7.8px); background-position: center 2px; background-size: 25px 17px; }
.raw-silo-card.is-liquid .raw-silo-fill::before { background: color-mix(in srgb, var(--material-top) 86%, white); animation: raw-silo-surface 3.6s ease-in-out infinite alternate; }
.raw-silo-card.is-liquid .raw-silo-fill i { inset: -8px -34px; opacity: .18; background: radial-gradient(circle at 30% 35%, rgba(255,255,255,.8) 0 2px, transparent 3px), radial-gradient(circle at 68% 62%, rgba(255,255,255,.55) 0 1px, transparent 2px); background-size: 25px 31px, 32px 28px; animation: raw-silo-current 5.2s ease-in-out infinite alternate; }
.raw-silo-shine { position: absolute; z-index: 7; top: 10px; bottom: 9px; left: 8px; width: 3px; border-radius: 3px; opacity: .3; background: linear-gradient(rgba(255,255,255,.56), rgba(255,255,255,.06)); transform-origin: center top; animation: raw-silo-shine-in 1.45s ease-out calc(var(--tank-delay) + .35s) both; }
.raw-silo-depth { position: absolute; z-index: 8; inset: 0; border-radius: inherit; pointer-events: none; background: linear-gradient(90deg, rgba(0,8,16,.46), transparent 17%, rgba(255,255,255,.08) 37%, rgba(255,255,255,.14) 48%, transparent 64%, rgba(0,7,14,.5)); box-shadow: inset 3px 0 5px rgba(0,0,0,.15), inset -4px 0 7px rgba(0,0,0,.22); }
.raw-silo-depth::after { position: absolute; right: 2px; bottom: -3px; left: 2px; height: 9px; border: 1px solid rgba(184,221,229,.25); border-radius: 50%; content: ""; background: linear-gradient(180deg, rgba(100,148,162,.13), rgba(3,21,32,.5)); box-shadow: inset 0 2px 3px rgba(255,255,255,.07); }
.raw-silo-copy { width: 100%; min-width: 0; text-align: center; }
.raw-silo-material-line { display: flex; min-width: 0; align-items: center; justify-content: center; gap: 4px; }
.raw-silo-material-line > span { max-width: calc(100% - 34px); overflow: hidden; color: #e1edf2; font-size: clamp(9px, .58cqw, 11px); font-weight: 680; text-overflow: ellipsis; white-space: nowrap; }
.raw-silo-material-line > small { flex: 0 0 auto; padding: 1px 4px; border: 1px solid color-mix(in srgb, var(--material-top) 35%, transparent); border-radius: 5px; color: var(--material-top); font-size: clamp(6px, .38cqw, 7px); line-height: 1.25; background: color-mix(in srgb, var(--material-mid) 13%, transparent); }
.raw-silo-copy > strong { display: block; margin-top: 2px; color: #f1f6f8; font: 740 clamp(13px, .84cqw, 17px)/1 "Bahnschrift", "Segoe UI", sans-serif; white-space: nowrap; }
.raw-silo-copy > strong em { margin-left: 2px; color: var(--rm-muted); font-size: .52em; font-style: normal; }
.raw-silo-copy p { margin: 2px 0 0; overflow: hidden; color: var(--rm-muted); font-size: clamp(7px, .43cqw, 8px); text-overflow: ellipsis; white-space: nowrap; }
.raw-posture-metrics { position: relative; z-index: 2; display: grid; flex: 0 0 auto; grid-template-columns: repeat(4, 1fr); margin: 0 12px; overflow: hidden; border: 1px solid var(--rm-line); border-radius: 10px; }
.raw-posture-metrics > div { min-width: 0; min-height: 38px; padding: 5px 4px; border-right: 1px solid var(--rm-line); text-align: center; }
.raw-posture-metrics > div:last-child { border-right: 0; }
.raw-posture-metrics span { display: block; color: var(--rm-muted); font-size: clamp(8px, .54cqw, 10px); }
.raw-posture-metrics strong { display: inline-block; max-width: calc(100% - 18px); margin-top: 3px; overflow: hidden; font: 720 clamp(13px, .86cqw, 17px)/1 "Bahnschrift", "Segoe UI", sans-serif; text-overflow: ellipsis; vertical-align: bottom; white-space: nowrap; }
.raw-posture-metrics small { margin-left: 3px; color: var(--rm-muted); font-size: clamp(8px, .52cqw, 9px); }

@keyframes raw-silo-card-enter { from { opacity: .25; transform: translateY(7px); } }
@keyframes raw-silo-vessel-settle { from { opacity: .34; transform: translateY(-4px) rotateY(-12deg) scale(.94); } to { opacity: 1; transform: translateY(0) rotateY(0) scale(1); } }
@keyframes raw-silo-fill-rise { from { opacity: .3; transform: scaleY(.06); } to { opacity: 1; transform: scaleY(1); } }
@keyframes raw-silo-surface { from { transform: translateX(-2px) scaleX(.96); } to { transform: translateX(2px) scaleX(1.04); } }
@keyframes raw-silo-current { from { transform: translate3d(-9px, 4px, 0); } to { transform: translate3d(9px, -4px, 0); } }
@keyframes raw-silo-shine-in { from { opacity: 0; transform: scaleY(.15); } }

.raw-month-panel { display: flex; flex-direction: column; }
.raw-month-overview { position: relative; z-index: 2; display: grid; min-height: 0; flex: 1 1 auto; grid-template-rows: repeat(2, minmax(46px, 1fr)); gap: 5px; margin: 1px 9px 5px; }
.raw-month-overview > div { position: relative; display: grid; min-width: 0; min-height: 0; grid-template-columns: 28px minmax(0, 1fr) auto; align-items: center; gap: 6px; padding: 5px 7px; overflow: hidden; border: 1px solid rgba(126,166,191,.16); border-radius: 9px; background: var(--rm-inner-surface); }
.raw-month-overview > div::before { position: absolute; top: 8px; bottom: 8px; left: 0; width: 2px; content: ""; background: var(--rm-accent); box-shadow: 4px 0 12px rgba(52,218,198,.13); }
.raw-month-overview > div.is-tasks::before { background: var(--rm-cyan); box-shadow: 4px 0 12px rgba(125,183,255,.14); }
.raw-month-icon { display: grid; width: 27px; height: 27px; place-items: center; border: 1px solid color-mix(in srgb, var(--rm-accent) 20%, transparent); border-radius: 8px; color: var(--rm-accent); background: color-mix(in srgb, var(--rm-accent) 8%, transparent); }
.raw-month-icon svg { width: 14px; height: 14px; stroke-width: 1.7; }
.raw-month-overview .is-tasks .raw-month-icon { border-color: rgba(125,183,255,.22); color: var(--rm-cyan); background: rgba(125,183,255,.08); }
.raw-month-primary { min-width: 0; }
.raw-month-primary > span { display: block; overflow: hidden; color: var(--rm-muted); font-size: clamp(7px, .46cqw, 9px); text-overflow: ellipsis; white-space: nowrap; }
.raw-month-primary strong { display: inline-block; margin-top: 2px; color: #e7f1f5; font: 740 clamp(15px, .96cqw, 19px)/1 "Bahnschrift", "Segoe UI", sans-serif; letter-spacing: -.02em; white-space: nowrap; }
.raw-month-primary strong small { margin-left: 2px; color: var(--rm-dim); font-size: .48em; }
.raw-month-primary em { display: inline-block; margin-left: 5px; padding: 1px 4px; border-radius: 4px; color: var(--rm-accent); font-size: clamp(6px, .38cqw, 7px); font-style: normal; white-space: nowrap; background: color-mix(in srgb, var(--rm-accent) 8%, transparent); vertical-align: 2px; }
.raw-month-overview .is-tasks .raw-month-primary em { color: var(--rm-cyan); background: rgba(125,183,255,.08); }
.raw-month-breakdown { display: grid; min-width: 83px; grid-template-columns: repeat(2, minmax(0, 1fr)); }
.raw-month-breakdown > span { min-width: 0; padding: 0 4px; border-left: 1px solid rgba(126,166,191,.13); text-align: center; }
.raw-month-breakdown i, .raw-month-breakdown b { display: block; font-style: normal; white-space: nowrap; }
.raw-month-breakdown i { color: var(--rm-dim); font-size: clamp(6px, .4cqw, 7px); }
.raw-month-breakdown b { margin-top: 3px; color: #cbdde6; font: 680 clamp(8px, .52cqw, 10px) "Bahnschrift", "Segoe UI", sans-serif; }
.raw-month-quality { position: relative; z-index: 2; display: grid; flex: 0 0 auto; grid-template-columns: repeat(3, minmax(0, 1fr)); margin: 0 9px 5px; overflow: hidden; border: 1px solid rgba(126,166,191,.16); border-radius: 9px; background: var(--rm-soft-surface); }
.raw-month-quality > div { display: grid; min-width: 0; grid-template-columns: 13px minmax(0, 1fr); align-items: center; padding: 5px 4px 4px; border-right: 1px solid rgba(126,166,191,.13); text-align: left; }
.raw-month-quality > div:last-child { border-right: 0; }
.raw-month-quality svg { width: 11px; height: 11px; color: var(--rm-accent); stroke-width: 1.8; }
.raw-month-quality span { overflow: hidden; color: var(--rm-muted); font-size: clamp(6px, .42cqw, 8px); text-overflow: ellipsis; white-space: nowrap; }
.raw-month-quality strong { grid-column: 1 / -1; margin-top: 3px; color: var(--rm-accent); font: 720 clamp(11px, .72cqw, 14px)/1 "Bahnschrift", "Segoe UI", sans-serif; text-align: center; white-space: nowrap; }
.raw-month-quality small { margin-left: 2px; color: var(--rm-dim); font-size: .56em; }
.raw-month-foot { position: relative; z-index: 2; display: grid; flex: 0 0 auto; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 3px; padding: 5px 9px 7px; border-top: 1px solid rgba(126,166,191,.11); color: var(--rm-muted); text-align: center; }
.raw-month-foot > span { display: grid; min-width: 0; grid-template-columns: 12px minmax(0, 1fr); align-items: center; column-gap: 2px; }
.raw-month-foot svg { width: 10px; height: 10px; color: var(--rm-dim); stroke-width: 1.8; }
.raw-month-foot em { overflow: hidden; font-size: clamp(6px, .41cqw, 8px); font-style: normal; text-overflow: ellipsis; white-space: nowrap; }
.raw-month-foot strong { grid-column: 1 / -1; margin-top: 2px; color: var(--rm-text); font: 700 clamp(10px, .64cqw, 12px) "Bahnschrift", "Segoe UI", sans-serif; }

.raw-zone-panel { display: flex; flex-direction: column; }
.raw-zone-overview { position: relative; z-index: 2; display: flex; flex: 0 0 auto; align-items: flex-end; justify-content: space-between; padding: 1px 12px 7px; border-bottom: 1px solid var(--rm-line); }
.raw-zone-overview > div span { display: block; color: var(--rm-muted); font-size: clamp(8px, .54cqw, 10px); }
.raw-zone-overview > div strong { display: inline-block; margin-top: 2px; color: var(--rm-warning); font: 740 clamp(23px, 1.5cqw, 27px)/1 "Bahnschrift", "Segoe UI", sans-serif; }
.raw-zone-overview > div small { margin-left: 6px; color: var(--rm-dim); font-size: clamp(8px, .5cqw, 9px); }
.raw-zone-overview p { display: flex; align-items: center; gap: 5px; margin: 0; color: var(--rm-muted); font-size: clamp(8px, .5cqw, 9px); }
.raw-zone-overview p i { width: 6px; height: 6px; border-radius: 50%; }
.raw-zone-overview p i.danger { background: var(--rm-danger); }
.raw-zone-overview p i.warning { margin-left: 5px; background: var(--rm-warning); }
.raw-zone-grid { position: relative; z-index: 2; display: grid; min-height: 0; flex: 1; grid-template-columns: repeat(4, minmax(0, 1fr)); grid-template-rows: repeat(2, minmax(0, 1fr)); gap: 5px; padding: 7px 9px 9px; }
.raw-zone-card { display: flex; min-width: 0; min-height: 0; flex-direction: column; justify-content: space-between; padding: 6px 7px; border: 1px solid rgba(101,161,201,.16); border-radius: 8px; text-align: left; background: var(--rm-inner-surface); box-shadow: inset 2px 0 rgba(52,218,198,.3); }
.raw-zone-card.danger { border-color: rgba(251,113,133,.24); box-shadow: inset 2px 0 rgba(251,113,133,.72); }
.raw-zone-card.warning { border-color: rgba(244,184,74,.22); box-shadow: inset 2px 0 rgba(244,184,74,.68); }
.raw-zone-card header { display: flex; min-width: 0; align-items: flex-start; justify-content: space-between; gap: 5px; }
.raw-zone-card header > span { display: flex; min-width: 0; flex-direction: column; }
.raw-zone-card header strong { color: #d5e4eb; font: 700 clamp(9px, .57cqw, 11px) "Bahnschrift", "Segoe UI", sans-serif; }
.raw-zone-card header small { margin-top: 1px; overflow: hidden; color: var(--rm-dim); font-size: clamp(7px, .45cqw, 9px); text-overflow: ellipsis; white-space: nowrap; }
.raw-zone-card header > em { flex: 0 0 auto; margin: 0; font: 740 clamp(12px, .78cqw, 15px) "Bahnschrift", "Segoe UI", sans-serif; font-style: normal; }
.raw-zone-card header > em.danger { color: var(--rm-danger); }
.raw-zone-card header > em.warning { color: var(--rm-warning); }
.raw-zone-card header > em.normal { color: var(--rm-accent); }
.raw-zone-card > div { height: 4px; margin-top: 5px; overflow: hidden; border-radius: 3px; background: rgba(86,120,151,.22); }
.raw-zone-card > div i { display: block; height: 100%; border-radius: inherit; }
.raw-zone-card > div i.danger { background: var(--rm-danger); }
.raw-zone-card > div i.warning { background: var(--rm-warning); }
.raw-zone-card > div i.normal { background: var(--rm-accent); }
.raw-zone-card p { display: flex; justify-content: space-between; gap: 3px; margin: 4px 0 0; color: var(--rm-dim); font-size: clamp(7px, .45cqw, 9px); }
.raw-zone-card p b { color: var(--rm-muted); font-weight: 600; white-space: nowrap; }
.raw-zone-card footer { display: flex; min-width: 0; flex-wrap: wrap; align-items: center; gap: 3px 6px; margin-top: 5px; padding-top: 4px; border-top: 1px solid rgba(126,166,191,.11); color: var(--rm-muted); font-size: clamp(7px, .43cqw, 8px); }
.raw-zone-card footer span { white-space: nowrap; }
.raw-zone-card footer strong, .raw-zone-card footer em, .raw-zone-card footer small { margin: 0 0 0 auto; font-size: inherit; font-style: normal; white-space: nowrap; }
.raw-zone-card footer strong { color: var(--rm-danger); }
.raw-zone-card footer em { color: var(--rm-warning); }
.raw-zone-card footer small { color: var(--rm-accent); }

.raw-target-strip { position: relative; z-index: 2; display: grid; grid-template-columns: repeat(3, 1fr); margin: 1px 11px 7px; overflow: hidden; border: 1px solid var(--rm-line); border-radius: 9px; background: var(--rm-soft-surface); box-shadow: inset 0 1px rgba(255,255,255,.045); }
.raw-target-strip > div { padding: 8px 4px; border-right: 1px solid var(--rm-line); text-align: center; }
.raw-target-strip > div:last-child { border-right: 0; }
.raw-target-strip span, .raw-target-strip small { display: block; color: var(--rm-muted); font-size: clamp(8px, .52cqw, 9px); }
.raw-target-strip strong { display: block; margin: 4px 0 2px; color: var(--rm-accent); font: 720 clamp(14px, .9cqw, 16px) "Bahnschrift", "Segoe UI", sans-serif; }
.raw-target-strip strong.warning { color: var(--rm-warning); }
.raw-target-strip strong.warning + small { color: #e6bc6a; }
.raw-risk-panel { display: flex; flex-direction: column; }
.raw-alert-viewport { position: relative; z-index: 2; min-height: 0; flex: 1; overflow-x: hidden; overflow-y: auto; outline: none; overscroll-behavior: contain; scroll-behavior: smooth; scrollbar-color: rgba(53,211,199,.72) rgba(75,111,132,.14); scrollbar-width: thin; }
.raw-alert-viewport::-webkit-scrollbar { width: 7px; }
.raw-alert-viewport::-webkit-scrollbar-track { border-radius: 8px; background: rgba(75,111,132,.13); }
.raw-alert-viewport::-webkit-scrollbar-thumb { border: 2px solid rgba(7,22,34,.82); border-radius: 8px; background: rgba(53,211,199,.74); }
.raw-alert-viewport::-webkit-scrollbar-thumb:hover { background: var(--rm-accent); }
.raw-alert-viewport:focus-visible { border-radius: 8px; box-shadow: inset 0 0 0 2px rgba(53,211,199,.7); }
.raw-alert-list { display: grid; gap: 5px; padding: 0 5px 5px 10px; }
.raw-alert-list-item { display: grid; min-height: 42px; grid-template-columns: 23px 1fr auto; align-items: center; gap: 7px; padding: 5px 8px; border: 1px solid rgba(146,193,216,.12); border-radius: 9px; background: linear-gradient(145deg, rgba(26,56,74,.62), rgba(8,27,41,.58)); box-shadow: inset 0 1px rgba(255,255,255,.045); }
.raw-alert-list-item.is-breached { border-left-color: var(--rm-danger); background: linear-gradient(90deg, rgba(251,113,133,.08), rgba(13,38,54,.58) 34%); }
.raw-alert-list-item > span { display: grid; width: 21px; height: 21px; place-items: center; border-radius: 50%; }
.raw-alert-list-item > span.danger { color: var(--rm-danger); background: rgba(251,113,133,.12); }
.raw-alert-list-item > span.warning { color: var(--rm-warning); background: rgba(244,184,74,.12); }
.raw-alert-list-item > div { min-width: 0; }
.raw-alert-list-item strong,
.raw-alert-list-item small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.raw-alert-list-item strong { font-size: clamp(9px, .58cqw, 11px); }
.raw-alert-list-item small { margin-top: 3px; color: var(--rm-muted); font-size: clamp(8px, .5cqw, 9px); }
.raw-alert-list-item small b { margin-right: 5px; color: var(--rm-warning); font-weight: 700; }
.raw-alert-list-item.is-breached small b { color: var(--rm-danger); }
.raw-alert-list-item em { color: var(--rm-dim); font: 650 clamp(8px, .5cqw, 9px) "Bahnschrift", "Segoe UI", sans-serif; font-style: normal; }
.raw-risk-foot { position: relative; z-index: 2; display: flex; flex: 0 0 auto; align-items: center; justify-content: space-between; min-height: 29px; margin-top: 4px; padding: 5px 11px 6px; border-top: 1px solid var(--rm-line); }
.raw-risk-foot > div { display: flex; align-items: baseline; gap: 7px; }
.raw-risk-foot > div span { color: var(--rm-dim); font-size: clamp(7px, .48cqw, 9px); }
.raw-risk-foot > div strong { color: var(--rm-text); font-size: clamp(8px, .54cqw, 10px); }
.raw-risk-foot p { margin: 0; color: var(--rm-muted); font-size: clamp(8px, .5cqw, 9px); }
.raw-risk-foot p span { margin-right: 8px; color: var(--rm-accent); font-variant-numeric: tabular-nums; }
.raw-risk-foot p strong { margin-left: 4px; color: var(--rm-danger); }

.raw-board, .raw-panel, .raw-kpi-strip article, .raw-today-pair > div, .raw-silo-overview, .raw-month-overview > div, .raw-zone-card { transition: border-color .3s ease, box-shadow .3s ease, background-color .3s ease; }

.raw-material-page[data-theme="glacier"] {
  --rm-page-surface:
    radial-gradient(circle at 14% 12%, rgba(89, 226, 255, .23), transparent 30%),
    radial-gradient(circle at 86% 15%, rgba(96, 153, 255, .26), transparent 32%),
    radial-gradient(circle at 54% 104%, rgba(76, 183, 222, .22), transparent 44%),
    linear-gradient(145deg, #040d17 0%, #0a253a 48%, #06131f 100%);
  --rm-page-haze:
    radial-gradient(ellipse at 17% 32%, rgba(103, 232, 249, .24) 0 8%, transparent 27%),
    radial-gradient(ellipse at 79% 20%, rgba(129, 173, 255, .26) 0 9%, transparent 29%),
    radial-gradient(ellipse at 66% 78%, rgba(116, 206, 238, .2) 0 11%, transparent 31%);
  --rm-noise-opacity: .07;
}
.raw-material-page[data-theme="glacier"] .raw-board {
  --rm-accent: #67e8f9;
  --rm-cyan: #9ab9ff;
  --rm-flow-out: #f8c76a;
  --rm-warning: #f8c76a;
  --rm-danger: #ff819d;
  --rm-bg: rgba(8, 31, 50, .58);
  --rm-panel: rgba(39, 83, 112, .4);
  --rm-panel-strong: rgba(46, 94, 126, .5);
  --rm-text: #f7fbff;
  --rm-muted: #b6cad8;
  --rm-dim: #82a1b4;
  --rm-line: rgba(190, 226, 243, .25);
  --rm-board-border: rgba(204, 237, 250, .32);
  --rm-board-surface:
    linear-gradient(rgba(224, 244, 252, .035) 1px, transparent 1px),
    linear-gradient(90deg, rgba(224, 244, 252, .035) 1px, transparent 1px),
    radial-gradient(circle at 48% -14%, rgba(103, 232, 249, .2), transparent 40%),
    radial-gradient(circle at 100% 7%, rgba(154, 185, 255, .18), transparent 35%),
    rgba(8, 31, 50, .58);
  --rm-board-shadow: 0 30px 86px rgba(0, 13, 28, .48), inset 0 1px rgba(255,255,255,.18);
  --rm-board-blur: 34px;
  --rm-board-saturation: 152%;
  --rm-panel-border: rgba(211, 239, 249, .27);
  --rm-panel-surface: linear-gradient(145deg, rgba(81, 130, 160, .29), rgba(10, 39, 63, .5));
  --rm-panel-shadow: inset 0 1px rgba(255,255,255,.16), 0 16px 38px rgba(0,15,32,.2);
  --rm-panel-sheen: linear-gradient(128deg, rgba(255,255,255,.11), transparent 30%), radial-gradient(circle at 52% 0, rgba(164,221,255,.13), transparent 42%);
  --rm-panel-sheen-opacity: .9;
  --rm-panel-blur: 26px;
  --rm-panel-saturation: 148%;
  --rm-kpi-border: rgba(206, 237, 249, .27);
  --rm-kpi-surface: linear-gradient(145deg, rgba(79, 128, 158, .31), rgba(11, 42, 67, .44));
  --rm-kpi-shadow: inset 0 1px rgba(255,255,255,.15), 0 13px 28px rgba(0,18,38,.18);
  --rm-inner-surface: linear-gradient(145deg, rgba(68, 117, 147, .27), rgba(8, 38, 60, .38));
  --rm-deep-surface: linear-gradient(145deg, rgba(39, 89, 122, .27), rgba(5, 31, 51, .42));
  --rm-soft-surface: rgba(24, 65, 91, .26);
}

.raw-material-page[data-theme="ember"] {
  --rm-page-surface:
    radial-gradient(circle at 13% 9%, rgba(218, 166, 75, .19), transparent 29%),
    radial-gradient(circle at 87% 16%, rgba(89, 160, 151, .16), transparent 31%),
    radial-gradient(circle at 54% 104%, rgba(135, 82, 42, .2), transparent 43%),
    linear-gradient(145deg, #090806 0%, #1c1610 50%, #0b0a08 100%);
  --rm-page-haze:
    radial-gradient(ellipse at 18% 34%, rgba(226, 174, 82, .18) 0 8%, transparent 27%),
    radial-gradient(ellipse at 79% 22%, rgba(74, 157, 151, .16) 0 9%, transparent 29%),
    radial-gradient(ellipse at 67% 78%, rgba(150, 86, 45, .18) 0 11%, transparent 31%);
  --rm-noise-opacity: .085;
}
.raw-material-page[data-theme="ember"] .raw-board {
  --rm-accent: #dfb562;
  --rm-cyan: #72c7c2;
  --rm-flow-out: #f09f59;
  --rm-warning: #eab45d;
  --rm-danger: #ff7588;
  --rm-bg: rgba(23, 19, 15, .76);
  --rm-panel: rgba(46, 38, 29, .66);
  --rm-panel-strong: rgba(57, 47, 34, .72);
  --rm-text: #faf5ea;
  --rm-muted: #c6b9a4;
  --rm-dim: #958875;
  --rm-line: rgba(206, 177, 127, .2);
  --rm-board-border: rgba(218, 188, 133, .24);
  --rm-board-surface:
    repeating-linear-gradient(0deg, rgba(231,198,137,.022) 0 1px, transparent 1px 5px),
    linear-gradient(90deg, rgba(226,190,125,.02) 1px, transparent 1px),
    radial-gradient(circle at 50% -16%, rgba(223,181,98,.15), transparent 38%),
    radial-gradient(circle at 100% 8%, rgba(93,178,167,.09), transparent 34%),
    rgba(23,19,15,.76);
  --rm-board-shadow: 0 30px 82px rgba(0,0,0,.5), inset 0 1px rgba(255,239,205,.09);
  --rm-board-blur: 16px;
  --rm-board-saturation: 112%;
  --rm-panel-border: rgba(213, 181, 124, .2);
  --rm-panel-surface: linear-gradient(145deg, rgba(68, 55, 38, .58), rgba(25, 23, 21, .66));
  --rm-panel-shadow: inset 0 1px rgba(255,235,196,.08), 0 14px 30px rgba(0,0,0,.18);
  --rm-panel-sheen: linear-gradient(130deg, rgba(255,225,172,.055), transparent 30%), radial-gradient(circle at 50% 0, rgba(223,181,98,.07), transparent 40%);
  --rm-panel-sheen-opacity: .72;
  --rm-panel-blur: 12px;
  --rm-panel-saturation: 112%;
  --rm-kpi-border: rgba(215, 182, 123, .2);
  --rm-kpi-surface: linear-gradient(145deg, rgba(70, 56, 38, .58), rgba(27, 25, 22, .56));
  --rm-kpi-shadow: inset 0 1px rgba(255,232,188,.08), 0 12px 24px rgba(0,0,0,.16);
  --rm-inner-surface: linear-gradient(145deg, rgba(63, 50, 35, .54), rgba(27, 25, 22, .45));
  --rm-deep-surface: linear-gradient(145deg, rgba(48, 39, 28, .52), rgba(20, 19, 18, .56));
  --rm-soft-surface: rgba(48, 39, 29, .28);
}

.raw-material-page[data-theme="aurora"] {
  --rm-page-surface:
    radial-gradient(circle at 13% 10%, rgba(159, 130, 255, .25), transparent 30%),
    radial-gradient(circle at 88% 15%, rgba(70, 211, 255, .23), transparent 32%),
    radial-gradient(circle at 54% 105%, rgba(96, 70, 205, .23), transparent 44%),
    linear-gradient(145deg, #060611 0%, #171238 48%, #07131f 100%);
  --rm-page-haze:
    radial-gradient(ellipse at 17% 34%, rgba(169, 146, 255, .24) 0 8%, transparent 28%),
    radial-gradient(ellipse at 79% 21%, rgba(74, 211, 255, .22) 0 9%, transparent 29%),
    radial-gradient(ellipse at 66% 79%, rgba(101, 78, 218, .23) 0 11%, transparent 31%);
  --rm-noise-opacity: .065;
}
.raw-material-page[data-theme="aurora"] .raw-board {
  --rm-accent: #a992ff;
  --rm-cyan: #63d9ff;
  --rm-flow-out: #ffc46b;
  --rm-warning: #ffc46b;
  --rm-danger: #ff7ca7;
  --rm-bg: rgba(15, 12, 39, .67);
  --rm-panel: rgba(37, 29, 77, .58);
  --rm-panel-strong: rgba(45, 35, 91, .67);
  --rm-text: #f7f4ff;
  --rm-muted: #b9b2d1;
  --rm-dim: #837ba2;
  --rm-line: rgba(176, 157, 239, .21);
  --rm-board-border: rgba(190, 174, 246, .27);
  --rm-board-surface:
    linear-gradient(rgba(203, 190, 255, .026) 1px, transparent 1px),
    linear-gradient(90deg, rgba(118, 213, 255, .024) 1px, transparent 1px),
    radial-gradient(circle at 48% -16%, rgba(169, 146, 255, .2), transparent 39%),
    radial-gradient(circle at 100% 8%, rgba(99, 217, 255, .14), transparent 35%),
    rgba(15,12,39,.67);
  --rm-board-shadow: 0 30px 88px rgba(1,0,24,.52), inset 0 1px rgba(255,255,255,.12);
  --rm-board-blur: 30px;
  --rm-board-saturation: 162%;
  --rm-panel-border: rgba(192, 176, 245, .22);
  --rm-panel-surface: linear-gradient(145deg, rgba(63, 48, 111, .5), rgba(12, 25, 52, .59));
  --rm-panel-shadow: inset 0 1px rgba(255,255,255,.12), 0 16px 36px rgba(2,0,32,.2);
  --rm-panel-sheen: linear-gradient(126deg, rgba(255,255,255,.08), transparent 29%), radial-gradient(circle at 54% 0, rgba(103,217,255,.1), transparent 42%);
  --rm-panel-sheen-opacity: .86;
  --rm-panel-blur: 24px;
  --rm-panel-saturation: 158%;
  --rm-kpi-border: rgba(191, 176, 242, .22);
  --rm-kpi-surface: linear-gradient(145deg, rgba(65, 50, 112, .48), rgba(13, 28, 55, .5));
  --rm-kpi-shadow: inset 0 1px rgba(255,255,255,.11), 0 13px 28px rgba(3,0,34,.17);
  --rm-inner-surface: linear-gradient(145deg, rgba(58, 45, 103, .48), rgba(11, 28, 55, .4));
  --rm-deep-surface: linear-gradient(145deg, rgba(42, 35, 88, .43), rgba(8, 24, 49, .5));
  --rm-soft-surface: rgba(42, 33, 83, .27);
}

.raw-material-page[data-theme="titan"] {
  --rm-page-surface:
    radial-gradient(circle at 14% 10%, rgba(176, 215, 230, .18), transparent 30%),
    radial-gradient(circle at 87% 15%, rgba(94, 150, 183, .2), transparent 32%),
    radial-gradient(circle at 54% 105%, rgba(102, 124, 139, .2), transparent 44%),
    linear-gradient(145deg, #070a0e 0%, #18242d 49%, #0a1016 100%);
  --rm-page-haze:
    radial-gradient(ellipse at 18% 33%, rgba(190, 222, 233, .16) 0 8%, transparent 28%),
    radial-gradient(ellipse at 79% 21%, rgba(110, 169, 201, .18) 0 9%, transparent 30%),
    radial-gradient(ellipse at 66% 79%, rgba(125, 146, 160, .16) 0 11%, transparent 31%);
  --rm-noise-opacity: .075;
}
.raw-material-page[data-theme="titan"] .raw-board {
  --rm-accent: #b7d8e4;
  --rm-cyan: #7fc2ee;
  --rm-flow-out: #f2c66d;
  --rm-warning: #f2c66d;
  --rm-danger: #ff8197;
  --rm-bg: rgba(16, 24, 31, .72);
  --rm-panel: rgba(48, 62, 72, .56);
  --rm-panel-strong: rgba(58, 73, 84, .64);
  --rm-text: #f6f9fa;
  --rm-muted: #b5c2c9;
  --rm-dim: #7f919b;
  --rm-line: rgba(186, 209, 220, .21);
  --rm-board-border: rgba(205, 225, 233, .25);
  --rm-board-surface: linear-gradient(120deg, rgba(255,255,255,.026), transparent 24%), linear-gradient(rgba(213,231,238,.024) 1px, transparent 1px), linear-gradient(90deg, rgba(213,231,238,.022) 1px, transparent 1px), radial-gradient(circle at 50% -15%, rgba(183,216,228,.14), transparent 39%), rgba(16,24,31,.72);
  --rm-board-shadow: 0 30px 84px rgba(0,0,0,.48), inset 0 1px rgba(255,255,255,.12);
  --rm-board-blur: 22px;
  --rm-board-saturation: 116%;
  --rm-panel-border: rgba(203, 223, 231, .22);
  --rm-panel-surface: linear-gradient(145deg, rgba(69, 86, 97, .52), rgba(18, 29, 37, .63));
  --rm-panel-shadow: inset 0 1px rgba(255,255,255,.11), 0 15px 32px rgba(0,0,0,.18);
  --rm-panel-sheen: linear-gradient(125deg, rgba(255,255,255,.08), transparent 28%), radial-gradient(circle at 52% 0, rgba(190,222,233,.08), transparent 41%);
  --rm-panel-sheen-opacity: .8;
  --rm-panel-blur: 18px;
  --rm-panel-saturation: 116%;
  --rm-kpi-border: rgba(199, 220, 229, .21);
  --rm-kpi-surface: linear-gradient(145deg, rgba(67, 83, 94, .5), rgba(19, 30, 38, .54));
  --rm-kpi-shadow: inset 0 1px rgba(255,255,255,.1), 0 12px 24px rgba(0,0,0,.16);
  --rm-inner-surface: linear-gradient(145deg, rgba(58, 74, 85, .48), rgba(18, 31, 39, .42));
  --rm-deep-surface: linear-gradient(145deg, rgba(47, 63, 74, .44), rgba(13, 25, 33, .52));
  --rm-soft-surface: rgba(47, 61, 71, .27);
}

.raw-material-page[data-theme="jade"] {
  --rm-page-surface:
    radial-gradient(circle at 13% 9%, rgba(48, 224, 155, .2), transparent 30%),
    radial-gradient(circle at 88% 15%, rgba(51, 144, 119, .2), transparent 32%),
    radial-gradient(circle at 54% 105%, rgba(33, 105, 78, .22), transparent 44%),
    linear-gradient(145deg, #020705 0%, #071b14 49%, #030a08 100%);
  --rm-page-haze:
    radial-gradient(ellipse at 17% 33%, rgba(66, 230, 164, .21) 0 8%, transparent 28%),
    radial-gradient(ellipse at 79% 21%, rgba(64, 178, 147, .19) 0 9%, transparent 30%),
    radial-gradient(ellipse at 67% 79%, rgba(31, 120, 86, .2) 0 11%, transparent 31%);
  --rm-noise-opacity: .065;
}
.raw-material-page[data-theme="jade"] .raw-board {
  --rm-accent: #42e6a4;
  --rm-cyan: #72d7d0;
  --rm-flow-out: #f4bf63;
  --rm-warning: #f4bf63;
  --rm-danger: #ff788e;
  --rm-bg: rgba(4, 18, 13, .75);
  --rm-panel: rgba(10, 42, 30, .61);
  --rm-panel-strong: rgba(12, 51, 36, .68);
  --rm-text: #f1fbf7;
  --rm-muted: #a8c8ba;
  --rm-dim: #6f9685;
  --rm-line: rgba(93, 189, 151, .21);
  --rm-board-border: rgba(98, 206, 163, .26);
  --rm-board-surface: linear-gradient(rgba(121,224,183,.024) 1px, transparent 1px), linear-gradient(90deg, rgba(121,224,183,.022) 1px, transparent 1px), radial-gradient(circle at 49% -16%, rgba(66,230,164,.17), transparent 39%), radial-gradient(circle at 100% 8%, rgba(114,215,208,.1), transparent 35%), rgba(4,18,13,.75);
  --rm-board-shadow: 0 30px 86px rgba(0,0,0,.54), inset 0 1px rgba(210,255,236,.09);
  --rm-board-blur: 24px;
  --rm-board-saturation: 132%;
  --rm-panel-border: rgba(98, 200, 159, .22);
  --rm-panel-surface: linear-gradient(145deg, rgba(18, 64, 46, .57), rgba(5, 23, 18, .66));
  --rm-panel-shadow: inset 0 1px rgba(211,255,235,.09), 0 15px 34px rgba(0,0,0,.19);
  --rm-panel-sheen: linear-gradient(126deg, rgba(207,255,235,.065), transparent 29%), radial-gradient(circle at 52% 0, rgba(66,230,164,.08), transparent 41%);
  --rm-panel-sheen-opacity: .8;
  --rm-panel-blur: 20px;
  --rm-panel-saturation: 132%;
  --rm-kpi-border: rgba(97, 196, 157, .21);
  --rm-kpi-surface: linear-gradient(145deg, rgba(19, 66, 48, .54), rgba(6, 25, 19, .56));
  --rm-kpi-shadow: inset 0 1px rgba(213,255,237,.08), 0 12px 24px rgba(0,0,0,.17);
  --rm-inner-surface: linear-gradient(145deg, rgba(17, 58, 43, .52), rgba(6, 27, 21, .44));
  --rm-deep-surface: linear-gradient(145deg, rgba(12, 48, 35, .48), rgba(4, 21, 16, .56));
  --rm-soft-surface: rgba(14, 48, 36, .28);
}

.raw-material-page[data-theme="copper"] {
  --rm-page-surface:
    radial-gradient(circle at 13% 10%, rgba(220, 111, 70, .21), transparent 30%),
    radial-gradient(circle at 88% 16%, rgba(72, 154, 148, .16), transparent 32%),
    radial-gradient(circle at 54% 105%, rgba(139, 55, 38, .23), transparent 44%),
    linear-gradient(145deg, #090504 0%, #24110d 49%, #0b0807 100%);
  --rm-page-haze:
    radial-gradient(ellipse at 18% 33%, rgba(231, 133, 88, .2) 0 8%, transparent 28%),
    radial-gradient(ellipse at 79% 22%, rgba(72, 169, 159, .15) 0 9%, transparent 30%),
    radial-gradient(ellipse at 67% 79%, rgba(150, 61, 42, .21) 0 11%, transparent 31%);
  --rm-noise-opacity: .09;
}
.raw-material-page[data-theme="copper"] .raw-board {
  --rm-accent: #e58b5b;
  --rm-cyan: #70cbc3;
  --rm-flow-out: #ef925f;
  --rm-warning: #f0b65f;
  --rm-danger: #ff6f8a;
  --rm-bg: rgba(28, 15, 12, .78);
  --rm-panel: rgba(59, 31, 25, .65);
  --rm-panel-strong: rgba(70, 36, 28, .71);
  --rm-text: #fff6f1;
  --rm-muted: #cbb3a8;
  --rm-dim: #987c71;
  --rm-line: rgba(205, 134, 103, .21);
  --rm-board-border: rgba(221, 146, 111, .25);
  --rm-board-surface: repeating-linear-gradient(0deg, rgba(239,166,129,.02) 0 1px, transparent 1px 4px), linear-gradient(90deg, rgba(235,145,104,.018) 1px, transparent 1px), radial-gradient(circle at 50% -16%, rgba(229,139,91,.17), transparent 39%), radial-gradient(circle at 100% 8%, rgba(112,203,195,.08), transparent 35%), rgba(28,15,12,.78);
  --rm-board-shadow: 0 30px 86px rgba(0,0,0,.55), inset 0 1px rgba(255,217,198,.09);
  --rm-board-blur: 14px;
  --rm-board-saturation: 118%;
  --rm-panel-border: rgba(218, 139, 103, .21);
  --rm-panel-surface: repeating-linear-gradient(0deg, rgba(255,192,160,.018) 0 1px, transparent 1px 4px), linear-gradient(145deg, rgba(77, 39, 30, .59), rgba(29, 22, 20, .68));
  --rm-panel-shadow: inset 0 1px rgba(255,218,199,.08), 0 14px 31px rgba(0,0,0,.19);
  --rm-panel-sheen: linear-gradient(128deg, rgba(255,204,178,.06), transparent 30%), radial-gradient(circle at 50% 0, rgba(229,139,91,.07), transparent 40%);
  --rm-panel-sheen-opacity: .74;
  --rm-panel-blur: 10px;
  --rm-panel-saturation: 118%;
  --rm-kpi-border: rgba(214, 136, 101, .21);
  --rm-kpi-surface: linear-gradient(145deg, rgba(76, 39, 31, .58), rgba(30, 23, 21, .58));
  --rm-kpi-shadow: inset 0 1px rgba(255,216,195,.08), 0 12px 24px rgba(0,0,0,.17);
  --rm-inner-surface: linear-gradient(145deg, rgba(70, 35, 29, .54), rgba(29, 23, 21, .47));
  --rm-deep-surface: linear-gradient(145deg, rgba(55, 29, 24, .52), rgba(23, 19, 18, .58));
  --rm-soft-surface: rgba(57, 31, 26, .29);
}

.raw-material-page[data-theme="ivory"] {
  --rm-page-surface:
    radial-gradient(circle at 12% 10%, rgba(55, 158, 169, .16), transparent 30%),
    radial-gradient(circle at 88% 13%, rgba(68, 126, 190, .13), transparent 32%),
    radial-gradient(circle at 54% 106%, rgba(137, 180, 195, .19), transparent 44%),
    linear-gradient(145deg, #f8fbfc 0%, #eaf2f5 50%, #f7fafb 100%);
  --rm-page-haze:
    radial-gradient(ellipse at 17% 32%, rgba(81, 179, 181, .16) 0 8%, transparent 27%),
    radial-gradient(ellipse at 79% 21%, rgba(100, 151, 205, .13) 0 9%, transparent 29%),
    radial-gradient(ellipse at 66% 79%, rgba(121, 169, 184, .15) 0 11%, transparent 31%);
  --rm-noise-opacity: .035;
}
.raw-material-page[data-theme="ivory"] .raw-board {
  --rm-accent: #087f83;
  --rm-cyan: #2563a8;
  --rm-flow-out: #c66a13;
  --rm-warning: #ad5b0c;
  --rm-danger: #c73552;
  --rm-bg: rgba(255, 255, 255, .72);
  --rm-panel: rgba(255, 255, 255, .75);
  --rm-panel-strong: rgba(249, 252, 253, .9);
  --rm-text: #17313f;
  --rm-muted: #526b79;
  --rm-dim: #6d8290;
  --rm-line: rgba(48, 87, 105, .16);
  --rm-board-border: rgba(67, 106, 124, .2);
  --rm-board-surface:
    linear-gradient(rgba(45, 91, 108, .035) 1px, transparent 1px),
    linear-gradient(90deg, rgba(45, 91, 108, .03) 1px, transparent 1px),
    radial-gradient(circle at 48% -16%, rgba(41, 145, 153, .13), transparent 40%),
    radial-gradient(circle at 100% 8%, rgba(52, 108, 175, .1), transparent 35%),
    rgba(255, 255, 255, .72);
  --rm-board-shadow: 0 28px 70px rgba(58, 83, 96, .16), inset 0 1px rgba(255,255,255,.96);
  --rm-board-blur: 26px;
  --rm-board-saturation: 112%;
  --rm-panel-border: rgba(55, 96, 114, .17);
  --rm-panel-surface: linear-gradient(145deg, rgba(255,255,255,.86), rgba(238,246,248,.76));
  --rm-panel-shadow: inset 0 1px rgba(255,255,255,.96), 0 12px 28px rgba(58,83,96,.09);
  --rm-panel-sheen: linear-gradient(128deg, rgba(255,255,255,.72), transparent 30%), radial-gradient(circle at 52% 0, rgba(63,160,166,.08), transparent 42%);
  --rm-panel-sheen-opacity: .7;
  --rm-panel-blur: 20px;
  --rm-panel-saturation: 108%;
  --rm-kpi-border: rgba(55, 96, 114, .16);
  --rm-kpi-surface: linear-gradient(145deg, rgba(255,255,255,.93), rgba(234,244,247,.84));
  --rm-kpi-shadow: inset 0 1px rgba(255,255,255,.98), 0 10px 22px rgba(58,83,96,.08);
  --rm-inner-surface: linear-gradient(145deg, rgba(255,255,255,.88), rgba(231,242,245,.77));
  --rm-deep-surface: linear-gradient(145deg, rgba(239,247,249,.94), rgba(219,234,239,.8));
  --rm-soft-surface: rgba(221, 236, 241, .62);
}
.raw-material-page[data-theme="ivory"] .raw-theme-picker { background: rgba(255,255,255,.58); }
.raw-material-page[data-theme="ivory"] .raw-theme-picker select { color-scheme: light; }
.raw-material-page[data-theme="ivory"] .raw-theme-picker option { color: #17313f; background: #fff; }
.raw-material-page[data-theme="ivory"] .raw-data-stamp { color: #315966; }
.raw-material-page[data-theme="ivory"] .raw-refresh { color: #526b79; border-color: rgba(55,96,114,.16); background: rgba(255,255,255,.55); }
.raw-material-page[data-theme="ivory"] .raw-today-pair > div:first-child strong { color: #087f83; }
.raw-material-page[data-theme="ivory"] .raw-today-pair > div:last-child strong { color: #a9530b; }
.raw-material-page[data-theme="ivory"] .raw-silo-card-head b,
.raw-material-page[data-theme="ivory"] .raw-silo-material-line > span,
.raw-material-page[data-theme="ivory"] .raw-silo-copy > strong,
.raw-material-page[data-theme="ivory"] .raw-month-primary strong,
.raw-material-page[data-theme="ivory"] .raw-month-breakdown b,
.raw-material-page[data-theme="ivory"] .raw-zone-card header strong { color: var(--rm-text); }
.raw-material-page[data-theme="ivory"] .raw-target-strip strong.warning + small { color: #874706; }
.raw-material-page[data-theme="ivory"] .raw-kpi-card.is-warning { border-color: rgba(173,91,12,.3); background: linear-gradient(145deg, rgba(255,249,232,.95), rgba(248,237,209,.84)); }
.raw-material-page[data-theme="ivory"] .raw-kpi-card.is-danger { border-color: rgba(199,53,82,.3); background: linear-gradient(145deg, rgba(255,245,247,.96), rgba(249,228,233,.84)); }
.raw-material-page[data-theme="ivory"] .raw-alert-viewport::-webkit-scrollbar-thumb { border-color: rgba(236,244,247,.9); }

.raw-material-page[data-theme="glacier"] .raw-alert-list-item,
.raw-material-page[data-theme="ember"] .raw-alert-list-item,
.raw-material-page[data-theme="aurora"] .raw-alert-list-item,
.raw-material-page[data-theme="titan"] .raw-alert-list-item,
.raw-material-page[data-theme="jade"] .raw-alert-list-item,
.raw-material-page[data-theme="ivory"] .raw-alert-list-item,
.raw-material-page[data-theme="copper"] .raw-alert-list-item { background: var(--rm-inner-surface); }
.raw-material-page[data-theme="glacier"] .raw-alert-list-item.is-breached,
.raw-material-page[data-theme="ember"] .raw-alert-list-item.is-breached,
.raw-material-page[data-theme="aurora"] .raw-alert-list-item.is-breached,
.raw-material-page[data-theme="titan"] .raw-alert-list-item.is-breached,
.raw-material-page[data-theme="jade"] .raw-alert-list-item.is-breached,
.raw-material-page[data-theme="ivory"] .raw-alert-list-item.is-breached,
.raw-material-page[data-theme="copper"] .raw-alert-list-item.is-breached { background: linear-gradient(90deg, color-mix(in srgb, var(--rm-danger) 10%, transparent), transparent 42%), var(--rm-inner-surface); }
.raw-material-page[data-theme="glacier"] .raw-alert-viewport::-webkit-scrollbar-thumb,
.raw-material-page[data-theme="ember"] .raw-alert-viewport::-webkit-scrollbar-thumb,
.raw-material-page[data-theme="aurora"] .raw-alert-viewport::-webkit-scrollbar-thumb,
.raw-material-page[data-theme="titan"] .raw-alert-viewport::-webkit-scrollbar-thumb,
.raw-material-page[data-theme="jade"] .raw-alert-viewport::-webkit-scrollbar-thumb,
.raw-material-page[data-theme="ivory"] .raw-alert-viewport::-webkit-scrollbar-thumb,
.raw-material-page[data-theme="copper"] .raw-alert-viewport::-webkit-scrollbar-thumb { background: color-mix(in srgb, var(--rm-accent) 74%, transparent); }

@media (min-width: 761px) and (max-width: 1600px) {
  .raw-title span { display: none; }
  .raw-title p { font-size: 7px; }
  .raw-title h1 { margin: 4px 0 2px; font-size: clamp(20px, 1.9cqw, 29px); }
  .raw-title-meta { min-height: 16px; }
  .raw-theme-picker { height: 16px; padding-inline: 4px; border-radius: 6px; }
  .raw-theme-picker svg { width: 9px; height: 9px; flex-basis: 9px; }
  .raw-theme-picker select { max-width: 82px; font-size: 7px; }
  .raw-kpi-strip article { gap: 6px; padding: 5px 7px; }
  .raw-kpi-icon { width: 30px; height: 30px; flex-basis: 30px; }
  .raw-kpi-strip small { font-size: 8px; }
  .raw-kpi-strip strong { margin-top: 2px; font-size: clamp(16px, 1.45cqw, 22px); }
  .raw-kpi-strip em { font-size: 7px; }
  .raw-kpi-strip p { margin-top: 2px; font-size: 7px; }

  .raw-panel-title { padding: 8px 10px 3px; }
  .raw-panel-title h2 { font-size: clamp(11px, .95cqw, 15px); }

  .raw-today-pair { gap: 6px; margin-inline: 9px; }
  .raw-today-pair > div { padding: 6px 8px; }
  .raw-today-pair strong { font-size: clamp(17px, 1.5cqw, 23px); }
  .raw-trend-chart { width: auto !important; max-width: calc(100% - 8px); height: clamp(96px, 9.6cqw, 155px) !important; overflow: hidden; }
  .raw-flow-foot { padding: 4px 10px 7px; }

  .raw-posture-panel { display: flex; flex-direction: column; padding-bottom: 2px; }
  .raw-silo-overview { gap: 1px; margin: 3px 8px 4px; padding: 3px 2px; }
  .raw-silo-card { padding: 3px 5px 4px; }
  .raw-silo-card-head { min-height: 18px; }
  .raw-silo-card-head b, .raw-silo-card-head small { font-size: 7px; }
  .raw-silo-card-head > strong { font-size: clamp(11px, .82cqw, 14px); }
  .raw-silo-card-body { grid-template-rows: minmax(68px, 1fr) auto; gap: 1px; padding-top: 0; }
  .raw-silo-vessel { width: 43px; height: 78px; }
  .raw-silo-shell { width: 40px; height: 63px; }
  .raw-silo-material-line > span { font-size: 8px; }
  .raw-silo-material-line > small { font-size: 6px; }
  .raw-silo-copy > strong { margin-top: 1px; font-size: clamp(11px, .78cqw, 14px); }
  .raw-silo-copy p { margin-top: 1px; font-size: 6px; }
  .raw-posture-metrics { margin-inline: 8px; }
  .raw-posture-metrics > div { min-height: 29px; padding: 3px; }
  .raw-posture-metrics strong { font-size: clamp(11px, .86cqw, 14px); }

  .raw-month-overview { gap: 5px; margin: 1px 8px 4px; }
  .raw-month-overview > div { min-height: 52px; grid-template-columns: 26px minmax(0, 1fr) auto; gap: 5px; padding: 4px 6px; }
  .raw-month-icon { width: 25px; height: 25px; }
  .raw-month-breakdown { min-width: 76px; }
  .raw-month-quality { margin: 0 8px 4px; }
  .raw-month-quality > div { padding: 5px 2px; }
  .raw-month-foot { padding: 3px 10px 7px; }

  .raw-zone-overview { padding: 0 10px 5px; }
  .raw-zone-overview > div strong { font-size: clamp(18px, 1.5cqw, 23px); }
  .raw-zone-grid { gap: 4px; padding: 6px 8px 8px; }
  .raw-zone-card { padding: 5px 6px; }
  .raw-zone-card header small { display: none; }
  .raw-zone-card header > em { font-size: clamp(11px, .85cqw, 14px); }
  .raw-zone-card p { margin-top: 3px; font-size: 7px; }
  .raw-zone-card footer { gap: 2px 4px; margin-top: 3px; padding-top: 3px; font-size: 6px; }

  .raw-target-strip { margin: 0 9px 5px; }
  .raw-target-strip > div { padding: 5px 3px; }
  .raw-target-strip strong { margin-block: 2px 1px; font-size: clamp(12px, .9cqw, 14px); }
  .raw-alert-list-item { min-height: 30px; padding: 4px 6px; }
  .raw-alert-list-item strong { font-size: 8px; }
  .raw-alert-list-item small { margin-top: 1px; font-size: 7px; }
  .raw-risk-foot { min-height: 24px; margin-top: 3px; padding: 3px 9px 4px; }
}

@media (min-width: 1601px) {
  .raw-board { padding-inline: 18px; padding-bottom: 18px; }
  .raw-brand strong, .raw-title span { font-size: 11px; }
  .raw-kpi-strip small, .raw-kpi-strip p { font-size: 11px; }
  .raw-panel-title p, .raw-flow-foot, .raw-month-foot { font-size: 10px; }
  .raw-alert-list-item strong { font-size: 11px; }
  .raw-alert-list-item small, .raw-zone-card p { font-size: 9px; }
}

@media (max-width: 1200px) and (min-width: 761px) {
  .raw-masthead { grid-template-columns: minmax(0, 1fr) minmax(270px, 1.1fr); }
  .raw-time { display: none; }
  .raw-brand-mark { width: 38px; height: 38px; flex-basis: 38px; }
  .raw-risk-foot p { display: none; }
}

@media (max-width: 900px) {
  .raw-material-page { display: block; height: auto; overflow: visible; }
  .raw-material-page::before { inset: -12% 0; }
  .raw-board { width: 100%; max-height: none; min-height: 100dvh; aspect-ratio: auto; overflow: visible; padding: 0 10px 10px; border: 0; }
  .raw-masthead { height: 64px; grid-template-columns: auto 1fr; }
  .raw-mobile-menu { width: 44px; height: 44px; }
  .raw-mobile-menu { display: inline-flex; }
  .raw-brand-mark, .raw-brand p, .raw-brand strong { display: none; }
  .raw-time { display: none; }
  .raw-title { text-align: right; }
  .raw-title h1 { font-size: 20px; white-space: nowrap; }
  .raw-kpi-strip { display: grid; height: auto; grid-template-columns: repeat(2, minmax(0, 1fr)); overflow: visible; }
  .raw-kpi-strip article { min-width: 0; }
  .raw-kpi-strip article:last-child { grid-column: 1 / -1; }
  .raw-board-grid { height: auto; grid-template-columns: 1fr; grid-template-rows: auto; }
  .raw-panel { min-height: 315px; }
  .raw-posture-panel { min-height: 430px; }
  .raw-month-panel { min-height: 260px; }
  .raw-risk-panel { height: 420px; min-height: 420px; }
  .raw-posture-panel, .raw-zone-panel { grid-column: auto; }
  .raw-panel { grid-column: 1; }
  .raw-silo-overview { min-height: 285px; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 3px; padding: 5px; }
  .raw-silo-card { padding: 7px; }
  .raw-silo-card:nth-child(2n)::after { display: none; }
  .raw-silo-card-body { grid-template-rows: minmax(70px, 1fr) auto; gap: 2px; }
  .raw-silo-vessel { width: 48px; height: 78px; }
  .raw-silo-shell { width: 44px; height: 63px; }
  .raw-silo-material-line > span { font-size: 10px; }
  .raw-silo-material-line > small, .raw-silo-copy p { font-size: 8px; }
  .raw-silo-copy > strong { font-size: 16px; }
  .raw-month-overview { flex: 0 0 auto; }
  .raw-month-overview > div { min-height: 58px; }
  .raw-zone-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); grid-template-rows: auto; }
  .raw-zone-card { min-height: 105px; }
  .raw-trend-chart { height: 270px !important; }
}

@media (max-height: 980px) and (min-width: 901px) {
  .raw-silo-overview { min-height: 116px; }
  .raw-month-overview > div { min-height: 58px; }
}

@media (max-width: 420px) {
  .raw-title p, .raw-title span { display: none; }
  .raw-posture-metrics { grid-template-columns: repeat(2, 1fr); }
  .raw-posture-metrics > div { border-bottom: 1px solid var(--rm-line); }
  .raw-posture-metrics > div:nth-child(2n) { border-right: 0; }
  .raw-posture-metrics > div:nth-child(n+3) { border-bottom: 0; }
}

/* Reference-aligned industrial cockpit */
.raw-material-page {
  --rm-page-surface: #020b14;
  --rm-page-haze: radial-gradient(circle at 50% -12%, rgba(0, 183, 229, .12), transparent 38%);
  --rm-noise-opacity: .025;
  background: #020b14;
}
.raw-material-page::before { inset: 0; filter: none; }
.raw-board {
  --rm-accent: #12d6cc;
  --rm-cyan: #18c9e8;
  --rm-flow-out: #f6b83f;
  --rm-warning: #f6b83f;
  --rm-danger: #ff6a78;
  --rm-bg: #020d18;
  --rm-panel: #041a2b;
  --rm-panel-strong: #062239;
  --rm-text: #ebf8ff;
  --rm-muted: #91afc1;
  --rm-dim: #5f8195;
  --rm-line: rgba(31, 135, 173, .27);
  --rm-board-border: transparent;
  --rm-board-surface:
    linear-gradient(rgba(23, 113, 151, .035) 1px, transparent 1px),
    linear-gradient(90deg, rgba(23, 113, 151, .035) 1px, transparent 1px),
    radial-gradient(circle at 50% -12%, rgba(0, 196, 232, .1), transparent 34%),
    #020d18;
  --rm-board-shadow: none;
  --rm-board-blur: 0px;
  --rm-board-saturation: 100%;
  --rm-panel-border: rgba(12, 139, 181, .48);
  --rm-panel-surface: linear-gradient(180deg, rgba(4, 29, 49, .97), rgba(2, 19, 34, .98));
  --rm-panel-shadow: inset 0 1px rgba(68, 204, 239, .06), 0 0 18px rgba(0, 141, 188, .035);
  --rm-panel-sheen: linear-gradient(125deg, rgba(23, 190, 224, .035), transparent 34%);
  --rm-panel-sheen-opacity: 1;
  --rm-panel-blur: 0px;
  --rm-panel-saturation: 100%;
  --rm-kpi-border: rgba(12, 146, 188, .48);
  --rm-kpi-surface: linear-gradient(135deg, rgba(4, 37, 59, .98), rgba(2, 23, 39, .98));
  --rm-kpi-shadow: inset 0 1px rgba(78, 222, 242, .07), 0 0 20px rgba(0, 166, 204, .035);
  --rm-inner-surface: linear-gradient(145deg, rgba(8, 42, 65, .82), rgba(3, 26, 44, .86));
  --rm-deep-surface: rgba(2, 22, 38, .72);
  --rm-soft-surface: rgba(4, 31, 50, .7);
  padding: 0 clamp(14px, 1.5cqw, 25px) clamp(18px, 2.5cqw, 42px);
  border: 0;
  border-radius: 0;
  box-shadow: none;
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}
.raw-board::before {
  position: absolute;
  z-index: 18;
  inset: 1px 3px 9px;
  border: 1px solid rgba(10, 132, 174, .6);
  content: "";
  pointer-events: none;
  opacity: .92;
  background:
    linear-gradient(90deg, transparent 0 5%, rgba(18, 211, 232, .82) 5% 13%, transparent 13% 87%, rgba(18, 211, 232, .82) 87% 95%, transparent 95%) top / 100% 1px no-repeat,
    linear-gradient(180deg, transparent 0 8%, rgba(11, 132, 174, .72) 8% 92%, transparent 92%) left / 1px 100% no-repeat,
    linear-gradient(180deg, transparent 0 8%, rgba(11, 132, 174, .72) 8% 92%, transparent 92%) right / 1px 100% no-repeat;
  clip-path: polygon(0 2.4%, 27.8% 2.4%, 29.4% 0, 70.6% 0, 72.2% 2.4%, 100% 2.4%, 100% 96.8%, 98.3% 100%, 1.7% 100%, 0 96.8%);
  box-shadow: inset 0 0 18px rgba(0, 158, 205, .025);
}
.raw-board::after {
  position: absolute;
  right: 2.6%;
  bottom: 13px;
  left: 2.6%;
  height: 15px;
  border-top: 1px solid rgba(10, 139, 180, .58);
  border-bottom: 1px solid rgba(7, 92, 125, .48);
  content: "";
  pointer-events: none;
  background: linear-gradient(90deg, transparent 46%, var(--rm-accent) 46% 54%, transparent 54%) top / 100% 1px no-repeat;
  clip-path: polygon(0 0, 31% 0, 32% 35%, 68% 35%, 69% 0, 100% 0, 100% 100%, 0 100%);
}

.raw-masthead {
  position: relative;
  height: clamp(68px, 5cqw, 84px);
  grid-template-columns: minmax(350px, 1fr) minmax(420px, 1.15fr) minmax(430px, 1fr);
  border-bottom-color: rgba(14, 137, 177, .42);
}
.raw-brand { gap: 14px; }
.raw-brand-mark {
  width: clamp(48px, 3.55cqw, 60px);
  height: clamp(48px, 3.55cqw, 60px);
  flex-basis: clamp(48px, 3.55cqw, 60px);
  border: 1px solid rgba(16, 201, 226, .58);
  border-radius: 0;
  color: #19d8e3;
  background: linear-gradient(145deg, rgba(8, 80, 107, .74), rgba(2, 30, 49, .9));
  clip-path: polygon(25% 4%, 75% 4%, 100% 27%, 100% 73%, 75% 96%, 25% 96%, 0 73%, 0 27%);
}
.raw-brand p { font-size: clamp(8px, .65cqw, 11px); letter-spacing: .17em; }
.raw-brand strong { margin-top: 8px; color: #79cde0; font-size: clamp(10px, .78cqw, 13px); font-weight: 600; letter-spacing: .08em; }
.raw-title {
  position: relative;
  align-self: stretch;
  display: grid;
  place-content: center;
}
.raw-title::before,
.raw-title::after {
  position: absolute;
  top: 0;
  bottom: 8px;
  width: 35%;
  border-bottom: 1px solid rgba(17, 195, 228, .68);
  content: "";
  pointer-events: none;
}
.raw-title::before { left: -8%; transform: skewX(40deg); }
.raw-title::after { right: -8%; transform: skewX(-40deg); }
.raw-title h1 {
  position: relative;
  z-index: 1;
  margin: 0;
  color: #e8f8ff;
  font-size: clamp(28px, 2.5cqw, 42px);
  font-weight: 800;
  letter-spacing: .08em;
  text-shadow: 0 0 18px rgba(126, 222, 244, .38), 0 2px 0 rgba(116, 190, 210, .18);
}
.raw-title > span { position: relative; z-index: 1; margin-top: 7px; font-size: clamp(8px, .56cqw, 10px); }
.raw-time { gap: 10px; }
.raw-clock { min-width: 106px; padding-right: 10px; border-right: 1px solid rgba(20, 93, 125, .36); }
.raw-time .raw-clock strong { font-size: clamp(16px, 1.12cqw, 20px); }
.raw-time .raw-clock span { font-size: clamp(8px, .55cqw, 10px); }
.raw-data-stamp {
  display: grid;
  min-width: 210px;
  grid-template-columns: 8px 1fr;
  gap: 3px 8px;
  padding: 8px 11px;
  border-color: rgba(16, 113, 151, .42);
  border-radius: 7px;
  color: #b7d0dd;
  background: rgba(2, 20, 35, .78);
}
.raw-data-stamp i { grid-row: 1 / 3; align-self: center; }
.raw-data-stamp small { grid-column: 2; color: var(--rm-dim); font-size: 8px; }
.raw-control {
  display: grid;
  width: 52px;
  height: 50px;
  flex: 0 0 52px;
  place-content: center;
  gap: 3px;
  cursor: pointer;
  border: 1px solid rgba(17, 105, 142, .5);
  border-radius: 7px;
  color: #83aabd;
  background: rgba(2, 19, 33, .72);
  transition: color .18s ease, border-color .18s ease, background-color .18s ease;
}
.raw-control span { font-size: 8px; }
.raw-control:hover,
.raw-control:focus-visible { outline: none; border-color: rgba(18, 213, 204, .76); color: var(--rm-accent); background: rgba(6, 48, 63, .8); }
.raw-control:active { transform: translateY(1px); }
.raw-control:disabled { cursor: wait; opacity: .68; }

.raw-kpi-strip {
  height: clamp(86px, 6.85cqw, 115px);
  gap: clamp(7px, .7cqw, 12px);
  padding: clamp(7px, .55cqw, 9px) 0 clamp(7px, .65cqw, 11px);
}
.raw-kpi-strip article {
  position: relative;
  gap: clamp(9px, .85cqw, 15px);
  padding: clamp(9px, .85cqw, 15px) clamp(34px, 2.6cqw, 44px) clamp(9px, .85cqw, 15px) clamp(9px, .85cqw, 15px);
  border-radius: 11px;
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}
.raw-kpi-strip article > div { min-width: 0; }
.raw-kpi-arrow {
  position: absolute;
  z-index: 3;
  top: 50%;
  right: 5px;
  display: grid;
  width: 32px;
  height: 44px;
  place-items: center;
  cursor: pointer;
  border: 0;
  color: #6d9bad;
  background: transparent;
  transform: translateY(-50%);
  transition: color .18s ease, transform .18s ease;
}
.raw-kpi-arrow svg { width: 16px; height: 16px; stroke-width: 1.7; }
.raw-kpi-arrow:hover,
.raw-kpi-arrow:focus-visible { outline: none; color: var(--rm-accent); transform: translate(2px, -50%); }
.raw-kpi-card.is-warning .raw-kpi-arrow { color: #c49b47; }
.raw-kpi-card.is-danger .raw-kpi-arrow { color: #ce6674; }
.raw-kpi-card::after { right: auto; width: 3px; height: 100%; opacity: .72; }
.raw-kpi-card.is-warning::after,
.raw-kpi-card.is-danger::after { height: 100%; }
.raw-kpi-icon {
  width: clamp(44px, 3.6cqw, 61px);
  height: clamp(44px, 3.6cqw, 61px);
  flex-basis: clamp(44px, 3.6cqw, 61px);
  border-radius: 8px;
  background: transparent !important;
}
.raw-kpi-icon.round { border: 1px solid currentColor; border-radius: 50%; box-shadow: inset 0 0 18px color-mix(in srgb, currentColor 10%, transparent), 0 0 12px color-mix(in srgb, currentColor 12%, transparent); }
.raw-kpi-icon svg { width: clamp(31px, 2.55cqw, 43px); height: clamp(31px, 2.55cqw, 43px); stroke-width: 1.55; filter: drop-shadow(0 0 6px currentColor); }
.raw-kpi-strip small { color: #91acbd; font-size: clamp(9px, .68cqw, 12px); }
.raw-kpi-strip strong { margin-top: 4px; font-size: clamp(22px, 1.8cqw, 31px); }
.raw-kpi-strip p { margin-top: 6px; color: #6e8da0; font-size: clamp(8px, .58cqw, 10px); }
.raw-kpi-strip p b { margin-left: 3px; font-weight: 700; }
.raw-kpi-strip p b.positive { color: var(--rm-accent); }
.raw-kpi-strip p b.warning { color: var(--rm-warning); }
.raw-kpi-strip p b.danger { color: var(--rm-danger); }
.raw-kpi-card.is-warning { background: linear-gradient(135deg, rgba(61, 48, 24, .75), rgba(3, 24, 40, .97)); }
.raw-kpi-card.is-danger { background: linear-gradient(135deg, rgba(57, 24, 39, .78), rgba(4, 23, 39, .97)); }

.raw-board-grid {
  grid-template-columns: repeat(30, minmax(0, 1fr));
  grid-template-rows: minmax(0, .96fr) minmax(0, 1fr);
  gap: clamp(7px, .65cqw, 11px);
}
.raw-flow-panel { grid-column: span 12; }
.raw-posture-panel { grid-column: span 18; }
.raw-month-panel { grid-column: span 8; }
.raw-zone-panel { grid-column: span 12; }
.raw-risk-panel { grid-column: span 10; }
.raw-panel { border-radius: 11px; backdrop-filter: none; -webkit-backdrop-filter: none; }
.raw-panel::before {
  background:
    linear-gradient(rgba(42, 129, 165, .025) 1px, transparent 1px),
    linear-gradient(90deg, rgba(42, 129, 165, .022) 1px, transparent 1px),
    var(--rm-panel-sheen);
  background-size: 36px 36px, 36px 36px, auto;
}
.raw-panel-accent { left: 0; width: 100%; height: 1px; opacity: .42; background: linear-gradient(90deg, transparent, var(--rm-cyan) 20%, transparent 72%); }
.raw-panel-accent.centered-accent { left: 0; width: 100%; }
.raw-panel-title {
  min-height: clamp(36px, 2.5cqw, 42px);
  padding: clamp(5px, .42cqw, 7px) clamp(10px, .78cqw, 13px) 4px;
  border-bottom: 1px solid rgba(14, 105, 143, .28);
  text-align: left !important;
}
.raw-panel-title h2 { justify-content: flex-start; color: #dceef7; font-size: clamp(12px, .95cqw, 16px); letter-spacing: .02em; }
.raw-panel-title h2 > span { display: none; }
.raw-panel-title h2 svg { width: 13px; height: 13px; color: #64899f; stroke-width: 1.7; }
.raw-panel-title p { margin: 0; color: #7393a6; }
.raw-panel-title p b { margin-left: 6px; color: var(--rm-accent); font-weight: 650; }
.raw-panel-head-actions { display: flex; min-width: 0; align-items: center; justify-content: flex-end; gap: clamp(7px, .65cqw, 11px); }
.raw-panel-link,
.raw-posture-link,
.raw-risk-sla {
  display: inline-flex;
  min-height: 30px;
  align-items: center;
  justify-content: center;
  gap: 2px;
  padding: 0 4px;
  cursor: pointer;
  border: 0;
  border-radius: 5px;
  color: #71bed3;
  font: 600 clamp(8px, .52cqw, 10px) "Noto Sans SC", "Microsoft YaHei UI", sans-serif;
  white-space: nowrap;
  background: transparent;
  transition: color .18s ease, background-color .18s ease, transform .18s ease;
}
.raw-panel-link svg,
.raw-posture-link svg { width: 13px; height: 13px; stroke-width: 1.8; }
.raw-panel-link:hover,
.raw-panel-link:focus-visible,
.raw-posture-link:hover,
.raw-posture-link:focus-visible,
.raw-risk-sla:hover,
.raw-risk-sla:focus-visible { outline: none; color: var(--rm-accent); background: rgba(18, 213, 204, .07); }
.raw-panel-link:active,
.raw-posture-link:active { transform: translateX(1px); }

.raw-today-pair { grid-template-columns: 1fr 1fr .92fr; gap: 6px; margin: 8px 12px 0; }
.raw-today-pair > div { position: relative; min-width: 0; padding: 7px 9px; border-radius: 7px; }
.raw-today-pair > div:last-child { border-left: 1px solid var(--rm-line); }
.raw-today-pair > div:last-child strong { color: var(--rm-accent); }
.raw-today-pair > div:last-child em { display: block; margin-top: 2px; color: var(--rm-dim); font-size: 7px; font-style: normal; }
.raw-today-pair strong { font-size: clamp(18px, 1.45cqw, 25px); }
.raw-trend-chart { min-height: 0; flex: 1 1 auto; height: clamp(150px, 11.8cqw, 198px) !important; margin-top: 1px; }
.raw-flow-foot { min-height: 32px; padding: 3px 9px 4px 13px; }
.raw-flow-foot .raw-panel-link { margin-left: 2px; }

.raw-posture-panel { padding-bottom: 5px; }
.raw-silo-overview {
  gap: clamp(5px, .55cqw, 9px);
  margin: 9px 9px 7px;
  padding: 0;
  overflow: visible;
  border: 0;
  border-radius: 0;
  background: transparent;
}
.raw-silo-card {
  --material-top: #42ebe0;
  --material-mid: #12bfc1;
  --material-bottom: #087a89;
  --material-highlight: #cafffb;
  --material-glow: rgba(22, 214, 207, .32);
  padding: 7px 9px 8px;
  overflow: hidden;
  border: 1px solid rgba(10, 118, 157, .46);
  border-radius: 9px;
  background: linear-gradient(180deg, rgba(3, 32, 53, .94), rgba(2, 22, 38, .96));
  animation: none;
}
.raw-silo-card.is-powder { --material-top: #f6c762; --material-mid: #d89328; --material-bottom: #8f5d12; --material-highlight: #fff0be; --material-glow: rgba(246, 184, 63, .28); }
.raw-silo-card.is-granule { --material-top: #42ebe0; --material-mid: #11bdc1; --material-bottom: #087888; --material-highlight: #cafffb; --material-glow: rgba(22, 214, 207, .32); }
.raw-silo-card.is-roll { --material-top: #38dfda; --material-mid: #10aeb7; --material-bottom: #087482; --material-highlight: #c9ffff; --material-glow: rgba(20, 196, 202, .28); }
.raw-silo-card.is-liquid { --material-top: #39e6ce; --material-mid: #12b6a9; --material-bottom: #08796e; --material-highlight: #cafff3; --material-glow: rgba(22, 207, 187, .28); }
.raw-silo-card:not(:last-child)::after { display: none; }
.raw-silo-card-head { min-height: 23px; align-items: center; }
.raw-silo-card-head > span { gap: 4px; overflow: hidden; }
.raw-silo-card-head b { font-size: clamp(8px, .54cqw, 10px); }
.raw-silo-card-head em { overflow: hidden; color: #b9d0dc; font-size: clamp(7px, .48cqw, 9px); font-style: normal; text-overflow: ellipsis; white-space: nowrap; }
.raw-silo-card-head small { margin-left: auto; padding: 2px 5px; border: 1px solid rgba(23, 199, 146, .23); border-radius: 5px; color: #24c891; background: rgba(14, 128, 96, .13); }
.raw-silo-card.is-high .raw-silo-card-head small { border-color: rgba(246, 184, 63, .28); color: var(--rm-warning); background: rgba(135, 90, 13, .14); }
.raw-silo-card-body {
  min-height: 0;
  grid-template-columns: minmax(84px, .94fr) minmax(0, 1fr);
  grid-template-rows: 1fr;
  justify-items: stretch;
  align-items: stretch;
  gap: 5px;
  overflow: hidden;
  padding: 2px 0 3px;
}
.raw-silo-model {
  width: clamp(82px, 5.8cqw, 98px);
  height: 100%;
  min-height: 0;
  max-height: 160px;
  align-self: center;
  justify-self: center;
}
.raw-silo-vessel {
  width: clamp(70px, 5.1cqw, 86px);
  height: clamp(116px, 8.3cqw, 140px);
  scale: 1;
  background:
    linear-gradient(63deg, transparent 47%, rgba(167, 193, 202, .9) 48% 51%, transparent 52%) 7% 84% / 44% 27% no-repeat,
    linear-gradient(-63deg, transparent 47%, rgba(167, 193, 202, .9) 48% 51%, transparent 52%) 93% 84% / 44% 27% no-repeat;
  filter: drop-shadow(0 8px 8px rgba(0, 0, 0, .38)) drop-shadow(0 0 6px var(--material-glow));
  animation: none;
}
.raw-silo-card.is-high .raw-silo-vessel,
.raw-silo-card.is-low .raw-silo-vessel { filter: drop-shadow(0 8px 8px rgba(0, 0, 0, .38)) drop-shadow(0 0 6px var(--material-glow)); }
.raw-silo-vessel::before,
.raw-silo-vessel::after {
  z-index: 5;
  bottom: 1%;
  width: 5%;
  height: 29%;
  border-radius: 1px;
  background: linear-gradient(90deg, #415966, #d6e5e8 48%, #526c78);
  box-shadow: 0 0 0 1px rgba(4, 24, 35, .55);
}
.raw-silo-vessel::before { left: 17%; transform: rotate(2deg); }
.raw-silo-vessel::after { right: 17%; transform: rotate(-2deg); }
.raw-silo-shell {
  top: 10%;
  width: 74%;
  height: 69%;
  border: 1px solid rgba(218, 237, 241, .88);
  border-radius: 45% 45% 9% 9% / 13% 13% 7% 7%;
  background:
    repeating-linear-gradient(0deg, transparent 0 30%, rgba(62, 91, 103, .16) 30% 31%),
    linear-gradient(90deg, #536b77 0%, #9fb5bd 12%, #eef7f8 31%, #cbdce1 48%, #92aab4 66%, #dce9ec 79%, #536d79 100%);
  box-shadow: inset 7px 0 8px rgba(13, 38, 51, .28), inset -7px 0 9px rgba(13, 36, 48, .28), inset 0 2px 3px rgba(255, 255, 255, .85), 0 0 0 1px rgba(3, 18, 28, .55);
}
.raw-silo-shell::before {
  top: -2%;
  right: -1px;
  left: -1px;
  height: 18%;
  border-color: rgba(227, 242, 244, .88);
  background: radial-gradient(ellipse at 42% 24%, #f6fbfc 0 10%, transparent 27%), linear-gradient(180deg, #e5f0f2, #8ea7b1 56%, #526d79);
  box-shadow: inset 0 3px rgba(255,255,255,.54), inset 0 -3px 5px rgba(34,61,72,.36), 0 2px 4px rgba(0,0,0,.28);
}
.raw-silo-shell::after { top: 37%; right: 2px; left: 2px; opacity: .52; box-shadow: 0 27px rgba(224, 240, 243, .34); }
.raw-silo-neck {
  top: 4%;
  width: 20%;
  height: 9%;
  border-color: rgba(220, 238, 241, .8);
  background: linear-gradient(90deg, #536d79, #e7f1f3 44%, #8ea8b2 68%, #405b68);
}
.raw-silo-cap {
  top: 0;
  width: 28%;
  height: 6%;
  border-color: rgba(227, 241, 244, .72);
  background: radial-gradient(ellipse at 50% 23%, #fbffff, #b7cbd1 44%, #526d79 82%);
}
.raw-silo-outlet {
  z-index: 4;
  bottom: 11%;
  width: 58%;
  height: 31%;
  background: linear-gradient(90deg, #425c69, #b8cbd1 20%, #eef6f7 45%, #91aab4 67%, #465f6b);
  clip-path: polygon(0 0, 100% 0, 66% 70%, 60% 100%, 40% 100%, 34% 70%);
}
.raw-silo-outlet::after { right: 39%; left: 39%; height: 11%; background: linear-gradient(90deg, #566e79, #dce9ec, #536c77); }
.raw-silo-floor-shadow { bottom: -3%; width: 96%; height: 11%; opacity: .82; }
.raw-silo-threshold { display: none; }
.raw-silo-fill {
  right: 2px;
  bottom: 2px;
  left: 2px;
  max-height: calc(100% - 4px);
  border-radius: 0 0 8% 8%;
  opacity: .93;
  box-shadow: inset 9px 0 12px rgba(255,255,255,.13), inset -9px 0 12px rgba(3,20,29,.22), 0 -2px 9px var(--material-glow);
  animation: none;
}
.raw-silo-fill::before,
.raw-silo-fill i,
.raw-silo-shine { animation: none; }
.raw-silo-shine { top: 16%; bottom: 9%; left: 19%; width: 5%; opacity: .6; background: linear-gradient(rgba(255,255,255,.9), rgba(255,255,255,.08)); }
.raw-silo-depth { background: linear-gradient(90deg, rgba(6, 25, 35, .35), transparent 20%, rgba(255,255,255,.1) 42%, rgba(255,255,255,.2) 51%, transparent 70%, rgba(9, 30, 40, .38)); }
.raw-silo-copy { align-self: center; min-width: 0; text-align: left; }
.raw-silo-rate { display: block !important; color: var(--rm-accent) !important; font-size: clamp(21px, 1.55cqw, 27px) !important; }
.raw-silo-card.is-high .raw-silo-rate { color: var(--rm-warning) !important; }
.raw-silo-rate em { display: block; margin: 3px 0 0; color: var(--rm-muted); font-size: 8px; font-style: normal; }
.raw-silo-quantity { display: block !important; margin-top: 13px !important; color: #e6f3f9 !important; font-size: clamp(15px, 1.05cqw, 19px) !important; }
.raw-silo-quantity em { margin-left: 3px; color: var(--rm-muted); font-size: 8px; font-style: normal; }
.raw-silo-copy > p { margin: 4px 0 0; color: #728fa1; font-size: clamp(7px, .48cqw, 9px); }
.raw-silo-copy dl { display: grid; gap: 3px; margin: 11px 0 0; color: #6f8d9f; font-size: clamp(6px, .42cqw, 8px); }
.raw-silo-copy dl div { display: flex; justify-content: space-between; gap: 4px; }
.raw-silo-copy dt,
.raw-silo-copy dd { margin: 0; }
.raw-silo-copy dd { color: #a4bdca; white-space: nowrap; }
.raw-silo-progress { position: relative; z-index: 4; height: 5px; flex: 0 0 5px; margin-top: 6px; overflow: hidden; border-radius: 4px; background: rgba(91, 124, 143, .25); box-shadow: inset 0 1px 2px rgba(0,0,0,.32); }
.raw-silo-progress i { display: block; height: 100%; border-radius: inherit; background: linear-gradient(90deg, var(--material-bottom), var(--material-top)); box-shadow: 0 0 8px var(--material-glow); }
.raw-posture-metrics { grid-template-columns: repeat(4, minmax(0, 1fr)) auto; margin: 0 9px; overflow: hidden; border: 1px solid rgba(9, 102, 139, .32); border-radius: 7px; }
.raw-posture-metrics > div { min-height: clamp(34px, 2.55cqw, 43px); }
.raw-posture-link { min-width: clamp(86px, 7cqw, 118px); border-left: 1px solid var(--rm-line); border-radius: 0; }

.raw-month-section-label { position: relative; z-index: 2; display: flex; min-height: 26px; align-items: center; justify-content: space-between; margin: 0 9px; padding: 3px 7px 2px; border-bottom: 1px solid rgba(14, 105, 143, .22); }
.raw-month-section-label span { color: #9ab5c4; font-size: clamp(8px, .58cqw, 10px); font-weight: 650; }
.raw-month-section-label em { color: #577b8f; font-size: clamp(7px, .46cqw, 8px); font-style: normal; }
.raw-month-overview { grid-template-rows: repeat(2, minmax(62px, 1fr)); gap: 6px; margin: 5px 9px 6px; }
.raw-month-overview > div { grid-template-columns: 36px minmax(0, 1fr) auto; gap: 8px; padding: 7px 9px; border-radius: 7px; }
.raw-month-icon { width: 34px; height: 34px; border-radius: 8px; }
.raw-month-icon svg { width: 18px; height: 18px; }
.raw-month-primary > span { font-size: clamp(8px, .56cqw, 10px); }
.raw-month-primary strong { margin-top: 4px; font-size: clamp(18px, 1.2cqw, 21px); }
.raw-month-primary em { margin-left: 7px; padding: 2px 5px; font-size: clamp(7px, .44cqw, 8px); }
.raw-month-breakdown { min-width: 104px; }
.raw-month-breakdown i { font-size: clamp(7px, .46cqw, 8px); }
.raw-month-breakdown b { margin-top: 5px; font-size: clamp(10px, .66cqw, 12px); }
.raw-month-quality { grid-template-columns: repeat(4, minmax(0, 1fr)); min-height: 62px; margin-bottom: 6px; border-radius: 7px; }
.raw-month-quality > div { grid-template-columns: 15px minmax(0, 1fr); padding: 7px 3px 6px; }
.raw-month-quality svg { width: 13px; height: 13px; }
.raw-month-quality span { font-size: clamp(7px, .48cqw, 9px); }
.raw-month-quality strong { margin-top: 5px; font-size: clamp(13px, .84cqw, 16px); }
.raw-month-foot { min-height: 48px; padding: 7px 10px 8px; }
.raw-month-foot svg { width: 12px; height: 12px; }
.raw-month-foot em { font-size: clamp(7px, .46cqw, 8px); }
.raw-month-foot strong { margin-top: 4px; font-size: clamp(11px, .72cqw, 14px); }
.raw-zone-overview { padding-top: 6px; }
.raw-zone-grid { gap: 5px; }
.raw-zone-foot { position: relative; z-index: 2; display: flex; min-height: 30px; flex: 0 0 auto; align-items: center; justify-content: flex-end; padding: 0 10px 2px; border-top: 1px solid rgba(14, 105, 143, .25); }
.raw-zone-card { border-radius: 6px; box-shadow: none; }
.raw-zone-card.danger { box-shadow: inset 0 2px var(--rm-danger); }
.raw-zone-card.warning { box-shadow: inset 0 2px var(--rm-warning); }
.raw-zone-card.normal { box-shadow: inset 0 2px var(--rm-accent); }
.raw-target-strip { grid-template-columns: repeat(4, minmax(0, 1fr)); margin-top: 5px; margin-bottom: 5px; border-radius: 7px; }
.raw-target-strip > div { min-width: 0; padding: 5px 3px; }
.raw-target-strip strong { margin: 3px 0 2px; }
.raw-target-strip span,
.raw-target-strip small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.raw-alert-subhead { position: relative; z-index: 2; display: flex; min-height: 20px; flex: 0 0 20px; align-items: center; justify-content: space-between; padding: 1px 12px 2px; border-top: 1px solid rgba(14, 105, 143, .18); color: #7b9aab; font-size: clamp(7px, .48cqw, 9px); }
.raw-alert-subhead em { color: #9eb4c0; font-style: normal; }
.raw-alert-list { gap: 4px; padding-right: 8px; }
.raw-alert-list-item {
  position: relative;
  min-height: 34px;
  grid-template-columns: 25px minmax(0, 1fr) auto;
  padding: 3px 7px 3px 8px;
  border: 1px solid rgba(21, 104, 140, .42);
  border-radius: 8px;
  background: linear-gradient(90deg, rgba(11, 54, 78, .82), rgba(3, 29, 48, .95) 46%, rgba(4, 35, 55, .9));
  box-shadow: inset 0 1px rgba(96, 184, 214, .08), inset 0 -1px rgba(0, 8, 14, .46), 0 3px 8px rgba(0, 6, 12, .18);
}
.raw-alert-list-item::before { position: absolute; top: 7px; bottom: 7px; left: 0; width: 2px; border-radius: 0 2px 2px 0; content: ""; background: var(--rm-warning); box-shadow: 4px 0 10px rgba(246, 184, 63, .1); }
.raw-alert-list-item::after { position: absolute; top: 4px; right: 4px; width: 10px; height: 6px; border-top: 1px solid rgba(61, 153, 187, .42); border-right: 1px solid rgba(61, 153, 187, .42); content: ""; }
.raw-alert-list-item.is-breached { border-color: rgba(167, 65, 83, .52); background: linear-gradient(90deg, rgba(92, 32, 48, .36), rgba(4, 31, 50, .94) 38%, rgba(4, 35, 55, .9)); box-shadow: inset 0 1px rgba(255, 132, 145, .07), inset 0 -1px rgba(0, 8, 14, .5), 0 3px 9px rgba(23, 2, 7, .2); }
.raw-alert-list-item.is-breached::before { background: var(--rm-danger); box-shadow: 4px 0 11px rgba(255, 106, 120, .16); }
.raw-alert-list-item > span { width: 21px; height: 21px; border: 1px solid currentColor; box-shadow: 0 0 0 3px color-mix(in srgb, currentColor 10%, transparent), 0 0 9px color-mix(in srgb, currentColor 18%, transparent); }
.raw-alert-end { display: grid; min-width: 54px; justify-items: end; gap: 3px; }
.raw-alert-end > em { color: #b4c8d2; font-size: clamp(7px, .47cqw, 9px); }
.raw-alert-end > span { min-width: 43px; padding: 2px 5px; border: 1px solid currentColor; border-radius: 4px; font-size: clamp(6px, .4cqw, 7px); line-height: 1; text-align: center; }
.raw-alert-end > span.danger { color: var(--rm-danger); background: rgba(156, 47, 65, .17); }
.raw-alert-end > span.warning { color: var(--rm-warning); background: rgba(138, 92, 16, .16); }
.raw-alert-end > span.normal { color: #39ce9b; background: rgba(20, 122, 89, .14); }
.raw-risk-summary { min-width: 0; }
.raw-risk-summary span { padding-left: 7px; border-left: 1px solid var(--rm-line); white-space: nowrap; }
.raw-risk-summary span.danger { color: var(--rm-danger); }
.raw-risk-summary span.warning { color: var(--rm-warning); }
.raw-risk-sla { min-height: 26px; margin: -2px -3px -2px 0; padding: 0 7px; border: 1px solid rgba(28, 109, 142, .38); color: #86a8b8; background: rgba(3, 27, 44, .68); }
.raw-risk-sla strong { margin-left: 4px; color: var(--rm-danger); }

@media (max-width: 1200px) and (min-width: 901px) {
  .raw-masthead { grid-template-columns: minmax(260px, 1fr) minmax(340px, 1.15fr) minmax(280px, 1fr); }
  .raw-data-stamp { min-width: 165px; }
  .raw-data-stamp small { display: none; }
  .raw-control { width: 42px; height: 42px; flex-basis: 42px; }
  .raw-control span { display: none; }
  .raw-silo-vessel { scale: 1; }
  .raw-silo-copy dl { margin-top: 5px; }
}

@media (max-height: 820px) and (min-width: 901px) {
  .raw-month-section-label { min-height: 20px; padding: 1px 6px; }
  .raw-month-overview { grid-template-rows: repeat(2, minmax(47px, 1fr)); gap: 4px; margin: 3px 8px 4px; }
  .raw-month-overview > div { min-height: 0; grid-template-columns: 30px minmax(0, 1fr) auto; gap: 5px; padding: 4px 6px; }
  .raw-month-icon { width: 29px; height: 29px; }
  .raw-month-icon svg { width: 15px; height: 15px; }
  .raw-month-primary > span { font-size: 8px; }
  .raw-month-primary strong { margin-top: 2px; font-size: 16px; }
  .raw-month-primary em { margin-left: 4px; padding: 1px 3px; font-size: 6px; }
  .raw-month-breakdown { min-width: 87px; }
  .raw-month-breakdown i { font-size: 6px; }
  .raw-month-breakdown b { margin-top: 3px; font-size: 9px; }
  .raw-month-quality { min-height: 46px; margin: 0 8px 4px; }
  .raw-month-quality > div { padding: 4px 2px 3px; }
  .raw-month-quality svg { width: 11px; height: 11px; }
  .raw-month-quality span { font-size: 6px; }
  .raw-month-quality strong { margin-top: 3px; font-size: 11px; }
  .raw-month-foot { min-height: 38px; padding: 4px 8px 5px; }
  .raw-month-foot svg { width: 10px; height: 10px; }
  .raw-month-foot em { font-size: 6px; }
  .raw-month-foot strong { margin-top: 2px; font-size: 10px; }
  .raw-alert-subhead { min-height: 18px; flex-basis: 18px; padding-inline: 9px; }
  .raw-alert-list-item { min-height: 32px; padding-block: 2px; }
  .raw-alert-end { gap: 1px; }
}

@media (max-width: 900px) {
  .raw-board { padding: 0 10px 12px; }
  .raw-board::before,
  .raw-board::after { display: none; }
  .raw-masthead { height: 64px; grid-template-columns: auto 1fr; }
  .raw-title::before,
  .raw-title::after { display: none; }
  .raw-kpi-strip { height: auto; }
  .raw-board-grid { grid-template-columns: 1fr; grid-template-rows: auto; }
  .raw-flow-panel,
  .raw-posture-panel,
  .raw-month-panel,
  .raw-zone-panel,
  .raw-risk-panel { grid-column: 1; }
  .raw-today-pair { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .raw-today-pair > div:last-child { grid-column: 1 / -1; }
  .raw-silo-card-body { grid-template-columns: minmax(68px, .8fr) minmax(0, 1fr); }
  .raw-silo-vessel { scale: 1; }
  .raw-target-strip { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .raw-target-strip > div:nth-child(2) { border-right: 0; }
  .raw-target-strip > div:nth-child(-n+2) { border-bottom: 1px solid var(--rm-line); }
}

@media (prefers-reduced-motion: reduce) {
  .raw-control, .raw-mobile-menu { transition: none; }
  .raw-alert-viewport { scroll-behavior: auto; }
  .raw-silo-card, .raw-silo-vessel, .raw-silo-fill, .raw-silo-fill::before, .raw-silo-fill i, .raw-silo-shine { animation: none; }
}
</style>

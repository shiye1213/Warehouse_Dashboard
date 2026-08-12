<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import {
  AlertTriangle,
  ArrowRight,
  BadgeCheck,
  Boxes,
  CalendarClock,
  ChartNoAxesColumnIncreasing,
  CheckCircle2,
  CircleDollarSign,
  Clock3,
  Crosshair,
  Download,
  PackageOpen,
  RotateCcw,
  ShieldCheck,
  ShieldAlert,
  Sparkles,
  UserRoundCheck,
} from 'lucide-vue-next'
import PageState from '../components/PageState.vue'
import inventoryHealthWarehouse from '../assets/inventory-health-warehouse-hologram.png'
import inventoryHealthTitleIcon from '../assets/inventory-aging/inventory-health-title-icon.png'
import potentialValueIcon from '../assets/inventory-aging/potential-value-icon.png'
import { registerProjectRefresh } from '../composables/useProjectRefresh'
import { dashboardApi } from '../services/api'

const snapshot = ref(null)
const loading = ref(false)
const error = ref('')
const warehouseFilter = ref('全部仓库')
const categoryFilter = ref('全部物料')
const riskFilter = ref('全部风险')
const selectedBucket = ref('')
const simulatedClockSeconds = ref(8 * 60)

const simulatedTimestamp = computed(() => {
  const secondsInHour = simulatedClockSeconds.value % 3600
  const minutes = Math.floor(secondsInHour / 60)
  const seconds = secondsInHour % 60
  return `2026-07-31 11:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
})

let simulatedClockTimer = 0

const bucketOrder = ['0-30天', '31-60天', '61-90天', '91-180天', '181-365天', '365天以上']
const bucketColors = ['#3ca7ff', '#54d9d0', '#8bd45f', '#f3c44f', '#f28a32', '#f05252']
const warehouseRiskBuckets = [
  { label: '0-30天', color: '#b8ca48', matches: ['0-30天'] },
  { label: '31-90天', color: '#79b651', matches: ['31-60天', '61-90天'] },
  { label: '91-180天', color: '#2f91e7', matches: ['91-180天'] },
  { label: '181-365天', color: '#f39b2e', matches: ['181-365天'] },
  { label: '365天以上', color: '#f35b50', matches: ['365天以上'] },
]
const levelOrder = ['关注', '预警', '呆滞', '严重呆滞']
const heatImpactLabels = ['极高', '高', '中', '低', '很低']
const heatFrequencyLabels = ['很低', '低', '中', '高', '极高']
const heatLegend = [
  { label: '高风险 (9-25)', tone: 'critical' },
  { label: '中高风险 (6-8)', tone: 'high' },
  { label: '中风险 (3-5)', tone: 'medium' },
  { label: '低风险 (1-2)', tone: 'low' },
]
async function load() {
  loading.value = true
  error.value = ''
  try {
    snapshot.value = await dashboardApi.getInventoryAging()
  } catch (cause) {
    error.value = cause.message || '库龄数据加载失败'
  } finally {
    loading.value = false
  }
}

registerProjectRefresh('inventory-aging-dashboard', load)
onMounted(() => {
  load()
  simulatedClockTimer = window.setInterval(() => {
    simulatedClockSeconds.value = (simulatedClockSeconds.value + 1) % 3600
  }, 1000)
})
onUnmounted(() => window.clearInterval(simulatedClockTimer))

const batches = computed(() => snapshot.value?.batches || [])
const skus = computed(() => snapshot.value?.skus || [])
const warehouses = computed(() => ['全部仓库', ...new Set(batches.value.map((item) => item.warehouseName))])
const categories = computed(() => ['全部物料', ...new Set(skus.value.map((item) => item.materialCategory))])

function matchesRisk(item) {
  if (riskFilter.value === '严重呆滞') return item.stagnantLevel === '严重呆滞'
  if (riskFilter.value === '呆滞') return item.isStagnant
  if (riskFilter.value === '预警及以上') return ['预警', '呆滞', '严重呆滞'].includes(item.stagnantLevel)
  return true
}

function matchesBase(item) {
  const warehouseMatched = warehouseFilter.value === '全部仓库' || item.warehouseName === warehouseFilter.value
  const categoryMatched = categoryFilter.value === '全部物料' || item.materialCategory === categoryFilter.value
  return warehouseMatched && categoryMatched && matchesRisk(item)
}

const filteredBatches = computed(() => batches.value.filter(matchesBase))
const filteredSkus = computed(() => skus.value.filter(matchesBase))
const totalAmount = computed(() => filteredBatches.value.reduce((sum, item) => sum + Number(item.inventoryAmount || 0), 0))
const stagnantBatches = computed(() => filteredBatches.value.filter((item) => item.isStagnant))
const stagnantSkus = computed(() => filteredSkus.value.filter((item) => item.isStagnant))
const stagnantAmount = computed(() => stagnantBatches.value.reduce((sum, item) => sum + Number(item.inventoryAmount || 0), 0))
const stagnantRatio = computed(() => totalAmount.value ? stagnantAmount.value / totalAmount.value : 0)
const over365Count = computed(() => filteredBatches.value.filter((item) => Number(item.ageDays || 0) > 365).length)
const severeSkuCount = computed(() => filteredSkus.value.filter((item) => item.stagnantLevel === '严重呆滞').length)
const noMovementCount = computed(() => filteredBatches.value.filter((item) => Number(item.daysSinceLastOutbound || 0) > 90).length)

const distribution = computed(() => bucketOrder.map((name, index) => {
  const rows = filteredBatches.value.filter((item) => item.ageBucket === name)
  const amount = rows.reduce((sum, item) => sum + Number(item.inventoryAmount || 0), 0)
  return {
    name,
    color: bucketColors[index],
    amount,
    batchCount: rows.length,
    ratio: totalAmount.value ? amount / totalAmount.value : 0,
  }
}))

const donutStyle = computed(() => {
  let cursor = 0
  const stops = distribution.value.map((item) => {
    const start = cursor
    cursor += item.ratio * 100
    return `${item.color} ${start.toFixed(2)}% ${cursor.toFixed(2)}%`
  })
  return { background: `conic-gradient(${stops.join(', ') || '#18384d 0 100%'})` }
})

const healthScore = computed(() => {
  const amountPenalty = Math.min(30, stagnantRatio.value * 260)
  const severePenalty = filteredSkus.value.length ? severeSkuCount.value / filteredSkus.value.length * 18 : 0
  const noMovePenalty = filteredBatches.value.length ? noMovementCount.value / filteredBatches.value.length * 10 : 0
  return Math.max(0, Math.min(100, Math.round(100 - amountPenalty - severePenalty - noMovePenalty)))
})
const healthLabel = computed(() => healthScore.value >= 85 ? '稳健' : healthScore.value >= 75 ? '可控' : healthScore.value >= 65 ? '关注' : '承压')
const amountHealth = computed(() => Math.max(0, Math.round(100 - stagnantRatio.value * 380)))
const cycleHealth = computed(() => Math.max(0, Math.round(100 - noMovementCount.value / Math.max(1, filteredBatches.value.length) * 80)))
const structureHealth = computed(() => Math.max(0, Math.round(100 - over365Count.value / Math.max(1, filteredBatches.value.length) * 110)))

const warehouseComparison = computed(() => {
  const names = [...new Set(filteredBatches.value.map((item) => item.warehouseName))]
  const result = names.map((name, index) => {
    const rows = filteredBatches.value.filter((item) => item.warehouseName === name)
    const total = rows.reduce((sum, item) => sum + Number(item.inventoryAmount || 0), 0)
    const stagnant = rows.filter((item) => item.isStagnant).reduce((sum, item) => sum + Number(item.inventoryAmount || 0), 0)
    return {
      name,
      code: warehouseCode(name, index),
      total,
      stagnant,
      ratio: total ? stagnant / total : 0,
      segments: warehouseRiskBuckets.map((bucket) => ({
        ...bucket,
        amount: rows.filter((item) => bucket.matches.includes(item.ageBucket)).reduce((sum, item) => sum + Number(item.inventoryAmount || 0), 0),
      })),
    }
  }).sort((a, b) => b.stagnant - a.stagnant)
  const maxTotal = Math.max(1, ...result.map((item) => item.total))
  return result.map((item) => ({ ...item, scale: item.total / maxTotal }))
})

function warehouseCode(name, index) {
  const text = String(name || '').toUpperCase()
  const latin = text.match(/[A-Z]{2,3}/)?.[0]
  if (latin) return latin
  if (text.includes('原料')) return 'RM'
  if (text.includes('成品')) return 'FG'
  if (text.includes('包装') || text.includes('包材')) return 'PK'
  if (text.includes('五金')) return 'WH'
  if (text.includes('备品') || text.includes('辅料')) return 'SP'
  return `W${index + 1}`
}

function ageBucketFor(value) {
  const days = Number(value || 0)
  if (days <= 30) return '0-30天'
  if (days <= 60) return '31-60天'
  if (days <= 90) return '61-90天'
  if (days <= 180) return '91-180天'
  if (days <= 365) return '181-365天'
  return '365天以上'
}

const riskRows = computed(() => filteredSkus.value
  .filter((item) => item.isStagnant || item.stagnantLevel === '预警')
  .filter((item) => !selectedBucket.value || ageBucketFor(item.maxAgeDays) === selectedBucket.value)
  .sort((a, b) => Number(b.stagnantScore || 0) - Number(a.stagnantScore || 0) || Number(b.maxAgeDays || 0) - Number(a.maxAgeDays || 0)))

const riskTickerRows = computed(() => {
  const rows = riskRows.value.slice(0, 12)
  const tickerRows = rows.length > 6 ? [...rows, ...rows] : rows
  return tickerRows.map((item, index) => ({
    ...item,
    tickerKey: `${item.warehouseId}-${item.sku}-${index}`,
  }))
})

const riskTickerStyle = computed(() => {
  const rowCount = Math.min(riskRows.value.length, 12)
  return {
    '--risk-row-count': rowCount,
    '--risk-row-height': '42px',
    '--risk-scroll-duration': `${Math.max(18, rowCount * 2.4)}s`,
  }
})

const heatCells = computed(() => {
  const amounts = filteredSkus.value.map((item) => Number(item.stagnantInventoryAmount || 0)).sort((a, b) => a - b)
  const maxAmount = Math.max(1, amounts.at(-1) || 0)
  const cells = Array.from({ length: 25 }, (_, index) => ({
    impact: 4 - Math.floor(index / 5),
    frequency: index % 5,
    count: 0,
  }))
  filteredSkus.value.forEach((item) => {
    const amount = Number(item.stagnantInventoryAmount || 0)
    const score = Math.max(0, Math.min(100, Number(item.stagnantScore || 0)))
    const impact = Math.max(0, Math.min(4, Math.ceil(amount / maxAmount * 5) - 1))
    const frequency = Math.max(0, Math.min(4, Math.floor(score / 20)))
    const cell = cells.find((entry) => entry.impact === impact && entry.frequency === frequency)
    if (cell) cell.count += 1
  })
  const max = Math.max(1, ...cells.map((item) => item.count))
  return cells.map((item) => ({
    ...item,
    score: (item.impact + 1) * (item.frequency + 1),
    tone: heatTone((item.impact + 1) * (item.frequency + 1)),
    isPeak: item.count > 0 && item.count === max,
  }))
})

function heatTone(score) {
  if (score >= 9) return 'critical'
  if (score >= 6) return 'high'
  if (score >= 3) return 'medium'
  return 'low'
}

const ownerRows = computed(() => {
  const groups = new Map()
  stagnantSkus.value.forEach((item) => {
    const owner = item.owner || '待分配'
    const current = groups.get(owner) || { owner, tasks: 0, p1: 0, p2: 0, severe: 0, amount: 0 }
    current.tasks += 1
    current.p1 += item.priority === 'P1-高' ? 1 : 0
    current.p2 += item.priority === 'P2-中' ? 1 : 0
    current.severe += item.stagnantLevel === '严重呆滞' ? 1 : 0
    current.amount += Number(item.stagnantInventoryAmount || 0)
    groups.set(owner, current)
  })
  return [...groups.values()].sort((a, b) => b.p1 - a.p1 || b.tasks - a.tasks)
})

const ownerTickerRows = computed(() => {
  const rows = ownerRows.value
  const tickerRows = rows.length > 3 ? [...rows, ...rows] : rows
  return tickerRows.map((item, index) => ({
    ...item,
    tickerKey: `${item.owner}-${index}`,
  }))
})

const ownerTickerStyle = computed(() => ({
  '--owner-row-count': ownerRows.value.length,
  '--owner-row-height': '36px',
  '--owner-scroll-duration': `${Math.max(20, ownerRows.value.length * 3)}s`,
}))

const p1Count = computed(() => stagnantSkus.value.filter((item) => item.priority === 'P1-高').length)
const p2Count = computed(() => stagnantSkus.value.filter((item) => item.priority === 'P2-中').length)
const ownerCoverage = computed(() => stagnantSkus.value.length ? stagnantSkus.value.filter((item) => item.owner).length / stagnantSkus.value.length : 0)
const actionCoverage = computed(() => stagnantSkus.value.length ? stagnantSkus.value.filter((item) => item.recommendedAction).length / stagnantSkus.value.length : 0)
const readiness = computed(() => Math.round((ownerCoverage.value + actionCoverage.value) * 50))
const p1Amount = computed(() => stagnantSkus.value.filter((item) => item.priority === 'P1-高').reduce((sum, item) => sum + Number(item.stagnantInventoryAmount || 0), 0))

const kpis = computed(() => [
  { label: '库存总金额', value: moneyWan(totalAmount.value), unit: '万元', note: `${filteredBatches.value.length} 个库存批次`, icon: CircleDollarSign, tone: 'blue' },
  { label: '呆滞库存金额', value: moneyWan(stagnantAmount.value), unit: '万元', note: '按批次金额追溯', icon: AlertTriangle, tone: 'amber' },
  { label: '呆滞金额占比', value: percent(stagnantRatio.value), unit: '', note: '风险资金占用', icon: ChartNoAxesColumnIncreasing, tone: 'rose' },
  { label: '呆滞批次数', value: stagnantBatches.value.length, unit: '批', note: '>180天且长期未出库', icon: CalendarClock, tone: 'blue' },
  { label: '365天以上批次', value: over365Count.value, unit: '批', note: '最高库龄风险层', icon: Clock3, tone: 'rose' },
  { label: '呆滞 SKU 数', value: stagnantSkus.value.length, unit: '个', note: `${severeSkuCount.value} 个严重呆滞`, icon: Boxes, tone: 'cyan' },
])

const warningCards = computed(() => [
  { label: '严重呆滞 SKU', value: severeSkuCount.value, note: '建议专项处置', icon: ShieldAlert, tone: 'danger' },
  { label: '超过180天批次', value: filteredBatches.value.filter((item) => Number(item.ageDays || 0) > 180).length, note: '进入呆滞判定区', icon: Clock3, tone: 'warning' },
  { label: '长期未出库批次', value: noMovementCount.value, note: '距最近出库 > 90天', icon: PackageOpen, tone: 'warning' },
  { label: '已明确责任人', value: `${Math.round(ownerCoverage.value * 100)}%`, note: '处置责任已覆盖', icon: UserRoundCheck, tone: 'good' },
])

function moneyWan(value) { return (Number(value || 0) / 10000).toFixed(value >= 1000000 ? 1 : 2) }
function compactMoney(value) {
  const number = Number(value || 0)
  if (number >= 1000000) return `¥${(number / 1000000).toFixed(2)}M`
  if (number >= 10000) return `¥${(number / 10000).toFixed(1)}万`
  return `¥${Math.round(number).toLocaleString('zh-CN')}`
}
function percent(value, digits = 1) { return `${(Number(value || 0) * 100).toFixed(digits)}%` }
function formatDate(value) { return value ? String(value).replaceAll('-', '.') : '—' }
function scoreTone(value) { return value >= 80 ? 'good' : value >= 65 ? 'warning' : 'danger' }
function levelClass(value) { return value === '严重呆滞' ? 'severe' : value === '呆滞' ? 'stagnant' : value === '预警' ? 'warning' : 'normal' }

function selectBucket(name) {
  selectedBucket.value = selectedBucket.value === name ? '' : name
  document.getElementById('aging-risk')?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

function openNavigation() {
  window.dispatchEvent(new CustomEvent('warehouse:open-navigation'))
}

function resetFilters() {
  warehouseFilter.value = '全部仓库'
  categoryFilter.value = '全部物料'
  riskFilter.value = '全部风险'
  selectedBucket.value = ''
}

function focusRisk(level) {
  riskFilter.value = level
  document.getElementById('aging-risk')?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

function exportRiskList() {
  const headers = ['仓库', '项目号', '物料编码', '物料名称', '最大库龄天数', '呆滞等级', '优先级', '责任人', '建议处置措施']
  const rows = riskRows.value.map((item) => [item.warehouseName, item.projectNo, item.materialCode, item.materialName, item.maxAgeDays, item.stagnantLevel, item.priority, item.owner, item.recommendedAction])
  const csv = [headers, ...rows].map((row) => row.map((cell) => `"${String(cell ?? '').replaceAll('"', '""')}"`).join(',')).join('\r\n')
  const blob = new Blob([`\ufeff${csv}`], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `呆滞物料风险清单_${snapshot.value?.meta?.snapshotDate || 'latest'}.csv`
  anchor.click()
  URL.revokeObjectURL(url)
}
</script>

<template>
  <div class="aging-viewport">
    <div class="aging-page">
      <PageState :loading="loading && !snapshot" :error="error" @retry="load">
      <header class="aging-hero">
        <div class="aging-title-block">
          <button type="button" class="aging-emblem" aria-label="打开系统导航" title="打开系统导航" @click="openNavigation">
            <img :src="inventoryHealthTitleIcon" alt="" />
          </button>
          <div><h2>库存健康与呆滞管理</h2></div>
        </div>
        <div class="aging-live"><small>数据更新时间<br><b>{{ simulatedTimestamp }}</b></small><span><i /> 模拟实时</span></div>
      </header>

      <div class="aging-toolbar" aria-label="看板导航与筛选">
        <div class="aging-filters">
          <label><span>仓库</span><select v-model="warehouseFilter"><option v-for="item in warehouses" :key="item">{{ item }}</option></select></label>
          <label><span>物料类型</span><select v-model="categoryFilter"><option v-for="item in categories" :key="item">{{ item }}</option></select></label>
          <label><span>风险等级</span><select v-model="riskFilter"><option>全部风险</option><option>预警及以上</option><option>呆滞</option><option>严重呆滞</option></select></label>
          <label class="aging-date"><CalendarClock :size="13" /><b>{{ formatDate(snapshot?.meta?.snapshotDate) }}</b></label>
          <button class="aging-reset" title="重置筛选" aria-label="重置筛选" @click="resetFilters"><RotateCcw :size="15" /></button>
        </div>
      </div>

      <main id="aging-overview" class="aging-board">
        <section class="aging-kpis" aria-label="关键健康指标">
          <article v-for="(item, index) in kpis" :key="item.label" class="aging-kpi" :class="`tone-${item.tone}`" :style="{ '--delay': `${index * 55}ms` }">
            <div class="aging-kpi-icon"><component :is="item.icon" :size="25" /></div>
            <div><span>{{ item.label }}</span><strong>{{ item.value }} <em>{{ item.unit }}</em></strong><small>{{ item.note }}</small></div>
          </article>
        </section>

        <section class="aging-primary-grid">
          <div class="aging-column left-column">
            <article class="aging-panel distribution-panel">
              <header class="panel-heading"><span class="panel-number">库龄结构分析</span><button v-if="selectedBucket" @click="selectedBucket = ''">清除 {{ selectedBucket }}</button></header>
              <div class="distribution-body">
                <button class="aging-donut" :style="donutStyle" aria-label="库存金额库龄分布" @click="selectedBucket = ''"><span><small>库存总金额</small><strong>¥{{ moneyWan(totalAmount) }}万</strong><em>{{ filteredBatches.length }} 批</em></span></button>
                <div class="bucket-list">
                  <button v-for="item in distribution" :key="item.name" :class="{ active: selectedBucket === item.name }" @click="selectBucket(item.name)">
                    <i :style="{ background: item.color, boxShadow: `0 0 8px ${item.color}` }" /><span>{{ item.name }}<small>{{ item.batchCount }} 批</small></span><div><b>{{ compactMoney(item.amount) }}</b><em>{{ percent(item.ratio) }}</em></div>
                    <u><i :style="{ width: `${Math.max(2, item.ratio * 100)}%`, background: item.color }" /></u>
                  </button>
                </div>
              </div>
              <footer><span><CheckCircle2 :size="14" /> 健康库存占比 <b>{{ percent(1 - stagnantRatio) }}</b></span><span><AlertTriangle :size="14" /> 风险库存占比 <b>{{ percent(stagnantRatio) }}</b></span></footer>
            </article>

            <article id="aging-risk" class="aging-panel risk-list-panel">
              <header class="panel-heading"><span class="panel-number">高风险物料清单</span><button @click="exportRiskList"><Download :size="13" /> 导出</button></header>
              <div class="risk-table-wrap" role="table" aria-label="高风险物料动态清单">
                <div class="risk-table-head" role="row"><span role="columnheader">物料 / 项目</span><span role="columnheader">仓库</span><span role="columnheader">最大库龄</span><span role="columnheader">等级</span><span role="columnheader">责任人</span></div>
                <div class="risk-scroll-viewport" role="rowgroup">
                  <div class="risk-scroll-track" :class="{ 'is-scrolling': riskRows.length > 6 }" :style="riskTickerStyle">
                    <div v-for="item in riskTickerRows" :key="item.tickerKey" class="risk-scroll-row" role="row">
                      <span role="cell"><strong>{{ item.materialName }}</strong><small>{{ item.projectNo }} · {{ item.materialCode }}</small></span>
                      <span role="cell">{{ item.warehouseName }}</span>
                      <span role="cell"><b>{{ item.maxAgeDays }}</b> 天</span>
                      <span role="cell"><i class="risk-level" :class="levelClass(item.stagnantLevel)">{{ item.stagnantLevel }}</i></span>
                      <span role="cell">{{ item.owner }}</span>
                    </div>
                  </div>
                </div>
                <div v-if="!riskRows.length" class="aging-empty"><BadgeCheck :size="18" /> 当前筛选范围内暂无高风险物料</div>
              </div>
            </article>
          </div>

          <div class="aging-column center-column">
            <article class="aging-panel health-core-panel">
              <div class="health-label top"><i class="health-glyph"><ShieldCheck :size="21" /></i><span class="health-caption">库存健康指数</span><div class="health-score-line"><strong>{{ healthScore }}</strong><em>分 · {{ healthLabel }}</em></div></div>
              <div class="health-stage">
                <div class="planetary-field" aria-hidden="true"><i /><i /><i /><i /><span /><span /><span /></div>
                <div class="health-field-lines" aria-hidden="true"><i /><i /><i /><i /></div>
                <div class="orbit orbit-a" /><div class="orbit orbit-b" /><div class="orbit orbit-c" />
                <div class="health-cylinder" aria-hidden="true"><i /><span /></div>
                <div class="health-link link-left" aria-hidden="true" /><div class="health-link link-right" aria-hidden="true" /><div class="health-link link-bottom" aria-hidden="true" />
                <div class="warehouse-core">
                  <img :src="inventoryHealthWarehouse" width="1254" height="1254" decoding="async" alt="透明全息立体仓储货架与库存托盘" />
                  <span class="warehouse-scan" aria-hidden="true" />
                </div>
                <div class="health-satellite satellite-left" :class="scoreTone(amountHealth)"><i class="satellite-glyph"><CircleDollarSign :size="17" /></i><small>金额健康度</small><strong>{{ amountHealth }}</strong><em>分</em></div>
                <div class="health-satellite satellite-right" :class="scoreTone(cycleHealth)"><i class="satellite-glyph"><RotateCcw :size="17" /></i><small>周转健康度</small><strong>{{ cycleHealth }}</strong><em>分</em></div>
                <div class="health-satellite satellite-bottom" :class="scoreTone(structureHealth)"><i class="satellite-glyph"><Boxes :size="17" /></i><small>结构健康度</small><strong>{{ structureHealth }}</strong><em>分</em></div>
              </div>
              <div class="health-rule"><Sparkles :size="15" /><span>呆滞判定</span><strong>库龄 &gt; 180 天 且 最近 90 天无出库</strong></div>
            </article>

            <article class="aging-panel warning-panel">
              <header class="panel-heading"><span class="panel-number">预警与异常总览</span><button @click="focusRisk('预警及以上')">查看清单 <ArrowRight :size="13" /></button></header>
              <div class="warning-grid">
                <div v-for="item in warningCards" :key="item.label" :class="`warning-${item.tone}`"><component :is="item.icon" :size="21" /><span><small>{{ item.label }}</small><strong>{{ item.value }}</strong><em>{{ item.note }}</em></span></div>
              </div>
            </article>

          </div>

          <div class="aging-column right-column">
            <article class="aging-panel warehouse-panel">
              <header class="panel-heading"><span class="panel-number">仓库风险对比</span><span class="unit-note">查看全部 ›</span></header>
              <div class="warehouse-bars">
                <header class="warehouse-table-head"><span>金额(万元)</span><b>呆滞金额</b><b>呆滞占比</b></header>
                <div v-for="item in warehouseComparison" :key="item.name" class="warehouse-risk-row">
                  <div class="warehouse-identity"><strong>{{ item.code }}</strong><span>{{ item.name }}</span></div>
                  <div class="warehouse-track" :aria-label="`${item.name}库龄金额分布`">
                    <i v-for="segment in item.segments" :key="segment.label" :title="`${segment.label} ${compactMoney(segment.amount)}`" :style="{ width: `${item.total ? segment.amount / item.total * item.scale * 100 : 0}%`, background: segment.color }" />
                  </div>
                  <b class="warehouse-stagnant">{{ moneyWan(item.stagnant) }}</b>
                  <em>{{ percent(item.ratio) }}</em>
                </div>
                <footer class="warehouse-legend"><span v-for="bucket in warehouseRiskBuckets" :key="bucket.label"><i :style="{ background: bucket.color }" />{{ bucket.label }}</span></footer>
              </div>
            </article>

            <article class="aging-panel heat-panel">
              <header class="panel-heading"><span class="panel-number">风险热力矩阵</span></header>
              <div class="risk-heat-layout">
                <div class="risk-heat-y-title">影响程度</div>
                <div class="risk-heat-y-labels"><span v-for="label in heatImpactLabels" :key="label">{{ label }}</span></div>
                <div class="risk-heat-grid">
                  <div v-for="cell in heatCells" :key="`${cell.impact}-${cell.frequency}`" :class="[`risk-cell-${cell.tone}`, { 'is-peak': cell.isPeak }]">
                    <strong v-if="cell.count">{{ cell.count }}</strong>
                  </div>
                </div>
                <aside class="risk-heat-legend"><span v-for="item in heatLegend" :key="item.label"><i :class="`risk-cell-${item.tone}`" />{{ item.label }}</span></aside>
                <div class="risk-heat-x-labels"><span v-for="label in heatFrequencyLabels" :key="label">{{ label }}</span></div>
                <div class="risk-heat-x-title">发生频率 →</div>
              </div>
            </article>

            <article id="aging-owners" class="aging-panel owner-panel">
              <header class="panel-heading"><span class="panel-number">责任人任务跟进</span><span class="source-note">处置台账</span></header>
              <div class="owner-table">
                <div class="owner-head"><span>责任人</span><span>待处置</span><span>P1 高</span><span>严重</span><span>涉及金额</span></div>
                <div class="owner-scroll-viewport">
                  <div class="owner-scroll-track" :class="{ 'is-scrolling': ownerRows.length > 3 }" :style="ownerTickerStyle">
                    <div v-for="item in ownerTickerRows" :key="item.tickerKey" class="owner-row"><strong><UserRoundCheck :size="16" /> {{ item.owner }}</strong><span>{{ item.tasks }}</span><b>{{ item.p1 }}</b><em>{{ item.severe }}</em><small>{{ compactMoney(item.amount) }}</small></div>
                  </div>
                </div>
              </div>
            </article>
          </div>
        </section>

        <section class="aging-secondary-grid">
          <article class="aging-panel readiness-panel">
            <header class="panel-heading compact"><span class="panel-number">处置准备度</span></header>
            <div class="readiness-body"><div class="readiness-ring" :style="{ '--progress': `${readiness * 3.6}deg` }"><span><strong>{{ readiness }}%</strong><small>已建档</small></span></div><div class="readiness-list"><p><span>责任人覆盖</span><b>{{ percent(ownerCoverage, 0) }}</b></p><i><em :style="{ width: percent(ownerCoverage, 0) }" /></i><p><span>处置建议覆盖</span><b>{{ percent(actionCoverage, 0) }}</b></p><i><em :style="{ width: percent(actionCoverage, 0) }" /></i><small>完成率需接入“处置跟踪”状态后计算</small></div></div>
          </article>

          <article class="aging-panel action-score-panel">
            <header class="panel-heading compact"><span class="panel-number">处置优先级</span></header>
            <div class="priority-score"><div><i class="tech-symbol priority-symbol"><Crosshair :size="31" /></i><strong>{{ p1Count }}</strong><span>P1 高优先级</span></div><ul><li><span>P1 高</span><b>{{ p1Count }} 项</b></li><li><span>P2 中</span><b>{{ p2Count }} 项</b></li><li><span>责任人组</span><b>{{ ownerRows.length }} 组</b></li></ul></div>
          </article>

          <article class="aging-panel value-panel">
            <header class="panel-heading compact"><span class="panel-number">潜在价值挖掘</span></header>
            <div class="value-body"><div class="value-gem"><img :src="potentialValueIcon" alt="" /></div><dl><div><dt>可优先去化金额</dt><dd>{{ compactMoney(p1Amount) }}</dd></div><div><dt>全部呆滞金额</dt><dd>{{ compactMoney(stagnantAmount) }}</dd></div><div><dt>可推动处置 SKU</dt><dd>{{ stagnantSkus.length }} 个</dd></div></dl></div>
          </article>

        </section>

      </main>
      </PageState>
    </div>
  </div>
</template>

<style scoped>
.aging-page {
  --aging-blue: #2ca7ff;
  --aging-cyan: #46e2f1;
  --aging-line: rgba(53, 153, 228, .34);
  --aging-panel: rgba(4, 25, 49, .9);
  min-height: calc(100vh - 78px);
  overflow: hidden;
  color: #d9edff;
  background:
    radial-gradient(circle at 51% 29%, rgba(21, 104, 205, .19), transparent 28%),
    linear-gradient(180deg, #020c1d 0%, #04142b 44%, #020a18 100%);
  font-family: "Microsoft YaHei UI", "Microsoft YaHei", "Segoe UI", sans-serif;
}
.aging-viewport {
  display: grid;
  width: 100vw;
  height: 100dvh;
  place-items: center;
  overflow: hidden;
  background: #020817;
}
.aging-page::before { position: fixed; inset: 78px 0 0 var(--sidebar-width); z-index: 0; content: ""; pointer-events: none; opacity: .34; background-image: linear-gradient(rgba(45,130,206,.055) 1px, transparent 1px), linear-gradient(90deg, rgba(45,130,206,.045) 1px, transparent 1px); background-size: 34px 34px; mask-image: linear-gradient(#000, transparent 94%); }
.is-collapsed .aging-page::before { left: var(--sidebar-collapsed); }
.aging-hero { position: relative; z-index: 1; display: flex; min-height: 92px; align-items: center; justify-content: space-between; gap: 20px; padding: 14px 28px 12px; border-bottom: 1px solid rgba(48,139,221,.25); background: linear-gradient(90deg, rgba(5,35,70,.84), rgba(4,19,40,.72), rgba(5,35,70,.78)); }
.aging-hero::after { position: absolute; right: 25%; bottom: -1px; left: 25%; height: 2px; content: ""; background: linear-gradient(90deg, transparent, #34aaff, transparent); box-shadow: 0 0 12px #2495ff; }
.aging-title-block { display: flex; align-items: center; gap: 18px; }
.aging-emblem { position: relative; display: grid; width: 58px; height: 58px; padding: 0; cursor: pointer; place-items: center; border: 1px solid rgba(76,190,255,.65); color: #7bd9ff; background: linear-gradient(145deg, rgba(29,142,255,.28), rgba(2,26,63,.2)); box-shadow: inset 0 0 22px rgba(46,169,255,.25), 0 0 18px rgba(37,144,255,.18); clip-path: polygon(50% 0, 94% 24%, 94% 76%, 50% 100%, 6% 76%, 6% 24%); transition: filter .18s ease, transform .18s ease; }
.aging-emblem:hover { filter: brightness(1.28); transform: scale(1.04); }
.aging-emblem:focus-visible { outline: 2px solid #8bdeff; outline-offset: 3px; }
.aging-title-block p { margin: 0 0 4px; color: #5da9e8; font: 600 8px/1 "Bahnschrift", sans-serif; letter-spacing: .34em; }
.aging-title-block h2 { margin: 0; color: #e7f4ff; font-size: clamp(24px, 2vw, 34px); font-weight: 720; letter-spacing: .12em; text-shadow: 0 0 14px rgba(102,187,255,.3); }
.aging-title-block span { display: block; margin-top: 5px; color: #698cae; font-size: 9px; letter-spacing: .1em; }
.aging-live { display: flex; flex-direction: column; align-items: flex-end; gap: 7px; }
.aging-live span { display: inline-flex; align-items: center; gap: 7px; padding: 6px 10px; border: 1px solid rgba(58,184,255,.25); border-radius: 16px; color: #9bdcff; font-size: 9px; background: rgba(14,83,145,.15); }
.aging-live i { width: 6px; height: 6px; border-radius: 50%; background: #62e2aa; box-shadow: 0 0 9px #62e2aa; animation: aging-live 2s ease-in-out infinite; }
.aging-live small { color: #587898; font-size: 8px; }
.aging-toolbar { position: sticky; top: 78px; z-index: 32; display: flex; min-height: 52px; align-items: center; justify-content: space-between; gap: 16px; padding: 7px 28px; border-bottom: 1px solid rgba(49,137,211,.26); background: rgba(2,14,32,.93); backdrop-filter: blur(18px); }
.aging-tabs { display: flex; height: 36px; align-items: stretch; }
.aging-tabs button { min-width: 96px; padding: 0 18px; cursor: pointer; border: 1px solid rgba(40,124,203,.22); color: #6e91b2; font-size: 10px; background: linear-gradient(180deg, rgba(12,46,81,.5), rgba(5,24,48,.5)); transform: skewX(-15deg); }
.aging-tabs button + button { margin-left: -1px; }
.aging-tabs button.active { z-index: 1; border-color: rgba(66,170,255,.65); color: #bfe8ff; background: linear-gradient(180deg, rgba(24,118,213,.5), rgba(11,56,108,.55)); box-shadow: inset 0 -2px 10px rgba(55,166,255,.25), 0 0 12px rgba(37,136,255,.12); }
.aging-filters { display: flex; align-items: center; gap: 7px; }
.aging-filters label { display: flex; height: 32px; align-items: center; gap: 6px; padding: 0 8px; border: 1px solid rgba(59,131,195,.25); border-radius: 5px; color: #5f81a0; background: rgba(8,30,57,.72); }
.aging-filters label span { font-size: 8px; white-space: nowrap; }
.aging-filters select { min-width: 86px; cursor: pointer; border: 0; outline: 0; color: #b6d6ef; font-size: 9px; background: transparent; }
.aging-filters option { color: #d8edff; background: #08203d; }
.aging-reset { display: grid; width: 32px; height: 32px; cursor: pointer; place-items: center; border: 1px solid rgba(60,142,212,.28); border-radius: 5px; color: #6da5d2; background: rgba(8,35,66,.6); }
.aging-board { position: relative; z-index: 1; width: min(1840px, 100%); margin: 0 auto; padding: 12px 16px 24px; }
.aging-kpis { display: grid; grid-template-columns: repeat(6, minmax(150px, 1fr)); gap: 9px; }
.aging-kpi { --tone: #3ca7ff; position: relative; display: flex; min-height: 86px; align-items: center; gap: 12px; padding: 12px 14px; overflow: hidden; border: 1px solid color-mix(in srgb, var(--tone) 46%, transparent); background: linear-gradient(135deg, color-mix(in srgb, var(--tone) 9%, rgba(3,25,51,.94)), rgba(3,17,36,.94)); box-shadow: inset 0 0 22px color-mix(in srgb, var(--tone) 6%, transparent); animation: aging-enter .55s var(--delay) both; clip-path: polygon(8px 0, calc(100% - 8px) 0, 100% 8px, 100% calc(100% - 8px), calc(100% - 8px) 100%, 8px 100%, 0 calc(100% - 8px), 0 8px); }
.aging-kpi::after { position: absolute; right: -22px; bottom: -38px; width: 90px; height: 72px; border: 1px solid color-mix(in srgb, var(--tone) 28%, transparent); border-radius: 50%; content: ""; }
.aging-kpi.tone-amber { --tone: #f2aa32; }.aging-kpi.tone-rose { --tone: #ef5555; }.aging-kpi.tone-cyan { --tone: #43d7eb; }
.aging-kpi-icon { display: grid; width: 47px; height: 47px; flex: 0 0 47px; place-items: center; border: 1px solid color-mix(in srgb, var(--tone) 48%, transparent); border-radius: 50%; color: var(--tone); background: radial-gradient(circle, color-mix(in srgb, var(--tone) 18%, transparent), rgba(5,27,55,.76)); box-shadow: inset 0 0 15px color-mix(in srgb, var(--tone) 13%, transparent), 0 0 14px color-mix(in srgb, var(--tone) 14%, transparent); }
.aging-kpi > div:last-child { min-width: 0; }
.aging-kpi span { display: block; color: #83a6c5; font-size: 9px; }
.aging-kpi strong { display: block; margin-top: 4px; color: #edf8ff; font: 720 21px/1 "Bahnschrift", sans-serif; white-space: nowrap; }
.aging-kpi em { color: #91afc7; font-size: 9px; font-style: normal; font-weight: 500; }
.aging-kpi small { display: block; margin-top: 5px; overflow: hidden; color: color-mix(in srgb, var(--tone) 68%, #7293ae); font-size: 8px; text-overflow: ellipsis; white-space: nowrap; }
.aging-primary-grid { display: grid; grid-template-columns: minmax(290px, .88fr) minmax(430px, 1.38fr) minmax(310px, .96fr); gap: 10px; margin-top: 10px; }
.aging-column { display: flex; min-width: 0; flex-direction: column; gap: 10px; }
.aging-panel { position: relative; overflow: hidden; border: 1px solid var(--aging-line); background: linear-gradient(180deg, rgba(5,31,60,.94), rgba(3,19,40,.94)); box-shadow: inset 0 0 25px rgba(29,119,204,.045), 0 8px 26px rgba(0,0,0,.16); clip-path: polygon(8px 0, 100% 0, 100% calc(100% - 8px), calc(100% - 8px) 100%, 0 100%, 0 8px); }
.aging-panel::before { position: absolute; top: 0; right: 18%; left: 18%; height: 1px; content: ""; background: linear-gradient(90deg, transparent, rgba(69,177,255,.75), transparent); }
.panel-heading { display: flex; min-height: 42px; align-items: center; gap: 8px; padding: 7px 10px; border-bottom: 1px solid rgba(63,138,204,.18); background: linear-gradient(90deg, rgba(21,76,132,.22), transparent); }
.panel-number { color: #a9dcff; font: italic 720 21px/1 "Bahnschrift", sans-serif; text-shadow: 0 0 9px #279dff; }
.panel-heading h3 { margin: 0; color: #d9edff; font-size: 11px; font-weight: 680; letter-spacing: .04em; }
.panel-heading p { margin: 2px 0 0; color: #4f79a1; font: 600 6px/1 "Bahnschrift", sans-serif; letter-spacing: .18em; }
.panel-heading button { display: inline-flex; height: 25px; align-items: center; gap: 4px; margin-left: auto; padding: 0 8px; cursor: pointer; border: 1px solid rgba(65,157,229,.26); border-radius: 4px; color: #6eb9ed; font-size: 8px; background: rgba(18,77,128,.2); }
.unit-note, .source-note { margin-left: auto; color: #5f83a4; font-size: 7px; }
.distribution-panel { min-height: 326px; }
.distribution-body { display: grid; grid-template-columns: 140px 1fr; align-items: center; gap: 10px; padding: 14px 12px 10px; }
.aging-donut { position: relative; display: grid; width: 126px; height: 126px; cursor: pointer; place-items: center; border: 0; border-radius: 50%; box-shadow: 0 0 22px rgba(35,142,255,.18); }
.aging-donut::before { position: absolute; inset: 13px; border-radius: 50%; content: ""; background: radial-gradient(circle, #092348, #03152e 72%); box-shadow: inset 0 0 15px rgba(36,148,255,.25); }
.aging-donut span { position: relative; z-index: 1; text-align: center; }
.aging-donut small, .aging-donut em { display: block; color: #668aa8; font-size: 7px; font-style: normal; }
.aging-donut strong { display: block; margin: 5px 0; color: #d8f0ff; font: 700 15px/1 "Bahnschrift", sans-serif; }
.bucket-list { display: grid; gap: 2px; }
.bucket-list button { position: relative; display: grid; min-height: 35px; grid-template-columns: 8px 1fr auto; align-items: center; gap: 6px; padding: 4px 3px 6px; cursor: pointer; border: 0; border-radius: 4px; color: inherit; text-align: left; background: transparent; }
.bucket-list button:hover, .bucket-list button.active { background: rgba(50,131,207,.12); }
.bucket-list button > i { width: 6px; height: 6px; border-radius: 50%; }
.bucket-list button span { color: #9bbbd4; font-size: 8px; }.bucket-list button span small { display: inline; margin-left: 4px; color: #4e718f; font-size: 7px; }
.bucket-list button div { display: flex; align-items: center; gap: 6px; }.bucket-list button b { color: #c7e3f6; font: 600 8px "Bahnschrift", sans-serif; }.bucket-list button em { min-width: 32px; color: #7394ae; font-size: 7px; font-style: normal; text-align: right; }
.bucket-list u { position: absolute; right: 3px; bottom: 2px; left: 17px; height: 2px; overflow: hidden; text-decoration: none; background: rgba(76,117,151,.2); }.bucket-list u i { display: block; height: 100%; }
.distribution-panel > footer { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; padding: 0 10px 10px; }
.distribution-panel > footer span { display: flex; min-height: 30px; align-items: center; justify-content: center; gap: 5px; border: 1px solid rgba(53,159,238,.2); color: #68a8d5; font-size: 8px; background: rgba(20,77,125,.13); }.distribution-panel > footer span:last-child { color: #ed7972; border-color: rgba(222,78,72,.2); background: rgba(115,30,36,.1); }.distribution-panel > footer b { color: currentColor; font-size: 10px; }
.risk-list-panel { min-height: 398px; }
.risk-table-wrap { overflow: auto; }
.risk-table-wrap table { width: 100%; border-collapse: collapse; }
.risk-table-wrap th { height: 27px; color: #5e82a0; font-size: 7px; font-weight: 550; text-align: left; background: rgba(19,63,105,.18); }
.risk-table-wrap th, .risk-table-wrap td { padding: 5px 6px; border-bottom: 1px solid rgba(63,127,180,.11); }
.risk-table-wrap td { color: #8eacc4; font-size: 7px; }.risk-table-wrap td:first-child { max-width: 160px; }.risk-table-wrap td strong, .risk-table-wrap td small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.risk-table-wrap td strong { color: #c6dfef; font-size: 8px; }.risk-table-wrap td small { margin-top: 3px; color: #52738e; font-size: 6px; }.risk-table-wrap td b { color: #f1a142; font-size: 9px; }
.risk-level { display: inline-flex; min-width: 40px; justify-content: center; padding: 3px 5px; border-radius: 8px; font-size: 6px; }.risk-level.severe { color: #ff8178; background: rgba(215,51,50,.28); }.risk-level.stagnant { color: #ffb34d; background: rgba(208,111,24,.25); }.risk-level.warning { color: #f3d06a; background: rgba(183,143,35,.2); }.risk-level.normal { color: #62dca7; background: rgba(39,144,103,.2); }
.aging-empty { display: flex; min-height: 90px; align-items: center; justify-content: center; gap: 7px; color: #6ba987; font-size: 9px; }
.health-core-panel { min-height: 450px; background: radial-gradient(circle at 50% 51%, rgba(19,116,227,.23), transparent 39%), linear-gradient(180deg, rgba(4,27,57,.96), rgba(3,16,34,.97)); }
.health-core-panel::after { position: absolute; inset: 0; content: ""; pointer-events: none; opacity: .42; background: repeating-radial-gradient(ellipse at 50% 58%, transparent 0 34px, rgba(51,139,216,.1) 35px 36px, transparent 37px 55px); }
.health-label.top { position: absolute; z-index: 5; top: 18px; left: 50%; display: grid; width: 154px; height: 78px; place-items: center; border: 1px solid rgba(58,178,255,.58); border-radius: 50%; background: radial-gradient(ellipse, rgba(12,73,141,.8), rgba(4,29,64,.82)); box-shadow: inset 0 0 20px rgba(50,163,255,.24), 0 0 18px rgba(43,155,255,.22); transform: translateX(-50%); }
.health-label.top span { position: absolute; top: 10px; color: #9acff0; font-size: 8px; }.health-label.top strong { margin-top: 5px; color: #65ceff; font: 750 38px/1 "Bahnschrift", sans-serif; text-shadow: 0 0 13px #218dff; }.health-label.top em { position: absolute; bottom: 8px; color: #78d8a9; font-size: 7px; font-style: normal; }
.health-stage { position: relative; z-index: 2; height: 396px; margin-top: 42px; }
.orbit { position: absolute; top: 55%; left: 50%; border: 1px solid rgba(52,156,244,.45); border-radius: 50%; box-shadow: 0 0 15px rgba(40,143,238,.18), inset 0 0 12px rgba(40,143,238,.12); transform: translate(-50%, -50%) rotateX(67deg); animation: orbit-breathe 3.8s ease-in-out infinite; }.orbit-a { width: 420px; height: 280px; }.orbit-b { width: 330px; height: 220px; animation-delay: -.8s; }.orbit-c { width: 235px; height: 150px; animation-delay: -1.4s; }
.warehouse-core { position: absolute; z-index: 4; top: 49%; left: 50%; width: 154px; transform: translate(-50%, -50%); filter: drop-shadow(0 0 16px rgba(40,157,255,.52)); }
.core-roof { width: 128px; height: 36px; margin: 0 auto -8px; border: 1px solid #4ab6ff; background: linear-gradient(135deg, rgba(63,176,255,.32), rgba(5,45,94,.9)); clip-path: polygon(50% 0, 100% 70%, 84% 100%, 16% 100%, 0 70%); }
.core-box { position: relative; display: grid; height: 126px; grid-template-columns: repeat(4, 1fr); grid-template-rows: repeat(3, 1fr); gap: 5px; padding: 12px; border: 2px solid #4ab7ff; color: #72c7ff; background: linear-gradient(135deg, rgba(12,79,154,.88), rgba(4,34,77,.94)); box-shadow: inset 0 0 25px rgba(33,149,255,.28); }.core-box svg { position: absolute; z-index: 2; top: 50%; left: 50%; opacity: .6; transform: translate(-50%, -50%); }.core-box span { border: 1px solid rgba(83,189,255,.35); background: rgba(29,111,184,.35); }
.core-base { width: 180px; height: 38px; margin: -5px 0 0 -13px; border: 1px solid rgba(65,174,255,.65); border-radius: 50%; box-shadow: inset 0 0 14px rgba(42,156,255,.24), 0 0 13px rgba(42,156,255,.25); }
.health-satellite { position: absolute; z-index: 5; display: flex; width: 115px; height: 68px; align-items: center; justify-content: center; gap: 4px; border: 1px solid rgba(50,155,235,.52); border-radius: 50%; background: radial-gradient(ellipse, rgba(11,65,124,.86), rgba(3,25,55,.9)); box-shadow: inset 0 0 16px rgba(45,151,235,.16), 0 0 12px rgba(38,131,216,.15); }.satellite-left { top: 48%; left: 5%; }.satellite-right { top: 48%; right: 5%; }.satellite-bottom { bottom: 3%; left: 50%; transform: translateX(-50%); }.health-satellite small { position: absolute; top: 10px; color: #77a3c5; font-size: 7px; }.health-satellite strong { margin-top: 13px; color: #62cfff; font: 720 25px/1 "Bahnschrift", sans-serif; }.health-satellite em { margin-top: 20px; color: #7597b1; font-size: 7px; font-style: normal; }.health-satellite.warning strong { color: #f1bc46; }.health-satellite.danger strong { color: #f06a62; }.health-satellite.good strong { color: #58dcb3; }
.health-rule { position: absolute; z-index: 6; right: 15px; bottom: 11px; left: 15px; display: flex; min-height: 31px; align-items: center; justify-content: center; gap: 7px; border: 1px solid rgba(53,143,214,.2); color: #6096be; font-size: 7px; background: rgba(5,35,70,.66); }.health-rule strong { color: #9ec8e5; font-size: 8px; }
.warning-panel { min-height: 135px; }.warning-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 6px; padding: 9px; }.warning-grid > div { display: flex; min-height: 72px; align-items: center; gap: 8px; padding: 8px; border: 1px solid rgba(48,138,207,.2); color: #57c5ee; background: rgba(14,65,105,.18); }.warning-grid > div.warning-danger { color: #f26660; border-color: rgba(213,67,61,.27); background: rgba(111,28,31,.17); }.warning-grid > div.warning-warning { color: #eda83e; border-color: rgba(215,139,42,.24); background: rgba(105,68,18,.15); }.warning-grid > div.warning-good { color: #57d7aa; }.warning-grid span { display: flex; min-width: 0; flex-direction: column; }.warning-grid small, .warning-grid em { overflow: hidden; color: #6889a5; font-size: 6px; font-style: normal; text-overflow: ellipsis; white-space: nowrap; }.warning-grid strong { margin: 4px 0; color: currentColor; font: 700 17px/1 "Bahnschrift", sans-serif; }
.owner-panel { min-height: 151px; }.owner-table { padding: 7px 10px 9px; }.owner-head, .owner-row { display: grid; grid-template-columns: 1.6fr .65fr .55fr .8fr 1fr; align-items: center; gap: 6px; min-height: 25px; border-bottom: 1px solid rgba(55,127,184,.12); }.owner-head { color: #527693; font-size: 6px; }.owner-row { color: #8ba9bf; font-size: 7px; }.owner-row strong { display: flex; align-items: center; gap: 4px; color: #bad7e9; font-size: 7px; }.owner-row b { color: #f0993e; }.owner-row em { color: #ec625d; font-style: normal; }.owner-row small { color: #6ba8cf; font-size: 7px; }
.warehouse-panel { min-height: 250px; }.warehouse-bars { display: grid; gap: 12px; padding: 13px 14px; }.warehouse-bars > div { display: grid; gap: 5px; }.warehouse-bars header, .warehouse-bars footer { display: flex; align-items: center; justify-content: space-between; }.warehouse-bars header strong { color: #bed8e9; font-size: 9px; }.warehouse-bars header span { color: #5a7c99; font-size: 7px; }.warehouse-track { height: 13px; overflow: hidden; border: 1px solid rgba(45,126,195,.22); border-radius: 2px; background: rgba(13,49,84,.48); }.warehouse-track i { display: block; min-width: 2px; height: 100%; background: linear-gradient(90deg, #248cd9, #f1664f); box-shadow: 0 0 9px rgba(239,87,68,.45); }.warehouse-bars footer b { color: #dcefff; font: 650 9px "Bahnschrift", sans-serif; }.warehouse-bars footer em { color: #e87565; font-size: 7px; font-style: normal; }
.heat-panel { min-height: 225px; }.heat-matrix { --columns: 3; display: grid; grid-template-columns: 58px repeat(var(--columns), 1fr); gap: 4px; padding: 12px 12px 7px; }.heat-matrix > * { display: grid; min-height: 28px; place-items: center; }.heat-matrix > strong, .heat-matrix > span, .heat-corner { color: #6688a3; font-size: 7px; font-weight: 550; }.heat-matrix > div:not(.heat-corner) { --heat: #35a9e8; border: 1px solid color-mix(in srgb, var(--heat) calc(20% + var(--intensity) * 45%), transparent); color: #d7efff; font: 650 10px "Bahnschrift", sans-serif; background: color-mix(in srgb, var(--heat) calc(8% + var(--intensity) * 44%), rgba(6,31,59,.75)); box-shadow: inset 0 0 12px color-mix(in srgb, var(--heat) calc(var(--intensity) * 14%), transparent); }.heat-matrix .heat-warning { --heat: #ebc042 !important; }.heat-matrix .heat-stagnant { --heat: #ee8b32 !important; }.heat-matrix .heat-severe { --heat: #e94f4b !important; }.heat-legend { display: flex; justify-content: flex-end; gap: 9px; padding: 0 12px 10px; }.heat-legend span { display: flex; align-items: center; gap: 4px; color: #587895; font-size: 6px; }.heat-legend i { width: 12px; height: 5px; background: #17476b; }.heat-legend .mid { background: #aa722d; }.heat-legend .high { background: #d94b47; }
.rules-panel { min-height: 260px; }.rule-list { display: grid; gap: 6px; padding: 10px; }.rule-list > div { display: grid; grid-template-columns: 53px 1fr; gap: 4px 7px; padding: 8px; border-left: 2px solid #38a9e9; background: rgba(21,73,114,.15); }.rule-list > div.warning { border-color: #e9bf47; }.rule-list > div.stagnant { border-color: #ed8d34; }.rule-list > div.severe { border-color: #ea514c; }.rule-list span { grid-row: span 2; align-self: center; color: #9cc8e3; font-size: 8px; }.rule-list strong { color: #a9c4d6; font-size: 7px; font-weight: 550; }.rule-list small { color: #557998; font-size: 6px; }
.aging-secondary-grid { display: grid; grid-template-columns: .95fr .78fr .88fr 1.4fr; gap: 10px; margin-top: 10px; }.aging-secondary-grid .aging-panel { min-height: 150px; }.panel-heading.compact { min-height: 38px; }.readiness-body { display: grid; grid-template-columns: 96px 1fr; align-items: center; gap: 12px; padding: 10px 14px; }.readiness-ring { --progress: 0deg; position: relative; display: grid; width: 78px; height: 78px; place-items: center; border-radius: 50%; background: conic-gradient(#43d9cf var(--progress), rgba(47,95,125,.34) 0); }.readiness-ring::before { position: absolute; inset: 8px; border-radius: 50%; content: ""; background: #061a34; }.readiness-ring span { position: relative; z-index: 1; text-align: center; }.readiness-ring strong { display: block; color: #6be4d5; font: 710 20px/1 "Bahnschrift", sans-serif; }.readiness-ring small { display: block; margin-top: 4px; color: #6989a2; font-size: 6px; }.readiness-list p { display: flex; justify-content: space-between; margin: 4px 0; color: #7796ad; font-size: 7px; }.readiness-list p b { color: #79d8cb; }.readiness-list > i { display: block; height: 3px; overflow: hidden; background: rgba(51,98,130,.35); }.readiness-list > i em { display: block; height: 100%; background: linear-gradient(90deg, #229ed1, #53dcb8); }.readiness-list > small { display: block; margin-top: 7px; color: #566f83; font-size: 6px; }
.priority-score { display: grid; grid-template-columns: .85fr 1.15fr; align-items: center; padding: 12px 14px; }.priority-score > div { display: grid; place-items: center; color: #f29c3d; }.priority-score > div strong { margin-top: 4px; color: #ffc26c; font: 730 25px/1 "Bahnschrift", sans-serif; }.priority-score > div span { margin-top: 4px; color: #8b745c; font-size: 6px; }.priority-score ul { margin: 0; padding: 0; list-style: none; }.priority-score li { display: flex; justify-content: space-between; padding: 5px 0; border-bottom: 1px solid rgba(61,128,180,.12); color: #718ea4; font-size: 7px; }.priority-score li b { color: #b9d8e9; }
.value-body { display: grid; grid-template-columns: 94px 1fr; align-items: center; padding: 10px 14px; }.value-gem { display: grid; width: 72px; height: 72px; place-items: center; border: 1px solid rgba(58,195,229,.43); color: #54d6ed; background: radial-gradient(circle, rgba(29,159,205,.27), rgba(5,31,62,.68)); box-shadow: inset 0 0 18px rgba(49,192,225,.18), 0 0 14px rgba(45,178,221,.14); clip-path: polygon(50% 0, 90% 25%, 78% 84%, 50% 100%, 22% 84%, 10% 25%); }.value-body dl { margin: 0; }.value-body dl div { display: flex; justify-content: space-between; gap: 8px; padding: 5px 0; border-bottom: 1px solid rgba(60,128,181,.12); }.value-body dt { color: #6f8ea5; font-size: 7px; }.value-body dd { margin: 0; color: #70d8e9; font: 650 8px "Bahnschrift", sans-serif; }
.value-gem img { display: block; width: 100%; height: 100%; object-fit: contain; }
.action-buttons { display: grid; grid-template-columns: repeat(4, 1fr); gap: 7px; padding: 12px; }.action-buttons button { display: flex; min-width: 0; min-height: 78px; align-items: center; justify-content: center; gap: 8px; cursor: pointer; border: 1px solid rgba(56,145,215,.25); color: #59bfe7; background: rgba(13,61,101,.22); }.action-buttons button:hover { border-color: rgba(73,185,255,.52); background: rgba(20,89,145,.28); }.action-buttons button:nth-child(1) { color: #ec7a65; border-color: rgba(220,83,63,.27); }.action-buttons button:nth-child(2) { color: #e0b557; }.action-buttons button:nth-child(3) { color: #61d7b3; }.action-buttons span { display: flex; min-width: 0; flex-direction: column; text-align: left; }.action-buttons strong { color: #bedbea; font-size: 8px; }.action-buttons small { margin-top: 4px; overflow: hidden; color: #587990; font-size: 6px; text-overflow: ellipsis; white-space: nowrap; }
.aging-footer-note { display: flex; min-height: 32px; align-items: center; justify-content: center; gap: 7px; margin-top: 10px; border: 1px solid rgba(51,128,190,.18); color: #577995; font-size: 7px; background: rgba(5,28,53,.66); }
@keyframes aging-live { 0%, 100% { opacity: .5; transform: scale(.8); } 50% { opacity: 1; transform: scale(1.1); } }
@keyframes aging-enter { from { opacity: 0; transform: translateY(7px); } to { opacity: 1; transform: translateY(0); } }
@keyframes orbit-breathe { 0%, 100% { opacity: .55; filter: brightness(.8); } 50% { opacity: 1; filter: brightness(1.2); } }
/* Responsive variants are retained for a future fluid mode. */
@media (max-width: 0px) {
  .aging-kpis { grid-template-columns: repeat(3, 1fr); }.aging-primary-grid { grid-template-columns: 1fr 1.35fr; }.right-column { grid-column: 1 / -1; display: grid; grid-template-columns: repeat(3, 1fr); }.aging-secondary-grid { grid-template-columns: repeat(2, 1fr); }.aging-toolbar { align-items: stretch; flex-direction: column; }.aging-filters { justify-content: flex-end; }
}
@media (max-width: 0px) {
  .aging-page::before { left: 0; }.aging-hero { align-items: flex-start; flex-direction: column; }.aging-live { align-items: flex-start; }.aging-toolbar { position: relative; top: auto; }.aging-tabs { width: 100%; overflow-x: auto; }.aging-tabs button { min-width: 92px; }.aging-filters { flex-wrap: wrap; justify-content: flex-start; }.aging-kpis, .aging-primary-grid, .right-column, .aging-secondary-grid { grid-template-columns: 1fr 1fr; }.center-column { grid-column: span 2; }.right-column { grid-column: 1 / -1; }.health-core-panel { min-height: 420px; }
}
@media (max-width: 0px) {
  .aging-hero, .aging-toolbar { padding-inline: 16px; }.aging-title-block { align-items: flex-start; }.aging-emblem { width: 48px; height: 48px; flex: 0 0 48px; }.aging-title-block h2 { font-size: 22px; }.aging-live small { display: none; }.aging-filters label { flex: 1 1 130px; }.aging-kpis, .aging-primary-grid, .right-column, .aging-secondary-grid { grid-template-columns: 1fr; }.center-column { grid-column: auto; }.distribution-body { grid-template-columns: 116px 1fr; }.aging-donut { width: 108px; height: 108px; }.orbit-a { width: 340px; }.orbit-b { width: 270px; }.health-satellite { width: 100px; }.satellite-left { left: 1%; }.satellite-right { right: 1%; }.warning-grid { grid-template-columns: 1fr 1fr; }.aging-secondary-grid { grid-template-columns: 1fr; }.action-buttons { grid-template-columns: 1fr 1fr; }.source-note { max-width: 120px; text-align: right; }
}
@media (prefers-reduced-motion: reduce) { .aging-live i, .aging-kpi, .orbit { animation: none; } }

/* Fixed 1920 x 1080 canvas, uniformly scaled to fit any viewport. */
.aging-page { position: relative; z-index: 1; display: flex; width: 1920px; min-width: 1920px; max-width: none; height: 1080px; min-height: 1080px; max-height: none; aspect-ratio: 16 / 9; flex: none; flex-direction: column; margin: 0; overflow: hidden; container-type: inline-size; zoom: min(calc(100vw / 1920px), calc(100dvh / 1080px)); }
.aging-page::before { position: absolute; inset: 0; }
.aging-hero { min-height: 0; flex: 0 0 clamp(68px, 5.2cqw, 90px); padding: clamp(9px, .8cqw, 14px) clamp(18px, 1.5cqw, 28px); }
.aging-toolbar { position: relative; top: auto; min-height: 0; flex: 0 0 clamp(38px, 3cqw, 50px); align-items: center; justify-content: flex-end; flex-direction: row; padding: clamp(4px, .38cqw, 7px) clamp(18px, 1.5cqw, 28px); }
.aging-filters { flex-wrap: nowrap; justify-content: flex-end; }
.aging-board { display: flex; min-height: 0; flex: 1; flex-direction: column; padding: clamp(6px, .55cqw, 10px) clamp(8px, .85cqw, 16px); }
.aging-kpis { min-height: 0; flex: 0 0 clamp(66px, 5cqw, 86px); grid-template-columns: repeat(6, minmax(0, 1fr)); gap: clamp(5px, .48cqw, 9px); }
.aging-kpi { min-height: 0; height: 100%; padding: clamp(7px, .68cqw, 12px) clamp(8px, .75cqw, 14px); }
.aging-primary-grid { min-height: 0; flex: 1; grid-template-columns: minmax(0, .88fr) minmax(0, 1.38fr) minmax(0, .96fr); grid-template-rows: minmax(0, 1fr); gap: clamp(5px, .52cqw, 10px); margin-top: clamp(5px, .52cqw, 10px); }
.aging-column { min-height: 0; height: 100%; gap: clamp(5px, .52cqw, 10px); }
.aging-panel, .distribution-panel, .risk-list-panel, .health-core-panel, .warning-panel, .owner-panel, .warehouse-panel, .heat-panel, .rules-panel { min-height: 0; }
.left-column .distribution-panel { flex: .45 1 0; }.left-column .risk-list-panel { flex: .55 1 0; }
.center-column .health-core-panel { display: flex; flex: .61 1 0; flex-direction: column; }.center-column .warning-panel { flex: .19 1 0; }.center-column .owner-panel { flex: .20 1 0; }
.right-column .warehouse-panel { flex: .30 1 0; }.right-column .heat-panel { flex: .29 1 0; }.right-column .rules-panel { flex: .41 1 0; }
.health-stage { min-height: 0; height: auto; flex: 1; margin-top: clamp(20px, 2.2cqw, 42px); }
.risk-table-wrap { height: calc(100% - 42px); overflow: hidden; }
.warning-grid { height: calc(100% - 42px); align-items: stretch; }.warning-grid > div { min-height: 0; }
.owner-table { height: calc(100% - 42px); overflow: hidden; }
.aging-secondary-grid { min-height: 0; flex: 0 0 clamp(112px, 7.8cqw, 150px); grid-template-columns: .95fr .78fr .88fr 1.4fr; gap: clamp(5px, .52cqw, 10px); margin-top: clamp(5px, .52cqw, 10px); }
.aging-secondary-grid .aging-panel { min-height: 0; height: 100%; }
.aging-footer-note { min-height: 0; flex: 0 0 clamp(22px, 1.65cqw, 32px); margin-top: clamp(4px, .42cqw, 8px); }

@media (max-height: 0px) {
  .aging-hero { flex-basis: 56px; padding-block: 5px; }.aging-emblem { width: 44px; height: 44px; }.aging-title-block h2 { font-size: 22px; }.aging-title-block span { margin-top: 3px; }
  .aging-toolbar { flex-basis: 34px; padding-block: 2px; }.aging-filters label, .aging-reset { height: 28px; }
  .aging-board { padding-block: 5px; }.aging-kpis { flex-basis: 60px; }.aging-kpi-icon { width: 38px; height: 38px; flex-basis: 38px; }.aging-kpi strong { font-size: 17px; }.aging-kpi small { margin-top: 3px; }
  .panel-heading, .panel-heading.compact { min-height: 28px; padding: 3px 7px; }.panel-number { font-size: 16px; }.panel-heading h3 { font-size: 9px; }.panel-heading p { margin-top: 1px; }.panel-heading button { height: 20px; }
  .left-column .distribution-panel { flex-basis: 44%; }.left-column .risk-list-panel { flex-basis: 56%; }.center-column .health-core-panel { flex-basis: 58%; }.center-column .warning-panel { flex-basis: 19%; }.center-column .owner-panel { flex-basis: 23%; }.right-column .warehouse-panel { flex-basis: 29%; }.right-column .heat-panel { flex-basis: 28%; }.right-column .rules-panel { flex-basis: 43%; }
  .distribution-body { grid-template-columns: 96px 1fr; gap: 6px; padding: 5px 8px 3px; }.aging-donut { width: 88px; height: 88px; }.aging-donut::before { inset: 10px; }.aging-donut strong { font-size: 12px; }.bucket-list button { min-height: 22px; padding-block: 2px 4px; }.distribution-panel > footer { padding: 0 7px 5px; }.distribution-panel > footer span { min-height: 20px; }
  .risk-table-wrap { height: calc(100% - 28px); }.risk-table-wrap th { height: 20px; }.risk-table-wrap th, .risk-table-wrap td { padding: 3px 5px; }.risk-table-wrap td small { margin-top: 1px; }
  .health-label.top { top: 7px; width: 126px; height: 58px; }.health-label.top span { top: 6px; }.health-label.top strong { font-size: 27px; }.health-label.top em { bottom: 5px; }.health-stage { margin-top: 18px; }.warehouse-core { transform: translate(-50%, -50%) scale(.7); }.orbit-a { width: 320px; height: 190px; }.orbit-b { width: 245px; height: 145px; }.orbit-c { width: 170px; height: 100px; }.health-satellite { width: 84px; height: 46px; }.health-satellite small { top: 5px; }.health-satellite strong { margin-top: 10px; font-size: 18px; }.health-satellite em { margin-top: 14px; }.health-rule { right: 8px; bottom: 4px; left: 8px; min-height: 21px; }
  .warning-grid { height: calc(100% - 28px); gap: 3px; padding: 4px 6px; }.warning-grid > div { padding: 4px; }.warning-grid strong { margin: 2px 0; font-size: 13px; }
  .owner-table { height: calc(100% - 28px); padding: 2px 7px 4px; }.owner-head, .owner-row { min-height: 18px; }
  .warehouse-bars { gap: 5px; padding: 6px 9px; }.warehouse-bars > div { gap: 2px; }.warehouse-track { height: 7px; }.heat-matrix { gap: 2px; padding: 4px 8px 2px; }.heat-matrix > * { min-height: 20px; }.heat-legend { padding: 0 8px 4px; }.rule-list { gap: 3px; padding: 5px 7px; }.rule-list > div { gap: 2px 5px; padding: 4px 6px; }
  .aging-secondary-grid { flex-basis: 100px; }.readiness-body, .priority-score, .value-body { height: calc(100% - 28px); padding: 4px 8px; }.readiness-ring { width: 56px; height: 56px; }.readiness-body { grid-template-columns: 65px 1fr; gap: 5px; }.priority-score > div strong { font-size: 19px; }.priority-score li, .value-body dl div { padding: 3px 0; }.value-body { grid-template-columns: 66px 1fr; }.value-gem { width: 52px; height: 52px; }.action-buttons { height: calc(100% - 28px); gap: 3px; padding: 5px; }.action-buttons button { min-height: 0; padding: 3px; }.aging-footer-note { flex-basis: 19px; margin-top: 3px; }
}

/* Reference layout: central health stage, stacked right rail and four action modules. */
.center-column .health-core-panel { flex: .72 1 0; }
.center-column .warning-panel { flex: .28 1 0; }
.right-column .warehouse-panel { flex: .34 1 0; }
.right-column .heat-panel { flex: .34 1 0; }
.right-column .owner-panel { flex: .32 1 0; }
.right-column .owner-table { height: calc(100% - 29px); padding: 2px 8px 4px; overflow: hidden; }
.right-column .owner-head, .right-column .owner-row { min-height: 18px; }
.right-column .warehouse-bars { gap: 5px; padding: 6px 9px; }
.right-column .warehouse-bars > div { gap: 2px; }
.right-column .warehouse-track { height: 7px; }
.right-column .heat-matrix { gap: 2px; padding: 4px 8px 2px; }
.right-column .heat-matrix > * { min-height: 20px; }
.right-column .heat-legend { padding: 0 8px 4px; }
.aging-secondary-grid { flex-basis: 168px; grid-template-columns: .95fr .78fr .88fr 1.35fr; }
@media (max-height: 0px) {
  .aging-secondary-grid { flex-basis: 86px; }.readiness-body, .priority-score, .value-body { height: calc(100% - 28px); padding: 3px 8px; }.readiness-ring { width: 52px; height: 52px; }.readiness-body { grid-template-columns: 61px 1fr; gap: 5px; }.priority-score > div strong { font-size: 18px; }.priority-score li, .value-body dl div { padding: 2px 0; }.value-body { grid-template-columns: 62px 1fr; }.value-gem { width: 48px; height: 48px; }
}

/* Reference-driven cockpit detailing: double rails, luminous brackets and a real warehouse visual. */
.aging-hero { justify-content: center; }
.aging-live { position: absolute; right: 28px; }
.aging-toolbar { justify-content: space-between; }
.aging-tabs button { position: relative; overflow: hidden; }
.aging-tabs button span { display: block; transform: skewX(15deg); }
.aging-tabs button::after { position: absolute; right: 14px; bottom: 4px; left: 14px; height: 1px; content: ""; opacity: 0; background: #70cfff; box-shadow: 0 0 7px #2c9cff; transition: opacity .18s ease; }
.aging-tabs button.active::after { opacity: 1; }
.aging-tabs button:focus-visible, .aging-reset:focus-visible, .panel-heading button:focus-visible { outline: 2px solid #8bdcff; outline-offset: 2px; }

.aging-kpi { border-color: color-mix(in srgb, var(--tone) 58%, transparent); box-shadow: inset 0 0 0 1px rgba(34,125,204,.13), inset 0 0 26px color-mix(in srgb, var(--tone) 8%, transparent), 0 0 13px rgba(0,96,191,.12); }
.aging-kpi::before { position: absolute; top: 4px; right: 4px; bottom: 4px; left: 4px; border: 1px solid color-mix(in srgb, var(--tone) 17%, transparent); content: ""; pointer-events: none; clip-path: polygon(0 0, 24px 0, 24px 1px, 1px 1px, 1px 18px, 0 18px, 0 0, 100% 0, 100% 18px, calc(100% - 1px) 18px, calc(100% - 1px) 1px, calc(100% - 24px) 1px, calc(100% - 24px) 0, 100% 0, 100% 100%, calc(100% - 24px) 100%, calc(100% - 24px) calc(100% - 1px), calc(100% - 1px) calc(100% - 1px), calc(100% - 1px) calc(100% - 18px), 100% calc(100% - 18px), 100% 100%, 0 100%, 0 calc(100% - 18px), 1px calc(100% - 18px), 1px calc(100% - 1px), 24px calc(100% - 1px), 24px 100%, 0 100%); }

.aging-panel { border-color: rgba(70,174,247,.48); box-shadow: inset 0 0 0 1px rgba(17,82,142,.32), inset 0 0 28px rgba(21,113,198,.055), 0 8px 26px rgba(0,0,0,.2), 0 0 10px rgba(20,113,211,.08); }
.aging-panel::before { top: 4px; right: auto; bottom: auto; left: 4px; width: 34px; height: 18px; border-top: 1px solid #75d2ff; border-left: 1px solid #75d2ff; content: ""; background: none; box-shadow: -2px -2px 8px rgba(43,159,255,.32); }
.aging-panel::after, .health-core-panel::after { position: absolute; right: 4px; bottom: 4px; left: auto; width: 34px; height: 18px; border-right: 1px solid #75d2ff; border-bottom: 1px solid #75d2ff; content: ""; pointer-events: none; opacity: 1; background: none; box-shadow: 2px 2px 8px rgba(43,159,255,.28); }
.panel-heading { position: relative; padding-left: 8px; border-bottom-color: rgba(72,157,225,.3); background: linear-gradient(90deg, rgba(24,91,158,.38), rgba(7,36,70,.2) 62%, transparent); }
.panel-heading::after { position: absolute; right: 0; bottom: -1px; width: 38%; height: 1px; content: ""; background: linear-gradient(90deg, transparent, rgba(82,181,255,.6)); }
.panel-number { display: grid; min-width: 30px; height: 29px; place-items: center; padding-right: 4px; color: #e7f7ff; background: linear-gradient(135deg, rgba(49,151,244,.82), rgba(10,63,121,.2)); text-shadow: 0 0 9px #279dff; clip-path: polygon(0 0, 100% 0, 76% 100%, 0 100%); }

.health-core-panel { background: repeating-radial-gradient(ellipse at 50% 58%, transparent 0 34px, rgba(51,139,216,.09) 35px 36px, transparent 37px 55px), radial-gradient(circle at 50% 51%, rgba(19,116,227,.25), transparent 42%), linear-gradient(180deg, rgba(4,27,57,.98), rgba(3,16,34,.98)); }
.warehouse-core { top: 54%; width: min(68%, 340px); aspect-ratio: 1; filter: drop-shadow(0 0 18px rgba(38,151,255,.42)); }
.warehouse-core::after { position: absolute; right: 7%; bottom: 4%; left: 7%; height: 15%; border: 1px solid rgba(64,178,255,.48); border-radius: 50%; content: ""; box-shadow: inset 0 0 18px rgba(39,146,255,.24), 0 0 19px rgba(39,146,255,.3); transform: translateY(42%); }
.warehouse-core img { position: relative; z-index: 1; display: block; width: 100%; height: 100%; object-fit: contain; mix-blend-mode: screen; filter: saturate(1.12) contrast(1.06); mask-image: radial-gradient(ellipse 66% 68% at 50% 55%, #000 48%, rgba(0,0,0,.92) 62%, transparent 88%); }
.orbit-a { width: min(88%, 520px); }.orbit-b { width: min(70%, 410px); }.orbit-c { width: min(52%, 300px); }
.health-satellite { backdrop-filter: blur(5px); }

/* Responsive variants are retained for a future fluid mode. */
@media (max-width: 0px) {
  .aging-toolbar { align-items: stretch; justify-content: space-between; }
}
@media (max-width: 0px) {
  .aging-toolbar { flex-direction: column; }
  .warehouse-core { width: min(62%, 300px); }
}
@media (max-width: 0px) {
  .warehouse-core { width: min(72%, 270px); }
  .aging-tabs button { min-width: 88px; padding-inline: 12px; }
}

@media (max-width: 0px) {
  .aging-page { display: block; width: 100%; height: auto; min-height: 100dvh; max-height: none; aspect-ratio: auto; overflow: visible; container-type: normal; }
  .aging-hero { min-height: 88px; padding: 12px 16px; }
  .aging-toolbar { position: relative; top: auto; min-height: auto; padding: 8px 16px; }
  .aging-tabs { flex: 0 0 auto; width: 100%; overflow-x: auto; }
  .aging-filters { display: grid; width: 100%; grid-template-columns: repeat(3, minmax(0, 1fr)) 34px; }
  .aging-filters label { min-width: 0; }
  .aging-filters select { width: 100%; min-width: 0; }
  .aging-board { display: block; padding: 10px 12px 28px; }
  .aging-kpis { display: grid; height: auto; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
  .aging-kpi { min-height: 82px; height: auto; }
  .aging-primary-grid { display: grid; grid-template-columns: minmax(0, 1fr); grid-template-rows: auto; gap: 10px; margin-top: 10px; }
  .aging-column, .center-column, .right-column { display: flex; height: auto; grid-column: auto; flex-direction: column; }
  .aging-panel, .distribution-panel, .risk-list-panel, .warning-panel, .owner-panel, .warehouse-panel, .heat-panel { min-height: initial; }
  .distribution-panel, .risk-list-panel, .warehouse-panel, .heat-panel { flex: none !important; }
  .distribution-panel { min-height: 320px; }
  .risk-list-panel { min-height: 350px; }
  .health-core-panel { min-height: 430px; flex: none !important; }
  .warning-panel { min-height: 150px; flex: none !important; }
  .owner-panel { min-height: 190px; flex: none !important; }
  .warehouse-panel { min-height: 250px; }
  .heat-panel { min-height: 230px; }
  .risk-table-wrap, .warning-grid, .owner-table { height: auto; overflow: auto; }
  .aging-secondary-grid { display: grid; min-height: 0; height: auto; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; margin-top: 10px; }
  .aging-secondary-grid .aging-panel { min-height: 150px; height: auto; }
  .aging-footer-note { min-height: 38px; height: auto; padding: 7px 10px; text-align: center; }
}

@media (max-width: 0px) {
  .aging-title-block { gap: 10px; }
  .aging-title-block h2 { font-size: 20px; letter-spacing: .06em; }
  .aging-title-block span { font-size: 8px; line-height: 1.4; }
  .aging-live { display: none; }
  .aging-filters { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .aging-reset { width: 100%; }
  .aging-kpi { gap: 8px; padding: 9px; }
  .aging-kpi-icon { width: 40px; height: 40px; flex-basis: 40px; }
  .aging-kpi strong { font-size: 17px; }
  .aging-primary-grid, .right-column, .aging-secondary-grid { grid-template-columns: minmax(0, 1fr); }
  .distribution-body { grid-template-columns: 112px minmax(0, 1fr); }
  .warning-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .health-satellite { width: 92px; }
  .aging-secondary-grid .aging-panel { min-height: 145px; }
}

/* Reference-calibrated visual system: 16:9 command cockpit. */
.aging-page {
  --aging-blue: #159cff;
  --aging-cyan: #45dfff;
  --aging-line: rgba(32, 151, 244, .66);
  --aging-panel: rgba(2, 20, 43, .96);
  color: #d9efff;
  background:
    linear-gradient(rgba(23, 105, 184, .035) 1px, transparent 1px),
    linear-gradient(90deg, rgba(23, 105, 184, .03) 1px, transparent 1px),
    radial-gradient(ellipse at 50% 34%, rgba(8, 76, 164, .24), transparent 43%),
    linear-gradient(180deg, #010817 0%, #021127 50%, #010817 100%);
  background-size: 32px 32px, 32px 32px, 100% 100%, 100% 100%;
}
.aging-page::before { opacity: .48; }
.aging-hero {
  min-height: 0;
  flex: 0 0 100px;
  align-items: flex-start;
  padding: 10px 28px 0;
  border-bottom-color: rgba(28, 137, 235, .48);
  background: linear-gradient(180deg, rgba(1, 9, 25, .98), rgba(2, 16, 38, .92));
  box-shadow: inset 0 -12px 30px rgba(0, 69, 151, .1);
}
.aging-hero::before { position: absolute; right: 24%; bottom: -1px; left: 24%; height: 2px; content: ""; background: #198fff; box-shadow: 0 0 12px #198fff; }
.aging-version { position: absolute; top: 13px; left: 15px; display: grid; height: 27px; min-width: 110px; place-items: center; border: 1px solid rgba(28,139,236,.58); border-radius: 8px; color: #a9d8ff; font-size: 12px; background: rgba(4,35,74,.55); box-shadow: inset 0 0 12px rgba(30,128,219,.12); }
.aging-title-block { gap: 14px; }
.aging-emblem { width: 64px; height: 64px; color: #b5e7ff; border-color: rgba(84,192,255,.9); background: radial-gradient(circle, rgba(40,161,255,.44), rgba(2,31,72,.45)); filter: drop-shadow(0 0 10px rgba(48,156,255,.7)); }
.aging-title-block h2 { color: #eef7ff; font-size: 36px; font-weight: 760; line-height: 1.05; letter-spacing: .1em; text-shadow: 0 0 16px rgba(109,190,255,.52); }
.aging-title-block p { margin: 7px 0 0; color: #668db9; font-size: 11px; letter-spacing: .27em; }
.aging-live { top: 13px; right: 18px; flex-direction: row; align-items: center; gap: 14px; }
.aging-live small { padding-right: 14px; border-right: 1px solid rgba(54,122,190,.28); color: #55789c; font-size: 8px; line-height: 1.6; text-align: right; }
.aging-live small b { color: #90b8d8; font-weight: 500; }
.aging-live span { padding: 6px 12px; color: #c1e7ff; border-color: rgba(47,143,220,.4); background: rgba(4,26,57,.72); }
.aging-toolbar { position: absolute; top: 54px; right: 0; left: 0; z-index: 32; min-height: 0; height: 43px; justify-content: flex-end; flex: none; padding: 4px 20px; border-bottom: 0; background: transparent; backdrop-filter: none; }
.aging-tabs { height: 38px; }
.aging-tabs button { min-width: 108px; padding-inline: 19px; border-color: rgba(23,108,194,.48); color: #8ba6c2; font-size: 11px; background: linear-gradient(180deg, rgba(5,31,65,.88), rgba(2,16,36,.82)); }
.aging-tabs button.active { color: #d5efff; border-color: #31a8ff; background: linear-gradient(180deg, rgba(24,124,224,.68), rgba(8,52,111,.76)); box-shadow: inset 0 0 18px rgba(60,177,255,.3), 0 0 13px rgba(28,143,255,.34); }
.aging-filters label, .aging-reset { height: 33px; border-color: rgba(48,125,199,.42); background: rgba(3,22,48,.86); }
.aging-filters label span { color: #7394b2; font-size: 9px; }
.aging-filters select { color: #c1dbef; font-size: 9px; }
.aging-date { min-width: 108px; color: #77a7ce; }
.aging-date b { color: #b7d4e9; font-size: 8px; font-weight: 500; }
.aging-board { padding: 11px 18px 9px; }
.aging-kpis { flex-basis: 92px; gap: 12px; }
.aging-kpi { padding: 10px 15px; border-color: color-mix(in srgb, var(--tone) 68%, transparent); background: radial-gradient(circle at 21% 50%, color-mix(in srgb, var(--tone) 11%, transparent), transparent 31%), linear-gradient(145deg, rgba(4,31,62,.98), rgba(2,18,39,.97)); box-shadow: inset 0 0 0 1px rgba(32,105,176,.2), inset 0 0 30px color-mix(in srgb, var(--tone) 7%, transparent), 0 0 14px rgba(13,105,205,.16); }
.aging-kpi-icon { width: 57px; height: 57px; flex-basis: 57px; border-color: color-mix(in srgb, var(--tone) 72%, transparent); box-shadow: inset 0 0 19px color-mix(in srgb, var(--tone) 22%, transparent), 0 0 17px color-mix(in srgb, var(--tone) 28%, transparent); }
.aging-kpi-icon svg { width: 30px; height: 30px; stroke-width: 2; }
.aging-kpi span { color: #9cb7cf; font-size: 10px; }
.aging-kpi strong { margin-top: 5px; font-size: 23px; }
.aging-kpi small { color: color-mix(in srgb, var(--tone) 82%, #8ba7bd); font-size: 9px; }
.aging-primary-grid { grid-template-columns: minmax(0, .9fr) minmax(0, 1.28fr) minmax(0, 1.08fr); gap: 10px; margin-top: 10px; }
.aging-panel { border-color: rgba(31,151,242,.62); background: linear-gradient(180deg, rgba(4,29,58,.97), rgba(2,17,37,.98)); box-shadow: inset 0 0 0 1px rgba(18,84,151,.34), inset 0 0 30px rgba(14,103,193,.06), 0 0 12px rgba(15,112,220,.12); }
.panel-heading, .panel-heading.compact { min-height: 34px; padding: 4px 8px; border-bottom-color: rgba(42,137,213,.34); background: linear-gradient(90deg, rgba(17,80,144,.48), rgba(3,30,63,.24) 72%, transparent); }
.panel-number { min-width: 32px; height: 31px; font-size: 22px; }
.panel-number { display: inline-flex; width: auto; min-width: 0; align-items: center; justify-content: flex-start; flex: none; padding: 0 28px 0 14px; white-space: nowrap; font-size: 16px; font-style: normal; clip-path: polygon(0 0, 100% 0, calc(100% - 15px) 100%, 0 100%); }
.panel-heading h3 { color: #e3f2ff; font-size: 13px; }
.panel-heading p { color: #6083a6; font-size: 6px; }
.risk-table-wrap { height: calc(100% - 34px); }
.risk-table-wrap th { height: 32px; font-size: 11px; }
.risk-table-wrap th, .risk-table-wrap td { padding: 7px 9px; }
.risk-table-wrap td { font-size: 11px; line-height: 1.25; }
.risk-table-wrap td strong { font-size: 13px; }
.risk-table-wrap td small { margin-top: 3px; font-size: 8px; }
.risk-table-wrap td b { font-size: 14px; }
.risk-list-panel .risk-level { min-width: 58px; padding: 4px 8px; font-size: 9px; }
.risk-list-panel .panel-heading button { height: 28px; font-size: 10px; }
.bucket-list button span, .bucket-list button b { font-size: 9px; }
.distribution-body { grid-template-columns: 132px 1fr; gap: 7px; padding: 7px 10px 5px; }
.aging-donut { width: 118px; height: 118px; }
.bucket-list { gap: 1px; }
.bucket-list button { min-height: 24px; padding: 2px 3px 4px; }
.distribution-panel > footer { padding: 0 8px 6px; }
.distribution-panel > footer span { min-height: 25px; }
.distribution-panel > footer span { color: #50d9d1; font-size: 9px; }
.distribution-panel > footer span:last-child { color: #ff625b; }
.health-core-panel { background: repeating-radial-gradient(ellipse at 50% 58%, transparent 0 34px, rgba(47,145,230,.12) 35px 36px, transparent 37px 55px), radial-gradient(circle at 50% 50%, rgba(16,112,231,.32), transparent 44%), linear-gradient(180deg, rgba(3,25,53,.99), rgba(1,12,29,.99)); }
.health-label.top { border-color: rgba(67,191,255,.82); box-shadow: inset 0 0 25px rgba(42,159,255,.34), 0 0 28px rgba(30,144,255,.4); }
.health-label.top strong { color: #65d7ff; font-size: 40px; }
.health-satellite { border-color: rgba(57,180,255,.72); box-shadow: inset 0 0 20px rgba(42,153,242,.24), 0 0 19px rgba(32,136,232,.25); }
.health-satellite strong { font-size: 27px; }
.satellite-bottom { bottom: 11%; }
.warning-grid > div { border-color: currentColor; background: color-mix(in srgb, currentColor 9%, rgba(3,30,59,.86)); box-shadow: inset 0 0 18px color-mix(in srgb, currentColor 8%, transparent); }
.warning-grid strong { font-size: 20px; }
.warehouse-bars header strong, .warehouse-bars footer b { font-size: 9px; }
.warehouse-bars header span, .warehouse-bars footer em, .owner-row, .owner-row strong, .owner-row small { font-size: 8px; }
.heat-matrix > strong, .heat-matrix > span, .heat-corner { color: #86a6c0; font-size: 8px; }
.heat-matrix > div:not(.heat-corner) { border-width: 1px; font-size: 11px; filter: saturate(1.35) brightness(1.12); }
.aging-secondary-grid { flex-basis: 158px; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.readiness-ring { width: 88px; height: 88px; box-shadow: 0 0 18px rgba(61,222,209,.28); }
.readiness-ring strong { font-size: 23px; }
.priority-score > div strong { font-size: 29px; }

/* Readability pass for the remaining 1920 x 1080 wallboard modules. */
.aging-kpi span { font-size: 12px; }
.aging-kpi strong { font-size: 27px; }
.aging-kpi small { font-size: 10px; }
.aging-donut small, .aging-donut em { font-size: 9px; }
.aging-donut strong { font-size: 18px; }
.bucket-list button { min-height: 30px; }
.bucket-list button span, .bucket-list button b { font-size: 11px; }
.bucket-list button span small { font-size: 9px; }
.bucket-list button em { font-size: 10px; }
.distribution-panel > footer span { font-size: 11px; }
.distribution-panel > footer b { font-size: 13px; }
.health-label.top span { font-size: 10px; }
.health-label.top em { font-size: 9px; }
.health-satellite small { font-size: 9px; }
.health-satellite strong { font-size: 30px; }
.health-satellite em { font-size: 9px; }
.health-rule { font-size: 10px; }
.health-rule strong { font-size: 11px; }
.warning-grid small, .warning-grid em { font-size: 9px; }
.warning-grid strong { font-size: 22px; }
.warehouse-bars header strong, .warehouse-bars footer b { font-size: 12px; }
.warehouse-bars header span, .warehouse-bars footer em { font-size: 10px; }
.right-column .heat-matrix > * { min-height: 26px; }
.heat-matrix > strong, .heat-matrix > span, .heat-corner { font-size: 10px; }
.heat-matrix > div:not(.heat-corner) { font-size: 13px; }
.heat-legend span { font-size: 9px; }
.right-column .owner-head, .right-column .owner-row { min-height: 25px; }
.owner-head { font-size: 10px; }
.owner-row, .owner-row strong { font-size: 11px; }
.owner-row small { font-size: 10px; }
.readiness-ring small { font-size: 9px; }
.readiness-list p { font-size: 10px; }
.readiness-list > small { font-size: 8px; }
.priority-score > div span { font-size: 9px; }
.priority-score li { padding-block: 6px; font-size: 10px; }
.value-body dl div { padding-block: 7px; }
.value-body dt { font-size: 10px; }
.value-body dd { font-size: 12px; }
.unit-note, .source-note { font-size: 9px; }
.action-buttons { height: calc(100% - 34px); gap: 0; padding: 6px 4px; }
.action-buttons button { min-height: 0; flex-direction: column; gap: 6px; border: 0; border-left: 1px solid rgba(42,113,180,.18); background: transparent; }
.action-buttons button:first-child { border-left: 0; }
.action-buttons button:hover { border-color: rgba(73,185,255,.3); background: rgba(20,89,145,.16); }
.action-buttons button > svg { width: 53px; height: 53px; padding: 14px; border: 1px solid currentColor; border-radius: 50%; background: radial-gradient(circle, color-mix(in srgb, currentColor 18%, transparent), rgba(3,25,53,.82)); filter: drop-shadow(0 0 7px currentColor); }
.action-buttons span { text-align: center; }
.action-buttons strong { color: #d2e7f5; font-size: 9px; }
.action-buttons small { font-size: 7px; }
.health-stage::before { position: absolute; z-index: 1; top: 54%; left: 50%; width: min(92%, 560px); aspect-ratio: 1.9; border-radius: 50%; content: ""; background: conic-gradient(from 18deg, transparent 0 10%, rgba(255,161,45,.95) 13%, transparent 17% 47%, rgba(53,181,255,.9) 52%, transparent 57% 76%, rgba(255,161,45,.82) 80%, transparent 84%); mask: radial-gradient(closest-side, transparent calc(100% - 3px), #000 calc(100% - 2px)); filter: drop-shadow(0 0 6px rgba(255,151,38,.5)); transform: translate(-50%, -50%) rotateX(66deg); animation: orbit-energy 12s linear infinite reverse; }

/* Motion system: restrained energy flow that keeps dense operational data readable. */
.aging-page { background-size: 100% 100%, 100% 100%; animation: aging-atmosphere 12s ease-in-out infinite alternate; }
.aging-hero::after { transform-origin: center; animation: hero-energy 4.8s ease-in-out infinite; }
.aging-emblem { animation: emblem-charge 3.6s ease-in-out infinite; }
.aging-live span { position: relative; overflow: hidden; }
.aging-live span::after { position: absolute; inset: 0 auto 0 -55%; width: 42%; content: ""; pointer-events: none; background: linear-gradient(90deg, transparent, rgba(156,231,255,.22), transparent); transform: skewX(-18deg); animation: live-sweep 3.4s ease-in-out infinite; }

.aging-kpi { transition: border-color .2s ease, filter .2s ease, transform .2s ease; }
.aging-kpi:hover { z-index: 2; filter: brightness(1.12); transform: translateY(-2px); }
.aging-kpi-icon { animation: kpi-charge 3.2s ease-in-out infinite; animation-delay: var(--delay); }
.aging-kpi::after { animation: kpi-radar 5.2s ease-in-out infinite; animation-delay: var(--delay); }

.aging-panel { transition: border-color .22s ease, box-shadow .22s ease; }
.aging-panel:hover { border-color: rgba(92,196,255,.68); box-shadow: inset 0 0 0 1px rgba(28,103,171,.36), inset 0 0 34px rgba(26,127,220,.075), 0 8px 30px rgba(0,0,0,.22), 0 0 15px rgba(36,141,235,.13); }
.aging-panel::before { animation: corner-charge 3.8s ease-in-out infinite; }
.aging-panel::after, .health-core-panel::after { animation: corner-charge 3.8s ease-in-out -1.9s infinite; }
.panel-heading::after { transform-origin: right center; animation: heading-rail 4.2s ease-in-out infinite; }
.panel-number { position: relative; overflow: hidden; }
.panel-number::after { position: absolute; inset: 0 auto 0 -70%; width: 48%; content: ""; background: linear-gradient(90deg, transparent, rgba(225,249,255,.55), transparent); transform: skewX(-16deg); animation: number-flash 4.6s ease-in-out infinite; }

.orbit::before { position: absolute; inset: -3px; border-radius: 50%; content: ""; background: conic-gradient(from 0deg, transparent 0 60%, rgba(58,171,255,.05) 69%, rgba(104,220,255,.9) 78%, rgba(58,171,255,.08) 86%, transparent 94%); mask: radial-gradient(closest-side, transparent calc(100% - 3px), #000 calc(100% - 2px)); animation: orbit-energy 5.8s linear infinite; }
.orbit-b::before { animation-duration: 7.2s; animation-direction: reverse; }
.orbit-c::before { animation-duration: 4.6s; }
.health-label.top { animation: health-beacon 3.1s ease-in-out infinite; }
.warehouse-core { animation: warehouse-hover 7.2s ease-in-out infinite; }
.warehouse-core img { animation: warehouse-luminance 7.2s ease-in-out infinite; }
.warehouse-scan { position: absolute; z-index: 3; top: 17%; right: 20%; left: 20%; height: 2px; pointer-events: none; opacity: 0; background: linear-gradient(90deg, transparent, rgba(117,229,255,.96), transparent); box-shadow: 0 0 8px rgba(75,190,255,.9), 0 8px 22px rgba(37,145,255,.35); animation: warehouse-scan 3.6s cubic-bezier(.45,0,.55,1) infinite; }
.health-satellite { animation: satellite-signal 4s ease-in-out infinite; }
.satellite-right { animation-delay: -1.3s; }.satellite-bottom { animation-delay: -2.6s; }
.warehouse-track i, .readiness-list > i em { position: relative; overflow: hidden; }
.warehouse-track i::after, .readiness-list > i em::after { position: absolute; inset: 0 auto 0 -45%; width: 34%; content: ""; background: linear-gradient(90deg, transparent, rgba(255,255,255,.78), transparent); animation: progress-sweep 3s ease-in-out infinite; }

@keyframes aging-atmosphere { from { filter: saturate(.96); } to { filter: saturate(1.06); } }
@keyframes hero-energy { 0%, 100% { opacity: .45; transform: scaleX(.58); } 48% { opacity: 1; transform: scaleX(1); } }
@keyframes emblem-charge { 0%, 100% { box-shadow: inset 0 0 18px rgba(46,169,255,.2), 0 0 12px rgba(37,144,255,.14); } 50% { box-shadow: inset 0 0 27px rgba(62,185,255,.34), 0 0 24px rgba(42,157,255,.3); } }
@keyframes live-sweep { 0%, 38% { left: -55%; opacity: 0; } 52% { opacity: 1; } 72%, 100% { left: 120%; opacity: 0; } }
@keyframes kpi-charge { 0%, 100% { box-shadow: inset 0 0 12px color-mix(in srgb, var(--tone) 10%, transparent), 0 0 9px color-mix(in srgb, var(--tone) 10%, transparent); } 50% { box-shadow: inset 0 0 21px color-mix(in srgb, var(--tone) 23%, transparent), 0 0 20px color-mix(in srgb, var(--tone) 26%, transparent); } }
@keyframes kpi-radar { 0%, 100% { opacity: .28; transform: scale(.92); } 50% { opacity: .72; transform: scale(1.12); } }
@keyframes corner-charge { 0%, 100% { opacity: .5; filter: brightness(.8); } 50% { opacity: 1; filter: brightness(1.45); } }
@keyframes heading-rail { 0%, 100% { opacity: .22; transform: scaleX(.22); } 48% { opacity: .9; transform: scaleX(1); } }
@keyframes number-flash { 0%, 48% { left: -70%; opacity: 0; } 58% { opacity: 1; } 72%, 100% { left: 130%; opacity: 0; } }
@keyframes orbit-energy { to { transform: rotate(360deg); } }
@keyframes health-beacon { 0%, 100% { box-shadow: inset 0 0 17px rgba(50,163,255,.2), 0 0 13px rgba(43,155,255,.18); } 50% { box-shadow: inset 0 0 25px rgba(73,190,255,.35), 0 0 27px rgba(43,155,255,.36); } }
@keyframes warehouse-hover { 0%, 100% { margin-top: 3px; } 50% { margin-top: -16px; } }
@keyframes warehouse-luminance { 0%, 100% { filter: saturate(1.05) contrast(1.04) brightness(.94); } 50% { filter: saturate(1.2) contrast(1.08) brightness(1.08); } }
@keyframes warehouse-scan { 0%, 12% { top: 17%; opacity: 0; } 25% { opacity: 1; } 72% { opacity: .8; } 88%, 100% { top: 79%; opacity: 0; } }
@keyframes satellite-signal { 0%, 100% { filter: brightness(.92); } 50% { filter: brightness(1.13); } }
@keyframes progress-sweep { 0%, 28% { left: -45%; opacity: 0; } 45% { opacity: .85; } 68%, 100% { left: 120%; opacity: 0; } }

/* Futuristic readability pass: glossy hierarchy, dimensional symbols and a seamless risk ticker. */
.aging-page {
  color: #eff8ff;
  text-rendering: geometricPrecision;
  -webkit-font-smoothing: antialiased;
}
.aging-panel {
  border-color: rgba(52, 169, 255, .74);
  background:
    linear-gradient(115deg, rgba(72, 190, 255, .055), transparent 24%),
    radial-gradient(circle at 50% -15%, rgba(34, 137, 255, .16), transparent 48%),
    linear-gradient(180deg, rgba(4, 30, 61, .985), rgba(1, 15, 34, .99));
  box-shadow:
    inset 0 1px 0 rgba(169, 228, 255, .18),
    inset 0 0 0 1px rgba(21, 91, 163, .42),
    inset 0 0 36px rgba(17, 111, 211, .075),
    0 8px 24px rgba(0, 5, 18, .38),
    0 0 14px rgba(16, 120, 235, .16);
}
.panel-heading, .panel-heading.compact {
  background:
    linear-gradient(90deg, rgba(20, 101, 184, .62), rgba(5, 42, 85, .34) 64%, transparent),
    linear-gradient(180deg, rgba(84, 186, 255, .08), transparent);
  box-shadow: inset 0 1px 0 rgba(156, 222, 255, .14);
}
.panel-heading h3 { font-size: 14px; font-weight: 750; letter-spacing: .045em; text-shadow: 0 0 10px rgba(88, 188, 255, .3); }
.panel-heading p { color: #789fc4; font-size: 7.5px; font-weight: 650; letter-spacing: .16em; }
.panel-heading button, .unit-note, .source-note { color: #a9d8f7; font-size: 9px; font-weight: 650; }
.panel-number { color: #d9f4ff; text-shadow: 0 0 10px #2c9fff; }

.aging-kpi { box-shadow: inset 0 1px 0 rgba(255,255,255,.17), inset 0 0 0 1px rgba(34,125,204,.28), inset 0 0 32px color-mix(in srgb, var(--tone) 10%, transparent), 0 7px 18px rgba(0,5,18,.32), 0 0 16px color-mix(in srgb, var(--tone) 18%, transparent); }
.aging-kpi-icon { position: relative; overflow: hidden; transform: perspective(100px) rotateX(8deg); }
.aging-kpi-icon::before { position: absolute; inset: 4px; border: 1px solid color-mix(in srgb, var(--tone) 45%, transparent); border-radius: 50%; content: ""; box-shadow: inset 0 -7px 13px rgba(0,0,0,.28), inset 0 5px 12px rgba(255,255,255,.08); }
.aging-kpi-icon::after { position: absolute; top: 5px; right: 10px; left: 10px; height: 13px; border-radius: 50%; content: ""; background: linear-gradient(180deg, rgba(255,255,255,.3), transparent); filter: blur(1px); }
.aging-kpi-icon svg { position: relative; z-index: 1; filter: drop-shadow(0 0 6px currentColor); }
.aging-kpi span { color: #b6cee2; font-size: 11px; font-weight: 650; }
.aging-kpi strong { color: #f6fbff; font-weight: 780; text-shadow: 0 0 11px rgba(121,202,255,.23); }
.aging-kpi small { font-size: 10px; font-weight: 620; }

.risk-table-wrap { position: relative; display: flex; flex-direction: column; padding: 0 5px 4px; }
.risk-table-head, .risk-scroll-row { display: grid; grid-template-columns: minmax(0, 1.72fr) .72fr .76fr .88fr .7fr; align-items: center; gap: 5px; }
.risk-table-head { z-index: 2; height: 27px; flex: 0 0 27px; padding: 0 7px; border-bottom: 1px solid rgba(76, 161, 225, .26); color: #8fb9d7; font-size: 9px; font-weight: 700; background: linear-gradient(90deg, rgba(18,80,137,.42), rgba(7,38,76,.22)); box-shadow: 0 5px 14px rgba(0,11,28,.3); }
.risk-scroll-viewport { position: relative; flex: 1; overflow: hidden; mask-image: linear-gradient(to bottom, transparent 0, #000 7%, #000 93%, transparent 100%); }
.risk-scroll-track { will-change: transform; }
.risk-scroll-track.is-scrolling { animation: risk-ticker var(--risk-scroll-duration) linear infinite; }
.risk-scroll-viewport:hover .risk-scroll-track { animation-play-state: paused; }
.risk-scroll-row { height: 32px; padding: 0 7px; border-bottom: 1px solid rgba(61, 141, 202, .13); color: #a9c6db; font-size: 8.5px; font-weight: 600; background: linear-gradient(90deg, rgba(15,75,126,.1), transparent 72%); }
.risk-scroll-row:nth-child(2n) { background: linear-gradient(90deg, rgba(28,112,179,.14), rgba(3,27,57,.04)); }
.risk-scroll-row > span { min-width: 0; }
.risk-scroll-row strong, .risk-scroll-row small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.risk-scroll-row strong { color: #e5f5ff; font-size: 10px; font-weight: 720; }
.risk-scroll-row small { margin-top: 1px; color: #7198b6; font-size: 7px; font-weight: 560; }
.risk-scroll-row b { color: #ffc15f; font-size: 10px; text-shadow: 0 0 7px rgba(255,162,52,.42); }
.risk-scroll-row .risk-level { min-width: 49px; padding: 3px 5px; font-size: 7px; font-style: normal; font-weight: 720; box-shadow: inset 0 1px 0 rgba(255,255,255,.12), 0 0 8px currentColor; }
@keyframes risk-ticker { to { transform: translateY(calc(var(--risk-row-count) * var(--risk-row-height, 42px) * -1)); } }

.health-core-panel {
  background:
    repeating-radial-gradient(ellipse at 50% 59%, transparent 0 36px, rgba(55,160,246,.12) 37px 38px, transparent 39px 58px),
    radial-gradient(circle at 50% 52%, rgba(12,109,229,.34), transparent 47%),
    linear-gradient(180deg, rgba(2,24,52,.99), rgba(1,11,28,.995));
}
.health-stage::after { position: absolute; z-index: 0; right: 5%; bottom: 7%; left: 5%; height: 35%; border: 1px solid rgba(49,160,255,.24); border-radius: 50%; content: ""; background: repeating-linear-gradient(90deg, transparent 0 22px, rgba(57,163,245,.1) 23px 24px), repeating-linear-gradient(0deg, transparent 0 14px, rgba(57,163,245,.08) 15px 16px); box-shadow: inset 0 0 25px rgba(20,120,225,.17), 0 0 18px rgba(22,122,226,.18); transform: perspective(220px) rotateX(66deg); }
.warehouse-core { top: 55%; width: min(78%, 390px); aspect-ratio: 1; filter: drop-shadow(0 0 13px rgba(48,168,255,.72)) drop-shadow(0 14px 15px rgba(0,7,25,.58)); }
.warehouse-core::after { z-index: -1; right: 4%; bottom: 5%; left: 4%; height: 17%; border-color: rgba(81,202,255,.78); background: radial-gradient(ellipse, rgba(35,148,255,.22), transparent 69%); box-shadow: inset 0 0 21px rgba(48,174,255,.38), 0 0 27px rgba(28,148,255,.46); }
.warehouse-core img { width: 100%; height: 100%; object-fit: contain; mix-blend-mode: normal; mask-image: none; filter: saturate(1.12) contrast(1.07) brightness(1.03) drop-shadow(0 0 7px rgba(73,193,255,.65)); }
.warehouse-scan { right: 14%; left: 14%; }

.health-label.top { width: 172px; height: 84px; overflow: visible; border-color: rgba(82,204,255,.92); background: radial-gradient(ellipse, rgba(10,93,176,.92), rgba(1,26,65,.95)); box-shadow: inset 0 7px 17px rgba(138,224,255,.1), inset 0 -12px 20px rgba(0,9,35,.55), 0 8px 9px rgba(0,5,22,.48), 0 0 30px rgba(35,159,255,.48); transform: translateX(-50%) perspective(180px) rotateX(7deg); }
.health-label.top::before, .health-satellite::before { position: absolute; inset: 5px; border: 1px solid rgba(112,218,255,.27); border-radius: inherit; content: ""; pointer-events: none; }
.health-glyph, .satellite-glyph { display: grid; place-items: center; color: #91e7ff; font-style: normal; background: linear-gradient(145deg, rgba(88,210,255,.32), rgba(3,37,83,.82)); box-shadow: inset 0 1px 0 rgba(255,255,255,.28), inset 0 -7px 11px rgba(0,8,37,.48), 0 0 12px rgba(39,172,255,.55); clip-path: polygon(50% 0, 92% 25%, 92% 75%, 50% 100%, 8% 75%, 8% 25%); }
.health-glyph { position: absolute; top: 18px; left: 13px; width: 34px; height: 39px; }
.health-caption { top: 12px !important; left: 53px; color: #c2e8ff !important; font-size: 9px !important; font-weight: 720; }
.health-label.top strong { margin-left: 28px; color: #8de7ff; font-size: 39px; font-weight: 800; text-shadow: 0 0 13px #168fff; }
.health-label.top em { left: 53px; color: #7df0c1; font-size: 8px; font-weight: 700; }
.health-satellite { width: 132px; height: 75px; padding-left: 25px; border-color: rgba(78,194,255,.82); background: radial-gradient(ellipse, rgba(12,91,170,.92), rgba(1,24,57,.96)); box-shadow: inset 0 5px 14px rgba(118,219,255,.08), inset 0 -13px 18px rgba(0,8,31,.56), 0 8px 10px rgba(0,5,20,.42), 0 0 22px rgba(28,145,242,.36); transform: perspective(170px) rotateX(8deg); }
.satellite-bottom { transform: translateX(-50%) perspective(170px) rotateX(8deg); }
.satellite-glyph { position: absolute; top: 18px; left: 11px; width: 31px; height: 36px; }
.health-satellite small { top: 11px; left: 49px; color: #a8d5ef; font-size: 8px; font-weight: 680; }
.health-satellite strong { margin: 14px 0 0 18px; font-size: 27px; font-weight: 800; text-shadow: 0 0 11px currentColor; }
.health-satellite em { margin-top: 24px; font-size: 8px; font-weight: 700; }

.tech-symbol { position: relative; display: grid; width: 58px; height: 62px; place-items: center; flex: 0 0 auto; overflow: visible; color: #5ee7ff; font-style: normal; background: linear-gradient(145deg, rgba(61,205,255,.3), rgba(2,30,72,.9)); box-shadow: inset 0 2px 0 rgba(219,248,255,.25), inset 0 -13px 18px rgba(0,8,35,.6), 0 10px 10px rgba(0,5,21,.5), 0 0 20px rgba(38,184,255,.42); clip-path: polygon(50% 0, 92% 24%, 92% 76%, 50% 100%, 8% 76%, 8% 24%); transform: perspective(130px) rotateX(8deg); }
.tech-symbol::before { position: absolute; inset: 7px; border: 1px solid currentColor; content: ""; opacity: .5; clip-path: inherit; }
.tech-symbol svg { position: relative; z-index: 1; filter: drop-shadow(0 0 7px currentColor); }
.priority-symbol { color: #ffb443; background: linear-gradient(145deg, rgba(255,172,54,.32), rgba(48,22,3,.92)); box-shadow: inset 0 2px 0 rgba(255,240,196,.22), inset 0 -13px 18px rgba(25,7,0,.62), 0 10px 10px rgba(0,5,21,.5), 0 0 20px rgba(255,151,38,.38); }
.value-body { grid-template-columns: 116px 1fr; }
.value-gem { display: grid; width: 108px; height: 104px; overflow: hidden; place-items: center; align-self: center; justify-self: center; border: 0; border-radius: 0; background: transparent; box-shadow: none; clip-path: none; transform: translateX(-12px); }
.value-gem::before, .value-gem::after { display: none; content: none; }
.readiness-ring { transform: perspective(150px) rotateX(7deg); box-shadow: inset 0 2px 0 rgba(220,255,251,.22), inset 0 -9px 14px rgba(0,23,42,.42), 0 10px 10px rgba(0,5,21,.45), 0 0 22px rgba(61,222,209,.34); }
.warning-grid > div > svg, .action-buttons button > svg { filter: drop-shadow(0 0 8px currentColor); box-shadow: inset 0 1px 0 rgba(255,255,255,.2), inset 0 -8px 13px rgba(0,8,32,.5), 0 7px 9px rgba(0,5,20,.45), 0 0 14px currentColor; }

.bucket-list button span, .bucket-list button b, .warehouse-bars header strong, .warehouse-bars footer b { font-size: 10px; font-weight: 700; }
.bucket-list button small, .bucket-list button em, .warehouse-bars header span, .warehouse-bars footer em { color: #8fb0c8; font-size: 8.5px; font-weight: 600; }
.distribution-panel > footer span { font-size: 10px; font-weight: 680; }
.warning-grid small { color: #b7d0e2; font-size: 9px; font-weight: 650; }.warning-grid em { color: #7d9db5; font-size: 8px; font-weight: 600; }
.owner-head { color: #8bb0cb; font-size: 8px; font-weight: 700; }.owner-row, .owner-row strong, .owner-row small { color: #a9c7db; font-size: 9px; font-weight: 620; }.owner-row strong { color: #d9effb; font-weight: 720; }
.readiness-list p, .priority-score li, .value-body dt { color: #9bb9ce; font-size: 8.5px; font-weight: 620; }
.readiness-list > small { color: #7697ae; font-size: 7.5px; }.priority-score > div span { color: #c49558; font-size: 8px; font-weight: 680; }.priority-score li b, .value-body dd { color: #d9f2ff; font-size: 9.5px; font-weight: 720; }
.action-buttons strong { color: #eff9ff; font-size: 10px; font-weight: 720; }.action-buttons small { color: #87a9bf; font-size: 8px; font-weight: 600; }
.aging-footer-note { color: #7298b3; font-size: 8px; font-weight: 600; }

/* 1920 × 1080 wallboard visibility pass: stronger hierarchy for bright display environments. */
.aging-page {
  --wallboard-copy: #d7ecfa;
  --wallboard-muted: #a8c8df;
  --wallboard-bright: #f4fbff;
}
.aging-page button,
.aging-page label,
.aging-page span,
.aging-page small,
.aging-page strong,
.aging-page b,
.aging-page em,
.aging-page dt,
.aging-page dd {
  text-shadow: 0 1px 2px rgba(0, 7, 22, .78);
}
.panel-number {
  color: var(--wallboard-bright);
  font-size: 22px;
  font-weight: 800;
  letter-spacing: .02em;
  text-shadow: 0 0 12px rgba(47, 169, 255, .88), 0 1px 2px rgba(0, 5, 18, .9);
}
.panel-heading button,
.unit-note,
.source-note {
  color: #c5e7fb;
  font-size: 11px;
  font-weight: 750;
}
.aging-version { color: #d7eeff; font-size: 13px; font-weight: 750; }
.aging-title-block p { color: #9bc6e8; font-size: 12px; font-weight: 700; }
.aging-live small { color: #a7c7df; font-size: 10px; font-weight: 650; }
.aging-live small b { color: #d3eafb; font-weight: 750; }
.aging-live span { color: #e0f5ff; font-size: 10.5px; font-weight: 750; }
.aging-filters label span { color: #adcbe0; font-size: 10.5px; font-weight: 700; }
.aging-filters select { color: #e0f1fc; font-size: 11px; font-weight: 680; }
.aging-date b { color: #d5ecfa; font-size: 10px; font-weight: 720; }
.aging-kpi span { color: #d2e6f4; font-size: 13px; font-weight: 750; }
.aging-kpi strong { color: #fff; font-size: 30px; font-weight: 820; }
.aging-kpi em { color: #c3dded; font-size: 11px; font-weight: 700; }
.aging-kpi small { color: color-mix(in srgb, var(--tone) 78%, #d7efff); font-size: 11px; font-weight: 700; }

.aging-donut small,
.aging-donut em { color: #b8d5e8; font-size: 10px; font-weight: 700; }
.aging-donut strong { color: #f5fbff; font-size: 19px; font-weight: 800; }
.bucket-list button span,
.bucket-list button b { color: #d6ebf8; font-size: 12px; font-weight: 750; }
.bucket-list button span small,
.bucket-list button em { color: #9fc2da; font-size: 10px; font-weight: 680; }
.distribution-panel > footer span { color: #9bd6fc; font-size: 11px; font-weight: 750; }
.distribution-panel > footer b { font-size: 13px; font-weight: 800; }

.risk-table-head { color: #d4ebf8; font-size: 12px; font-weight: 800; }
.risk-scroll-row { color: #d4e7f2; font-size: 11px; font-weight: 700; }
.risk-scroll-row strong { color: #f7fcff; font-size: 13px; font-weight: 800; }
.risk-scroll-row small { color: #abc8da; font-size: 9px; font-weight: 680; }
.risk-scroll-row b { font-size: 13px; font-weight: 820; }
.risk-scroll-row .risk-level { min-width: 58px; font-size: 9px; font-weight: 800; }

/* Stretch analytical content to the bottom rail instead of leaving a dead strip. */
.distribution-panel,
.warehouse-panel,
.heat-panel {
  display: flex;
  flex-direction: column;
}
.distribution-body {
  min-height: 0;
  flex: 1;
}
.distribution-body .bucket-list {
  align-self: stretch;
  grid-template-rows: repeat(6, minmax(0, 1fr));
}
.distribution-body .bucket-list button { min-height: 0; }
.warehouse-bars {
  min-height: 0;
  flex: 1;
  grid-template-rows: repeat(3, minmax(0, 1fr));
  align-content: stretch;
}
.heat-matrix {
  min-height: 0;
  flex: 1;
  grid-template-rows: repeat(5, minmax(28px, 1fr));
  align-content: stretch;
}

/* Center the score in its ellipse and keep its unit/status on the same baseline. */
.health-label.top {
  display: grid;
  width: 194px;
  height: 92px;
  grid-template-columns: 1fr;
  grid-template-rows: 20px 48px;
  align-content: center;
  align-items: center;
  justify-items: center;
  padding: 8px 12px;
  overflow: visible;
}
.health-glyph {
  position: absolute;
  top: 28px;
  left: 15px;
  width: 34px;
  height: 39px;
}
.health-caption,
.health-label.top span {
  position: static !important;
  grid-column: 1;
  grid-row: 1;
  align-self: center;
  color: #e1f5ff !important;
  font-size: 11px !important;
  font-weight: 800;
  line-height: 1;
  white-space: nowrap;
}
.health-score-line {
  display: flex;
  width: 100%;
  grid-column: 1;
  grid-row: 2;
  align-items: baseline;
  justify-content: center;
  gap: 5px;
  padding-left: 4px;
}
.health-score-line strong,
.health-label.top .health-score-line strong {
  margin: 0;
  color: #9beaff;
  font-size: 42px;
  font-weight: 850;
  line-height: 1;
}
.health-score-line em,
.health-label.top .health-score-line em {
  position: static;
  margin: 0;
  color: #8affce;
  font-size: 10px;
  font-weight: 780;
  line-height: 1;
  white-space: nowrap;
}
.health-satellite small { color: #d0e7f6; font-size: 10px; font-weight: 750; }
.health-satellite strong { font-size: 31px; font-weight: 840; }
.health-satellite em { color: #b4cfdf; font-size: 10px; font-weight: 750; }
.health-rule { color: #acd2ec; font-size: 11px; font-weight: 700; }
.health-rule strong { color: #e0f3ff; font-size: 12px; font-weight: 780; }

.warning-grid small { color: #e0eef6; font-size: 12px; font-weight: 780; }
.warning-grid strong { font-size: 23px; font-weight: 820; }
.warning-grid em { color: #b7cfdd; font-size: 10px; font-weight: 700; }
.warehouse-bars header strong,
.warehouse-bars footer b { color: #f3fbff; font-size: 14px; font-weight: 800; }
.warehouse-bars header span,
.warehouse-bars footer em { color: #c2d9e7; font-size: 12px; font-weight: 700; }
.heat-matrix > strong,
.heat-matrix > span,
.heat-corner { color: #cbe2ef; font-size: 12px; font-weight: 750; }
.heat-matrix > div:not(.heat-corner) { color: #f3fbff; font-size: 14px; font-weight: 800; }
.heat-legend span { color: #bfd7e6; font-size: 11px; font-weight: 720; }
.owner-head { color: #cae2ef; font-size: 12px; font-weight: 780; }
.owner-row,
.owner-row small { color: #d4e7f1; font-size: 12px; font-weight: 700; }
.owner-row strong { color: #f5fbff; font-size: 12.5px; font-weight: 800; }

.readiness-ring strong { font-size: 25px; font-weight: 820; }
.readiness-ring small { color: #a9c7d9; font-size: 9px; font-weight: 700; }
.readiness-list p { color: #bed7e6; font-size: 11px; font-weight: 700; }
.readiness-list p b { color: #8ff1df; font-size: 12px; font-weight: 800; }
.readiness-list > small { color: #95b5c9; font-size: 8.5px; font-weight: 650; }

/* A horizontal icon/score lockup prevents the P1 label from falling below the card. */
.priority-score {
  height: calc(100% - 38px);
  grid-template-columns: minmax(180px, .95fr) minmax(0, 1.2fr);
  align-items: stretch;
  gap: 18px;
  padding: 8px 18px 10px;
  overflow: hidden;
}
.priority-score > div {
  display: grid;
  min-width: 0;
  grid-template-columns: 64px minmax(0, 1fr);
  grid-template-rows: auto auto;
  align-content: center;
  align-items: center;
  justify-items: start;
  column-gap: 13px;
  row-gap: 4px;
}
.priority-score .priority-symbol {
  grid-column: 1;
  grid-row: 1 / 3;
  width: 60px;
  height: 66px;
}
.priority-score > div strong {
  grid-column: 2;
  grid-row: 1;
  align-self: center;
  margin: 0;
  color: #ffd48c;
  font-size: 34px;
  font-weight: 850;
  line-height: 1;
  text-shadow: 0 0 12px rgba(255, 163, 44, .62), 0 1px 2px #160900;
}
.priority-score > div span {
  grid-column: 2;
  grid-row: 2;
  align-self: center;
  margin: 0;
  color: #ffd79b;
  font-size: 11px;
  font-weight: 780;
  line-height: 1.2;
  white-space: nowrap;
}
.priority-score ul { align-self: center; min-width: 0; }
.priority-score li { color: #bcd7e8; padding-block: 7px; font-size: 11px; font-weight: 700; }
.priority-score li b { color: #f1f9ff; font-size: 12px; font-weight: 800; }

.value-body dt { color: #b8d4e5; font-size: 11px; font-weight: 700; }
.value-body dd { color: #90efff; font-size: 13px; font-weight: 800; }
.aging-footer-note { color: #a8c8dc; font-size: 10px; font-weight: 680; }

/* Original package-search emblem, polished with restrained depth and a scanning highlight. */
.aging-title-block {
  position: absolute;
  top: 50%;
  left: 50%;
  display: block;
  transform: translate(-50%, -50%);
}
.aging-title-block h2 { margin: 0; }
.aging-emblem {
  position: absolute;
  top: 50%;
  right: calc(100% + 16px);
  isolation: isolate;
  overflow: visible;
  border-color: rgba(103, 210, 255, .9);
  color: #c7f4ff;
  background:
    linear-gradient(145deg, rgba(64, 181, 255, .25), transparent 42%),
    radial-gradient(circle at 50% 44%, rgba(61, 190, 255, .26), transparent 55%),
    linear-gradient(145deg, rgba(13, 91, 170, .84), rgba(2, 26, 66, .97));
  box-shadow:
    inset 0 1px 0 rgba(224, 251, 255, .52),
    inset 0 -13px 22px rgba(0, 11, 45, .68),
    0 8px 14px rgba(0, 5, 27, .58),
    0 0 21px rgba(38, 168, 255, .55);
  transform: translateY(-50%);
}
.aging-emblem:hover { transform: translateY(-50%) scale(1.04); }
.aging-emblem > img { position: absolute; z-index: 2; inset: -7px; width: calc(100% + 14px); height: calc(100% + 14px); max-width: none; object-fit: contain; filter: drop-shadow(0 0 7px rgba(70, 181, 255, .48)); }
.aging-emblem::before,
.aging-emblem::after {
  position: absolute;
  content: "";
  pointer-events: none;
  clip-path: inherit;
}
.aging-emblem::before {
  z-index: -1;
  inset: -5px;
  border: 1px solid rgba(73, 183, 255, .28);
  opacity: .68;
}
.aging-emblem::after {
  z-index: 3;
  top: 8px;
  right: 14px;
  left: 14px;
  height: 16px;
  background: linear-gradient(180deg, rgba(235, 253, 255, .43), transparent);
  filter: blur(.4px);
}
.aging-emblem-frame {
  position: absolute;
  z-index: 1;
  inset: 7px;
  margin: 0;
  border: 1px solid rgba(144, 231, 255, .45);
  background: radial-gradient(circle, rgba(68, 192, 255, .11), transparent 68%);
  box-shadow: inset 0 0 15px rgba(70, 197, 255, .18), 0 0 8px rgba(55, 181, 255, .22);
  clip-path: inherit;
}
.aging-emblem-icon {
  position: relative;
  z-index: 2;
  color: #d7f8ff;
  stroke-width: 1.8;
  filter: drop-shadow(0 0 4px rgba(174, 241, 255, .9)) drop-shadow(0 0 9px rgba(53, 186, 255, .72));
}
.aging-emblem-scan {
  position: absolute;
  z-index: 4;
  right: 12px;
  left: 12px;
  height: 1px;
  margin: 0;
  opacity: .55;
  background: linear-gradient(90deg, transparent, #d7fbff, transparent);
  box-shadow: 0 0 6px #49cfff;
  animation: emblem-scan 3.2s ease-in-out infinite;
}
.aging-emblem { border: 0; background: transparent; box-shadow: none; clip-path: none; filter: none; animation: none; }
.aging-emblem::before, .aging-emblem::after { display: none; content: none; }

/* Compact stacked aging comparison, matched to the reference warehouse-risk panel. */
.warehouse-panel .panel-heading .unit-note { color: #a9c8dc; font-size: 10px; cursor: default; }
.warehouse-bars {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  gap: 4px;
  padding: 7px 10px 5px;
}
.warehouse-table-head,
.warehouse-risk-row {
  display: grid;
  grid-template-columns: minmax(92px, .9fr) minmax(105px, 1.35fr) 64px 60px;
  align-items: center;
  column-gap: 8px;
}
.warehouse-table-head { min-height: 20px; flex: 0 0 20px; color: #8eabc0; font-size: 10px; font-weight: 750; }
.warehouse-table-head span { grid-column: 1 / 3; }
.warehouse-table-head b { color: #9db8cb; font-size: 10px; text-align: center; }
.warehouse-risk-row { min-height: 30px; flex: 1 1 30px; }
.warehouse-identity { display: flex; min-width: 0; align-items: center; gap: 7px; }
.warehouse-identity strong {
  display: grid;
  width: 31px;
  height: 25px;
  flex: 0 0 31px;
  place-items: center;
  border-radius: 3px;
  color: #dffbff;
  font: 800 11px/1 "Bahnschrift", sans-serif;
  background: linear-gradient(135deg, #12b599, #2585d9);
  box-shadow: inset 0 0 9px rgba(154, 255, 239, .22), 0 0 7px rgba(42, 188, 255, .2);
}
.warehouse-risk-row:nth-of-type(4n) .warehouse-identity strong { background: linear-gradient(135deg, #df8d30, #e04f43); }
.warehouse-risk-row:nth-of-type(5n) .warehouse-identity strong { background: linear-gradient(135deg, #35a7a0, #3f76c7); }
.warehouse-identity span { overflow: hidden; color: #d6e8f3; font-size: 11px; font-weight: 740; text-overflow: ellipsis; white-space: nowrap; }
.warehouse-risk-row .warehouse-track {
  display: flex;
  width: 100%;
  height: 19px;
  overflow: hidden;
  border: 1px solid rgba(54, 137, 207, .32);
  border-radius: 1px;
  background: rgba(17, 58, 104, .68);
  box-shadow: inset 0 0 8px rgba(20, 94, 169, .36);
}
.warehouse-risk-row .warehouse-track i { min-width: 0; height: 100%; box-shadow: inset -1px 0 rgba(255,255,255,.12), 0 0 6px currentColor; }
.warehouse-stagnant { color: #eaf7ff; font: 800 12px/1 "Bahnschrift", sans-serif; text-align: center; }
.warehouse-risk-row > em { color: #eaf7ff; font: normal 800 11px/1 "Bahnschrift", sans-serif; text-align: center; }
.warehouse-legend { display: flex; min-height: 24px; flex: 0 0 24px; align-items: center; justify-content: space-between; gap: 7px; margin-top: 2px; padding-top: 3px; border-top: 1px solid rgba(60, 143, 207, .18); }
.warehouse-legend span { display: inline-flex; align-items: center; gap: 4px; color: #a9c3d5; font-size: 8px; font-weight: 700; white-space: nowrap; }
.warehouse-legend i { width: 8px; height: 8px; flex: 0 0 8px; border-radius: 50%; box-shadow: 0 0 5px currentColor; }

/* Higher-legibility spacing for the high-risk material ticker. */
.risk-table-head { height: 31px; flex-basis: 31px; padding-inline: 10px; font-size: 13px; }
.risk-table-head,
.risk-scroll-row { column-gap: 8px; }
.risk-scroll-row {
  height: var(--risk-row-height, 42px);
  padding: 4px 10px;
  border-bottom-color: rgba(83, 159, 216, .24);
  color: #deedf6;
  font-size: 12px;
  line-height: 1.2;
}
.risk-scroll-row strong { font-size: 14px; line-height: 1.15; }
.risk-scroll-row small { margin-top: 3px; font-size: 10px; line-height: 1.1; }
.risk-scroll-row b { font-size: 14px; }
.risk-scroll-row .risk-level { min-width: 64px; padding: 4px 7px; font-size: 10px; }

/* Five-by-five impact/frequency heat map matched to the reference cockpit. */
.risk-heat-layout {
  display: grid;
  min-height: 0;
  flex: 1;
  height: calc(100% - 34px);
  grid-template-columns: 23px 30px minmax(255px, 1.78fr) minmax(100px, .52fr);
  grid-template-rows: minmax(0, 1fr) 22px 17px;
  gap: 3px 5px;
  align-items: stretch;
  padding: 5px 5px 3px;
  transform: translateY(5px);
}
.heat-panel { display: flex; flex-direction: column; }
.heat-panel > .panel-heading { flex: 0 0 34px; }
.heat-panel > .risk-heat-layout { flex: 1; }
.risk-heat-y-title {
  display: grid;
  grid-row: 1;
  place-items: center;
  color: #a9c5d9;
  font-size: 10px;
  font-weight: 760;
  line-height: 1.2;
  writing-mode: vertical-rl;
  letter-spacing: .18em;
}
.risk-heat-y-title::after { margin-top: 3px; color: #6d94b2; content: "↓"; }
.risk-heat-y-labels { display: grid; grid-row: 1; grid-template-rows: repeat(5, 1fr); }
.risk-heat-y-labels span { display: grid; place-items: center; color: #a9c2d4; font-size: 11px; font-weight: 740; }
.risk-heat-grid {
  display: grid;
  grid-row: 1;
  grid-template-columns: repeat(5, 1fr);
  grid-template-rows: repeat(5, 1fr);
  gap: 3px;
  padding: 3px;
  border: 1px solid rgba(75, 157, 215, .22);
  background: rgba(3, 23, 51, .6);
  box-shadow: inset 0 0 16px rgba(22, 103, 184, .15);
}
.risk-heat-grid > div {
  position: relative;
  display: grid;
  min-width: 0;
  min-height: 0;
  place-items: center;
  border: 1px solid rgba(255,255,255,.16);
  box-shadow: inset 0 1px 0 rgba(255,255,255,.12), inset 0 0 12px rgba(0,0,0,.14);
}
.risk-cell-low { background: linear-gradient(145deg, #267a51, #17603f); }
.risk-cell-medium { background: linear-gradient(145deg, #efbd3d, #c88b26); }
.risk-cell-high { background: linear-gradient(145deg, #f47b28, #cf4f24); }
.risk-cell-critical { background: linear-gradient(145deg, #eb4d3f, #ba2f35); }
.risk-heat-grid strong { position: relative; z-index: 2; color: #fff; font: 850 14px/1 "Bahnschrift", sans-serif; text-shadow: 0 0 7px rgba(0,0,0,.65); }
.risk-heat-grid .is-peak::after {
  position: absolute;
  z-index: 1;
  width: 31px;
  height: 31px;
  border: 2px solid #fff1e8;
  border-radius: 50% 50% 50% 12%;
  content: "";
  box-shadow: 0 0 8px #ff483d, inset 0 0 9px rgba(255,52,43,.45);
  transform: rotate(-45deg);
}
.risk-heat-legend {
  display: flex;
  grid-row: 1;
  flex-direction: column;
  justify-content: center;
  gap: 12px;
  padding: 8px 4px 8px 7px;
  border: 1px solid rgba(49, 131, 197, .33);
  background: rgba(4, 29, 62, .62);
}
.risk-heat-legend span { display: flex; align-items: center; gap: 8px; color: #c7dce9; font-size: 10px; font-weight: 720; white-space: nowrap; }
.risk-heat-legend i { width: 15px; height: 15px; flex: 0 0 15px; border: 1px solid rgba(255,255,255,.18); }
.risk-heat-x-labels { display: grid; grid-column: 3; grid-row: 2; grid-template-columns: repeat(5, 1fr); }
.risk-heat-x-labels span { display: grid; place-items: center; color: #a9c2d4; font-size: 11px; font-weight: 740; }
.risk-heat-x-title { grid-column: 3; grid-row: 3; align-self: start; justify-self: center; color: #9ab8cc; font-size: 10px; font-weight: 740; text-align: center; letter-spacing: .08em; transform: translateY(-2px); }

/* Roomier owner follow-up rows with a seamless vertical ticker for larger datasets. */
.owner-table {
  display: flex;
  min-height: 0;
  flex-direction: column;
  padding: 5px 12px 7px;
}
.owner-head,
.owner-row {
  grid-template-columns: minmax(78px, 1.45fr) .68fr .58fr .7fr minmax(72px, 1.05fr);
  column-gap: 10px;
  padding-inline: 7px;
}
.owner-head {
  min-height: 30px;
  flex: 0 0 30px;
  color: #d8edf8;
  font-size: 12.5px;
  line-height: 1;
}
.owner-scroll-viewport {
  position: relative;
  min-height: 0;
  flex: 1;
  overflow: hidden;
  mask-image: linear-gradient(to bottom, transparent 0, #000 8%, #000 92%, transparent 100%);
}
.owner-scroll-track { will-change: transform; }
.owner-scroll-track.is-scrolling { animation: owner-ticker var(--owner-scroll-duration) linear infinite; }
.owner-scroll-viewport:hover .owner-scroll-track { animation-play-state: paused; }
.owner-row {
  height: var(--owner-row-height, 36px);
  min-height: var(--owner-row-height, 36px);
  border-bottom-color: rgba(83, 159, 216, .23);
  color: #deedf6;
  font-size: 13px;
  line-height: 1.15;
  background: linear-gradient(90deg, rgba(21, 92, 151, .13), transparent 76%);
}
.owner-row:nth-child(2n) { background: linear-gradient(90deg, rgba(31, 119, 184, .17), rgba(4, 29, 61, .04)); }
.owner-row strong { gap: 6px; color: #f6fcff; font-size: 13.5px; }
.owner-row small { color: #a9d8f1; font-size: 12.5px; }
.owner-row b { color: #ffc15f; text-shadow: 0 0 7px rgba(255, 162, 52, .4); }
.owner-row em { color: #ff766e; text-shadow: 0 0 7px rgba(255, 79, 73, .35); }
@keyframes owner-ticker {
  to { transform: translateY(calc(var(--owner-row-count) * var(--owner-row-height, 36px) * -1)); }
}

/* Glass-cylinder health core: the score and warehouse read as one instrument. */
.health-core-panel {
  isolation: isolate;
  overflow: hidden;
  background:
    radial-gradient(ellipse 74% 50% at 50% 57%, rgba(8, 105, 211, .28), transparent 69%),
    radial-gradient(circle at 50% 48%, rgba(32, 141, 242, .18), transparent 37%),
    linear-gradient(180deg, rgba(3, 25, 54, .99), rgba(1, 13, 31, .99));
}
.health-core-panel::before {
  position: absolute;
  z-index: 0;
  inset: 0;
  content: "";
  pointer-events: none;
  opacity: .78;
  background:
    linear-gradient(90deg, transparent 49.82%, rgba(93, 190, 255, .12) 50%, transparent 50.18%),
    linear-gradient(0deg, transparent 49.82%, rgba(93, 190, 255, .08) 50%, transparent 50.18%),
    repeating-radial-gradient(ellipse at 50% 59%, transparent 0 42px, rgba(42, 139, 224, .12) 43px 44px, transparent 45px 66px);
  mask-image: radial-gradient(ellipse 82% 72% at 50% 56%, #000 34%, rgba(0,0,0,.68) 69%, transparent 96%);
}
.health-stage { isolation: isolate; }
.health-field-lines {
  position: absolute;
  z-index: 0;
  inset: 1% 2% 2%;
  pointer-events: none;
  overflow: hidden;
  opacity: .78;
}
.health-field-lines::before,
.health-field-lines::after {
  position: absolute;
  top: 55%;
  left: 50%;
  width: 96%;
  height: 54%;
  border: 1px solid rgba(48, 156, 244, .18);
  border-radius: 50%;
  content: "";
  transform: translate(-50%, -50%) rotate(-4deg);
  box-shadow: inset 0 0 22px rgba(36, 139, 231, .12), 0 0 17px rgba(23, 115, 210, .12);
}
.health-field-lines::after { width: 83%; height: 44%; border-style: dashed; transform: translate(-50%, -50%) rotate(5deg); }
.health-field-lines i {
  position: absolute;
  top: 55%;
  left: 50%;
  width: 88%;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(70, 174, 252, .2) 22%, rgba(131, 222, 255, .48) 50%, rgba(70, 174, 252, .2) 78%, transparent);
  transform-origin: center;
}
.health-field-lines i:nth-child(1) { transform: translate(-50%, -50%) rotate(14deg); }
.health-field-lines i:nth-child(2) { transform: translate(-50%, -50%) rotate(-14deg); }
.health-field-lines i:nth-child(3) { transform: translate(-50%, -50%) rotate(31deg) scaleX(.82); }
.health-field-lines i:nth-child(4) { transform: translate(-50%, -50%) rotate(-31deg) scaleX(.82); }
.health-cylinder {
  position: absolute;
  z-index: 3;
  top: 10px;
  bottom: 16%;
  left: 50%;
  width: clamp(220px, 45%, 282px);
  border-right: 1px solid rgba(88, 197, 255, .48);
  border-left: 1px solid rgba(88, 197, 255, .48);
  background:
    linear-gradient(90deg, rgba(38, 146, 237, .03), rgba(112, 218, 255, .1) 47%, rgba(166, 235, 255, .13) 50%, rgba(50, 159, 242, .05) 72%, transparent),
    linear-gradient(180deg, rgba(49, 160, 249, .1), transparent 18% 72%, rgba(31, 128, 226, .09));
  box-shadow: inset 19px 0 28px rgba(23, 117, 211, .08), inset -19px 0 28px rgba(50, 165, 250, .07), 0 0 35px rgba(28, 135, 231, .12);
  transform: translateX(-50%);
  pointer-events: none;
}
.health-cylinder::before,
.health-cylinder::after,
.health-cylinder > i,
.health-cylinder > span {
  position: absolute;
  left: 50%;
  width: calc(100% + 2px);
  height: 54px;
  border: 2px solid rgba(106, 216, 255, .78);
  border-radius: 50%;
  content: "";
  transform: translateX(-50%);
}
.health-cylinder::before { top: -27px; background: radial-gradient(ellipse, rgba(71, 184, 255, .12), rgba(7, 51, 101, .06) 62%, transparent 73%); box-shadow: inset 0 0 18px rgba(112, 221, 255, .24), 0 0 19px rgba(42, 165, 255, .38); }
.health-cylinder::after { bottom: -27px; border-color: rgba(69, 178, 255, .65); background: radial-gradient(ellipse, rgba(30, 145, 247, .16), transparent 67%); box-shadow: inset 0 0 19px rgba(58, 174, 255, .24), 0 0 21px rgba(26, 137, 236, .32); }
.health-cylinder > i { top: 21%; height: 42px; border-width: 1px; border-color: rgba(75, 184, 252, .23); }
.health-cylinder > span { bottom: 17%; height: 46px; border-width: 1px; border-color: rgba(75, 184, 252, .2); }
.health-label.top { z-index: 7; top: 17px; border-width: 2px; border-color: rgba(132, 225, 255, .92); background: radial-gradient(ellipse, rgba(12, 88, 167, .55), rgba(2, 27, 65, .78) 70%); box-shadow: inset 0 0 24px rgba(80, 191, 255, .24), 0 0 7px rgba(132, 230, 255, .74), 0 0 28px rgba(28, 152, 255, .42); }
.health-label.top::after {
  position: absolute;
  right: 16px;
  bottom: -9px;
  left: 16px;
  height: 15px;
  border-bottom: 1px solid rgba(106, 214, 255, .52);
  border-radius: 50%;
  content: "";
  pointer-events: none;
}
.warehouse-core { z-index: 5; top: 57%; width: min(72%, 360px); }
.orbit { z-index: 2; border-color: rgba(61, 170, 255, .4); box-shadow: 0 0 11px rgba(41, 151, 244, .17), inset 0 0 11px rgba(48, 158, 247, .1); }
.orbit-a { border-width: 2px; border-color: rgba(74, 194, 255, .58); }
.orbit-b { border-style: dashed; border-color: rgba(59, 162, 246, .35); }
.health-link {
  position: absolute;
  z-index: 3;
  pointer-events: none;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(97, 213, 255, .88), transparent);
  filter: drop-shadow(0 0 4px rgba(77, 198, 255, .8));
  transform-origin: center;
}
.health-link::after { position: absolute; top: -3px; width: 7px; height: 7px; border: 1px solid #a4edff; border-radius: 50%; content: ""; background: #35b6ff; box-shadow: 0 0 8px #40c0ff; }
.link-left { top: 53%; left: 17%; width: 25%; transform: rotate(10deg); }.link-left::after { right: 0; }
.link-right { top: 53%; right: 17%; width: 25%; transform: rotate(-10deg); }.link-right::after { left: 0; }
.link-bottom { bottom: 21%; left: 50%; width: 15%; transform: translateX(-50%) rotate(90deg); }.link-bottom::after { right: 0; }
.health-satellite {
  z-index: 7;
  width: 138px;
  height: 80px;
  padding-left: 25px;
  border: 2px solid rgba(105, 218, 255, .75);
  background: radial-gradient(ellipse, rgba(12, 86, 160, .72), rgba(2, 23, 54, .91) 72%);
  box-shadow: inset 0 0 0 5px rgba(55, 172, 246, .06), inset 0 0 23px rgba(67, 180, 255, .17), 0 0 7px rgba(100, 222, 255, .56), 0 0 23px rgba(27, 143, 239, .35);
}
.health-satellite::after {
  position: absolute;
  inset: -8px 9px;
  border-top: 1px solid rgba(126, 225, 255, .46);
  border-bottom: 1px solid rgba(60, 162, 247, .42);
  border-radius: 50%;
  content: "";
  pointer-events: none;
}
.health-satellite.warning { border-color: rgba(255, 189, 74, .72); box-shadow: inset 0 0 0 5px rgba(255, 178, 44, .05), inset 0 0 22px rgba(255, 160, 32, .12), 0 0 7px rgba(255, 187, 61, .48), 0 0 22px rgba(255, 145, 20, .23); }
.health-satellite.good { border-color: rgba(61, 231, 199, .72); box-shadow: inset 0 0 0 5px rgba(48, 222, 185, .05), inset 0 0 22px rgba(42, 215, 181, .12), 0 0 7px rgba(72, 240, 210, .48), 0 0 22px rgba(30, 210, 176, .2); }
.satellite-left { top: 48%; left: 4%; }.satellite-right { top: 48%; right: 4%; }.satellite-bottom { bottom: 8%; }

/* Fill the owner panel edge-to-edge and move warehouse risk bars left. */
.owner-panel { display: flex; min-height: 0; flex-direction: column; }
.owner-panel > .panel-heading { flex: 0 0 34px; }
.owner-panel > .owner-table {
  width: 100%;
  height: auto;
  min-height: 0;
  flex: 1;
  padding: 0 4px 3px;
}
.owner-head,
.owner-row {
  width: 100%;
  grid-template-columns: minmax(92px, 1.5fr) minmax(54px, .72fr) minmax(48px, .62fr) minmax(52px, .68fr) minmax(84px, 1.08fr);
  column-gap: 12px;
  padding-inline: 10px;
}
.owner-head {
  min-height: 32px;
  flex-basis: 32px;
  border-bottom-color: rgba(87, 175, 235, .34);
  background: linear-gradient(90deg, rgba(20, 84, 142, .16), rgba(6, 38, 78, .06));
}
.owner-scroll-viewport {
  width: 100%;
  border-bottom: 1px solid rgba(68, 153, 216, .16);
}
.owner-row { background: linear-gradient(90deg, rgba(21, 92, 151, .18), rgba(6, 38, 78, .07) 76%, rgba(21, 92, 151, .1)); }
.owner-row:nth-child(2n) { background: linear-gradient(90deg, rgba(31, 119, 184, .22), rgba(4, 29, 61, .08) 58%, rgba(24, 92, 150, .13)); }
.owner-head > span:not(:first-child),
.owner-row > span,
.owner-row > b,
.owner-row > em,
.owner-row > small { text-align: center; }

.warehouse-bars { padding-inline: 5px 9px; }
.warehouse-table-head,
.warehouse-risk-row {
  grid-template-columns: 100px minmax(138px, 1fr) 58px 54px;
  column-gap: 6px;
}
.warehouse-identity { gap: 5px; }
.warehouse-identity strong { width: 29px; flex-basis: 29px; }
/* Final hierarchy: central cylinder is local; three overall indices sit outside it. */
.health-core-panel::before {
  opacity: .95;
  background:
    linear-gradient(90deg, transparent 49.78%, rgba(100, 218, 255, .23) 50%, transparent 50.22%),
    linear-gradient(0deg, transparent 49.78%, rgba(153, 105, 255, .18) 50%, transparent 50.22%),
    repeating-radial-gradient(ellipse at 50% 59%, transparent 0 42px, rgba(45, 170, 255, .22) 43px 44px, transparent 45px 66px);
}
.health-field-lines { opacity: .98; }
.health-field-lines::before { border-color: rgba(56, 194, 255, .42); box-shadow: inset 0 0 22px rgba(36, 139, 231, .22), 0 0 17px rgba(23, 115, 210, .25); }
.health-field-lines::after { border-color: rgba(174, 104, 255, .44); box-shadow: 0 0 13px rgba(139, 78, 255, .25); }
.health-field-lines i { height: 2px; box-shadow: 0 0 8px currentColor; }
.health-field-lines i:nth-child(1) { color: #53d7ff; background: linear-gradient(90deg, transparent, rgba(50,184,255,.44) 18%, #7be6ff 50%, rgba(50,184,255,.44) 82%, transparent); }
.health-field-lines i:nth-child(2) { color: #9b70ff; background: linear-gradient(90deg, transparent, rgba(132,78,255,.4) 18%, #bb92ff 50%, rgba(132,78,255,.4) 82%, transparent); }
.health-field-lines i:nth-child(3) { color: #ffad42; background: linear-gradient(90deg, transparent, rgba(255,145,35,.38) 18%, #ffc264 50%, rgba(255,145,35,.38) 82%, transparent); }
.health-field-lines i:nth-child(4) { color: #3ef0bd; background: linear-gradient(90deg, transparent, rgba(39,220,174,.38) 18%, #7dffd9 50%, rgba(39,220,174,.38) 82%, transparent); }
.health-cylinder {
  top: 12px;
  bottom: 23%;
  width: clamp(228px, 42%, 270px);
  border-right: 2px solid rgba(91, 212, 255, .7);
  border-left: 2px solid rgba(91, 212, 255, .7);
  background:
    repeating-linear-gradient(180deg, transparent 0 36px, rgba(95,203,255,.1) 37px, transparent 38px 49px),
    linear-gradient(90deg, rgba(25,119,218,.04), rgba(76,185,255,.14) 17%, transparent 37%, rgba(176,240,255,.15) 50%, transparent 63%, rgba(81,182,255,.11) 83%, rgba(17,101,199,.03)),
    linear-gradient(180deg, rgba(50,176,255,.16), transparent 18% 72%, rgba(108,76,232,.13));
  box-shadow: inset 23px 0 30px rgba(22,119,216,.13), inset -23px 0 30px rgba(66,181,255,.12), inset 0 0 30px rgba(76,196,255,.09), 0 0 9px rgba(85,216,255,.46), 0 0 39px rgba(28,135,231,.22);
}
.health-cylinder::before { border-color: rgba(127,231,255,.95); background: radial-gradient(ellipse, rgba(64,191,255,.23), rgba(46,83,181,.1) 54%, transparent 73%); box-shadow: inset 0 0 20px rgba(130,231,255,.36), 0 0 8px rgba(149,235,255,.76), 0 0 24px rgba(42,165,255,.5); }
.health-cylinder::after { border-color: rgba(105,207,255,.86); background: radial-gradient(ellipse, rgba(30,145,247,.24), rgba(111,72,220,.1) 47%, transparent 70%); box-shadow: inset 0 0 22px rgba(58,174,255,.33), 0 0 7px rgba(112,219,255,.66), 0 0 25px rgba(95,75,235,.34); }
.health-cylinder > i { border-color: rgba(90,214,255,.38); box-shadow: 0 0 10px rgba(54,183,255,.2); }
.health-cylinder > span { border-color: rgba(178,108,255,.34); box-shadow: 0 0 10px rgba(141,73,255,.18); }
.orbit { border-color: rgba(61,187,255,.6); box-shadow: 0 0 13px rgba(41,168,244,.3), inset 0 0 12px rgba(48,158,247,.18); }
.orbit-a { border-color: rgba(73,214,255,.84); box-shadow: 0 0 10px rgba(54,207,255,.5), inset 0 0 15px rgba(40,158,255,.22); }
.orbit-b { border-color: rgba(177,101,255,.62); box-shadow: 0 0 10px rgba(139,73,255,.32); }
.orbit-c { border-color: rgba(255,174,66,.68); box-shadow: 0 0 10px rgba(255,140,31,.32); }
.health-satellite {
  width: 164px;
  height: 94px;
  padding-left: 31px;
}
.health-satellite::before { inset: 7px; border-color: rgba(149,232,255,.4); }
.health-satellite .satellite-glyph { top: 24px; left: 13px; width: 38px; height: 44px; }
.health-satellite small { top: 13px; left: 57px; color: #f0f9ff; font-size: 12px; font-weight: 820; letter-spacing: .03em; text-shadow: 0 0 7px rgba(114,211,255,.58); }
.health-satellite strong { margin: 18px 0 0 18px; font-size: 38px; font-weight: 880; line-height: 1; }
.health-satellite em { margin-top: 31px; color: #d9edf8; font-size: 12px; font-weight: 800; }
.satellite-left { top: 44%; left: 1%; }
.satellite-right { top: 44%; right: 1%; }
.satellite-bottom { bottom: 10%; }

/* Keep metric typography on an untransformed layer so Chinese labels stay crisp. */
.health-label.top {
  transform: translateX(-50%);
  filter: none;
  backface-visibility: visible;
}
.health-satellite {
  transform: none;
  filter: none;
  backdrop-filter: none;
  animation: none;
  backface-visibility: visible;
}
.satellite-bottom { transform: translateX(-50%); }
.health-caption,
.health-label.top span,
.health-satellite small {
  color: #f4fbff !important;
  font-family: "Microsoft YaHei UI", "Microsoft YaHei", sans-serif;
  font-weight: 800;
  letter-spacing: 0;
  text-shadow: none;
  text-rendering: optimizeLegibility;
  -webkit-font-smoothing: antialiased;
}
.health-caption,
.health-label.top span {
  font-size: 12px !important;
}
.health-satellite small {
  font-size: 13px;
  line-height: 1.2;
}

/* Emphasize the central illustration score and seat it deeper inside the cylinder. */
.health-label.top {
  top: 31px;
  width: 226px;
  height: 108px;
  grid-template-rows: 24px 56px;
  padding: 9px 15px;
}
.health-label.top::before { inset: 6px; }
.health-label.top::after { right: 19px; bottom: -10px; left: 19px; height: 17px; }
.health-glyph { top: 33px; left: 17px; width: 39px; height: 45px; }
.health-caption,
.health-label.top span { font-size: 14px !important; }
.health-score-line { gap: 7px; padding-left: 8px; }
.health-score-line strong,
.health-label.top .health-score-line strong { font-size: 50px; }
.health-score-line em,
.health-label.top .health-score-line em { font-size: 12px; }

/* Wide planetary lanes fill the upper corners without competing with data. */
.planetary-field {
  position: absolute;
  z-index: 1;
  inset: -5% -16% 2%;
  overflow: hidden;
  pointer-events: none;
  opacity: .92;
  mask-image: radial-gradient(ellipse 87% 74% at 50% 50%, #000 35%, rgba(0,0,0,.84) 72%, transparent 100%);
}
.planetary-field::before,
.planetary-field::after,
.planetary-field > i {
  position: absolute;
  top: 40%;
  left: 50%;
  width: 93%;
  height: 49%;
  border: 1px solid rgba(52, 156, 255, .27);
  border-radius: 50%;
  content: "";
  box-shadow: 0 0 8px rgba(39, 147, 255, .15), inset 0 0 11px rgba(39, 147, 255, .1);
  transform: translate(-50%, -50%) rotate(-8deg);
}
.planetary-field::after { width: 108%; height: 61%; border-color: rgba(72, 191, 255, .2); transform: translate(-50%, -50%) rotate(6deg); }
.planetary-field > i:nth-child(1) { width: 76%; height: 38%; border-color: rgba(160, 102, 255, .32); border-style: dashed; transform: translate(-50%, -50%) rotate(13deg); }
.planetary-field > i:nth-child(2) { width: 122%; height: 70%; border-color: rgba(32, 126, 235, .2); transform: translate(-50%, -50%) rotate(-3deg); }
.planetary-field > i:nth-child(3),
.planetary-field > i:nth-child(4) {
  width: 42%;
  height: 20%;
  border-width: 2px 0 0;
  border-color: transparent;
  border-top-color: #ffad38;
  border-radius: 50%;
  box-shadow: 0 -1px 8px rgba(255, 139, 31, .58);
}
.planetary-field > i:nth-child(3) { top: 25%; left: 21%; transform: translate(-50%, -50%) rotate(-8deg); }
.planetary-field > i:nth-child(4) { top: 25%; left: 79%; border-top-color: #4bcdff; box-shadow: 0 -1px 8px rgba(56, 191, 255, .62); transform: translate(-50%, -50%) rotate(8deg); }
.planetary-field > span {
  position: absolute;
  z-index: 2;
  width: 8px;
  height: 8px;
  border: 1px solid rgba(221, 248, 255, .9);
  border-radius: 50%;
  background: #50d8ff;
  box-shadow: 0 0 5px #b8f3ff, 0 0 14px #28b9ff;
  animation: planet-node-pulse 3.2s ease-in-out infinite;
}
.planetary-field > span:nth-of-type(1) { top: 19%; left: 13%; }
.planetary-field > span:nth-of-type(2) { top: 18%; right: 13%; width: 7px; height: 7px; background: #ffae3c; box-shadow: 0 0 5px #ffe0a0, 0 0 14px #ff8d24; animation-delay: -1.1s; }
.planetary-field > span:nth-of-type(3) { top: 39%; right: 5%; width: 5px; height: 5px; background: #a779ff; box-shadow: 0 0 5px #dbc8ff, 0 0 12px #8c55ff; animation-delay: -2.2s; }
.warehouse-core { width: min(84%, 420px); }
@keyframes planet-node-pulse { 0%, 100% { opacity: .55; transform: scale(.82); } 50% { opacity: 1; transform: scale(1.35); } }

/* Crisp orbital rendering: solid cores with tightly controlled glow. */
.health-core-panel::before {
  background:
    linear-gradient(90deg, transparent calc(50% - .5px), rgba(121, 228, 255, .52) 50%, transparent calc(50% + .5px)),
    linear-gradient(0deg, transparent calc(50% - .5px), rgba(174, 124, 255, .36) 50%, transparent calc(50% + .5px)),
    repeating-radial-gradient(ellipse at 50% 59%, transparent 0 42px, rgba(70, 182, 255, .36) 43px, transparent 44px 66px);
}
.health-field-lines::before,
.health-field-lines::after {
  border-width: 1px;
  box-shadow: none;
}
.health-field-lines::before { border-color: rgba(95, 211, 255, .62); }
.health-field-lines::after { border-color: rgba(190, 126, 255, .58); border-style: solid; }
.health-field-lines i {
  height: 1px;
  box-shadow: 0 0 2px currentColor;
}
.health-field-lines i:nth-child(1) { background: linear-gradient(90deg, transparent, #52caff 17%, #c7f7ff 50%, #52caff 83%, transparent); }
.health-field-lines i:nth-child(2) { background: linear-gradient(90deg, transparent, #9a65ff 17%, #e1d2ff 50%, #9a65ff 83%, transparent); }
.health-field-lines i:nth-child(3) { background: linear-gradient(90deg, transparent, #ff9c2f 17%, #ffe09b 50%, #ff9c2f 83%, transparent); }
.health-field-lines i:nth-child(4) { background: linear-gradient(90deg, transparent, #27dca7 17%, #b7ffe9 50%, #27dca7 83%, transparent); }
.orbit,
.orbit-a,
.orbit-b,
.orbit-c { border-width: 1px; border-style: solid; box-shadow: none; filter: none; animation-name: orbit-crisp-pulse; }
.orbit-a { border-width: 2px; border-color: rgba(96, 222, 255, .9); box-shadow: 0 0 3px rgba(67, 203, 255, .5); }
.orbit-b { border-color: rgba(186, 116, 255, .76); }
.orbit-c { border-color: rgba(255, 180, 72, .82); }
.orbit::before { filter: none; }
.planetary-field { opacity: 1; }
.planetary-field::before,
.planetary-field::after,
.planetary-field > i {
  border-width: 1px;
  box-shadow: none;
}
.planetary-field::before { border-color: rgba(85, 188, 255, .58); }
.planetary-field::after { border-color: rgba(71, 179, 255, .44); }
.planetary-field > i:nth-child(1) { border-color: rgba(177, 108, 255, .58); border-style: solid; }
.planetary-field > i:nth-child(2) { border-color: rgba(45, 137, 248, .4); }
.planetary-field > i:nth-child(3),
.planetary-field > i:nth-child(4) { border-top-width: 2px; box-shadow: 0 -1px 2px currentColor; }
.planetary-field > i:nth-child(3) { color: #ffad38; }
.planetary-field > i:nth-child(4) { color: #4bcdff; }
.health-link { height: 1px; filter: none; }
@keyframes orbit-crisp-pulse {
  0%, 100% { opacity: .64; }
  50% { opacity: 1; }
}

/* Keep the central score frameless; let the cylinder itself carry the visual hierarchy. */
.health-label.top {
  border: 0;
  background: transparent;
  box-shadow: none;
  animation: none;
}
.health-label.top::before,
.health-label.top::after { content: none; }

/* Deep-blue double rim for the cylinder crown. */
.health-cylinder {
  top: 30px;
  border-right-color: rgba(40, 128, 226, .92);
  border-left-color: rgba(40, 128, 226, .92);
  background:
    linear-gradient(90deg, rgba(16, 73, 164, .11), rgba(40, 135, 232, .22) 18%, transparent 39%, rgba(63, 164, 255, .24) 50%, transparent 62%, rgba(35, 121, 221, .2) 82%, rgba(9, 54, 139, .1)),
    linear-gradient(180deg, rgba(30, 116, 222, .22), rgba(5, 35, 102, .08) 24% 70%, rgba(24, 96, 205, .2));
  box-shadow: inset 22px 0 29px rgba(20, 88, 195, .21), inset -22px 0 29px rgba(45, 143, 242, .2), 0 0 20px rgba(16, 78, 181, .3);
}
.health-label.top { top: 31px; }
.health-score-line { transform: translateX(16px); }
.health-label.top .health-glyph { transform: translateX(8px); }
.health-satellite .satellite-glyph { transform: translateX(8px); }
.health-satellite strong {
  display: inline-block;
  transform: translateX(-9px);
}
.warning-grid > div > svg {
  width: 32px;
  height: 32px;
  flex: 0 0 32px;
  stroke-width: 1.8;
}
.warning-grid > div > span { margin-left: 10px; }
.health-cylinder::before {
  top: -49px;
  width: calc(100% + 24px);
  height: 102px;
  border: 2px solid rgba(25, 91, 190, 1);
  background:
    radial-gradient(ellipse at 50% 62%, rgba(42, 132, 235, .42), rgba(10, 54, 137, .3) 53%, rgba(2, 22, 68, .22) 70%, transparent 74%),
    linear-gradient(180deg, rgba(8, 40, 104, .18) 0 44%, rgba(31, 101, 204, .4) 72%, rgba(5, 31, 94, .58) 100%);
  box-shadow:
    0 0 0 3px rgba(4, 31, 91, 1),
    0 0 0 5px rgba(23, 77, 166, .9),
    inset 0 0 0 2px rgba(58, 140, 239, .72),
    inset 0 -10px 17px rgba(27, 101, 213, .55),
    inset 0 11px 18px rgba(3, 22, 70, .62),
    0 5px 10px rgba(5, 22, 65, .55),
    0 0 14px rgba(20, 89, 199, .52);
  transform: translateX(-50%) perspective(300px) rotateX(9deg);
  transform-origin: 50% 100%;
}
@keyframes emblem-scan {
  0%, 12% { top: 16px; opacity: 0; }
  28% { opacity: .9; }
  72% { opacity: .7; }
  88%, 100% { top: 48px; opacity: 0; }
}

@media (prefers-reduced-motion: reduce) {
  .aging-page, .aging-hero::after, .aging-emblem, .aging-live span::after, .aging-kpi-icon, .aging-kpi::after,
  .aging-panel::before, .aging-panel::after, .health-core-panel::after, .panel-heading::after, .panel-number::after,
  .orbit, .orbit::before, .health-label.top, .warehouse-core, .warehouse-core img, .warehouse-scan, .health-satellite, .aging-emblem-scan,
  .warehouse-track i::after, .readiness-list > i em::after, .risk-scroll-track, .owner-scroll-track { animation: none !important; }
}
</style>

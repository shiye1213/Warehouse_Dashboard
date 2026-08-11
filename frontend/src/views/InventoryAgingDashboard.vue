<script setup>
import { computed, onMounted, ref } from 'vue'
import {
  AlertTriangle,
  ArrowDownRight,
  ArrowRight,
  BadgeCheck,
  Boxes,
  CalendarClock,
  ChartNoAxesColumnIncreasing,
  CheckCircle2,
  CircleDollarSign,
  ClipboardCheck,
  Clock3,
  Download,
  Menu,
  PackageOpen,
  PackageSearch,
  RotateCcw,
  Scale,
  ShieldAlert,
  Sparkles,
  Target,
  UserRoundCheck,
} from 'lucide-vue-next'
import PageState from '../components/PageState.vue'
import inventoryHealthWarehouse from '../assets/inventory-health-warehouse.jpg'
import { registerProjectRefresh } from '../composables/useProjectRefresh'
import { dashboardApi } from '../services/api'

const snapshot = ref(null)
const loading = ref(false)
const error = ref('')
const warehouseFilter = ref('全部仓库')
const categoryFilter = ref('全部物料')
const riskFilter = ref('全部风险')
const selectedBucket = ref('')
const activeSection = ref('overview')

const bucketOrder = ['0-30天', '31-60天', '61-90天', '91-180天', '181-365天', '365天以上']
const bucketColors = ['#3ca7ff', '#54d9d0', '#8bd45f', '#f3c44f', '#f28a32', '#f05252']
const levelOrder = ['关注', '预警', '呆滞', '严重呆滞']
const navItems = [
  { key: 'overview', label: '健康总览', target: 'aging-overview' },
  { key: 'risk', label: '风险清单', target: 'aging-risk' },
  { key: 'owners', label: '责任跟进', target: 'aging-owners' },
  { key: 'rules', label: '规则口径', target: 'aging-rules' },
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
onMounted(load)

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
  return names.map((name) => {
    const rows = filteredBatches.value.filter((item) => item.warehouseName === name)
    const total = rows.reduce((sum, item) => sum + Number(item.inventoryAmount || 0), 0)
    const stagnant = rows.filter((item) => item.isStagnant).reduce((sum, item) => sum + Number(item.inventoryAmount || 0), 0)
    const skuRows = filteredSkus.value.filter((item) => item.warehouseName === name)
    return {
      name,
      total,
      stagnant,
      ratio: total ? stagnant / total : 0,
      severe: skuRows.filter((item) => item.stagnantLevel === '严重呆滞').length,
      health: Math.max(30, Math.round(100 - (total ? stagnant / total : 0) * 360 - skuRows.filter((item) => item.stagnantLevel === '严重呆滞').length * 1.5)),
    }
  }).sort((a, b) => b.ratio - a.ratio)
})

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

const heatCells = computed(() => {
  const names = [...new Set(filteredSkus.value.map((item) => item.warehouseName))]
  const values = names.flatMap((warehouse) => levelOrder.map((level) => ({
    warehouse,
    level,
    count: filteredSkus.value.filter((item) => item.warehouseName === warehouse && item.stagnantLevel === level).length,
  })))
  const max = Math.max(1, ...values.map((item) => item.count))
  return { names, max, values }
})

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
  activeSection.value = 'risk'
  document.getElementById('aging-risk')?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

function scrollToSection(item) {
  activeSection.value = item.key
  document.getElementById(item.target)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
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
  activeSection.value = 'risk'
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
        <span class="aging-version">V4/4 展示版</span>
        <div class="aging-title-block">
          <button type="button" class="aging-emblem" aria-label="打开系统导航" title="打开系统导航" @click="openNavigation">
            <PackageSearch :size="30" />
            <span class="aging-emblem-menu" aria-hidden="true"><Menu :size="11" :stroke-width="2.4" /></span>
            <i />
            <i />
          </button>
          <div><h2>库存健康与呆滞管理</h2><p>INVENTORY HEALTH &amp; AGING MANAGEMENT</p></div>
        </div>
        <div class="aging-live"><small>数据更新时间<br><b>{{ formatDate(snapshot?.meta?.snapshotDate) }} 10:30:00</b></small><span><i /> 实时数据</span></div>
      </header>

      <div class="aging-toolbar" aria-label="看板导航与筛选">
        <nav class="aging-tabs" aria-label="库存健康页面分区">
          <button
            v-for="item in navItems"
            :key="item.key"
            type="button"
            :class="{ active: activeSection === item.key }"
            @click="scrollToSection(item)"
          ><span>{{ item.label }}</span></button>
        </nav>
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
              <header class="panel-heading"><span class="panel-number">1</span><div><h3>库龄结构分析</h3><p>AGING DISTRIBUTION</p></div><button v-if="selectedBucket" @click="selectedBucket = ''">清除 {{ selectedBucket }}</button></header>
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
              <header class="panel-heading"><span class="panel-number">4</span><div><h3>高风险物料清单</h3><p>TOP RISK MATERIALS</p></div><button @click="exportRiskList"><Download :size="13" /> 导出</button></header>
              <div class="risk-table-wrap">
                <table>
                  <thead><tr><th>物料 / 项目</th><th>仓库</th><th>最大库龄</th><th>等级</th><th>责任人</th></tr></thead>
                  <tbody>
                    <tr v-for="item in riskRows.slice(0, 8)" :key="`${item.warehouseId}-${item.sku}`">
                      <td><strong>{{ item.materialName }}</strong><small>{{ item.projectNo }} · {{ item.materialCode }}</small></td>
                      <td>{{ item.warehouseName }}</td>
                      <td><b>{{ item.maxAgeDays }}</b> 天</td>
                      <td><span class="risk-level" :class="levelClass(item.stagnantLevel)">{{ item.stagnantLevel }}</span></td>
                      <td>{{ item.owner }}</td>
                    </tr>
                  </tbody>
                </table>
                <div v-if="!riskRows.length" class="aging-empty"><BadgeCheck :size="18" /> 当前筛选范围内暂无高风险物料</div>
              </div>
            </article>
          </div>

          <div class="aging-column center-column">
            <article class="aging-panel health-core-panel">
              <div class="health-label top"><span>库存健康指数</span><strong>{{ healthScore }}</strong><em>分 · {{ healthLabel }}</em></div>
              <div class="health-stage">
                <div class="orbit orbit-a" /><div class="orbit orbit-b" /><div class="orbit orbit-c" />
                <div class="warehouse-core">
                  <img :src="inventoryHealthWarehouse" alt="立体仓储货架与库存托盘" />
                  <span class="warehouse-scan" aria-hidden="true" />
                </div>
                <div class="health-satellite satellite-left" :class="scoreTone(amountHealth)"><small>金额健康度</small><strong>{{ amountHealth }}</strong><em>分</em></div>
                <div class="health-satellite satellite-right" :class="scoreTone(cycleHealth)"><small>周转健康度</small><strong>{{ cycleHealth }}</strong><em>分</em></div>
                <div class="health-satellite satellite-bottom" :class="scoreTone(structureHealth)"><small>结构健康度</small><strong>{{ structureHealth }}</strong><em>分</em></div>
              </div>
              <div class="health-rule"><Sparkles :size="15" /><span>呆滞判定</span><strong>库龄 &gt; 180 天 且 最近 90 天无出库</strong></div>
            </article>

            <article class="aging-panel warning-panel">
              <header class="panel-heading"><span class="panel-number">5</span><div><h3>预警与异常总览</h3><p>WARNING OVERVIEW</p></div><button @click="scrollToSection(navItems[1])">查看清单 <ArrowRight :size="13" /></button></header>
              <div class="warning-grid">
                <div v-for="item in warningCards" :key="item.label" :class="`warning-${item.tone}`"><component :is="item.icon" :size="21" /><span><small>{{ item.label }}</small><strong>{{ item.value }}</strong><em>{{ item.note }}</em></span></div>
              </div>
            </article>

          </div>

          <div class="aging-column right-column">
            <article class="aging-panel warehouse-panel">
              <header class="panel-heading"><span class="panel-number">2</span><div><h3>仓库风险对比</h3><p>BY STAGNANT AMOUNT</p></div><span class="unit-note">金额单位：万元</span></header>
              <div class="warehouse-bars">
                <div v-for="item in warehouseComparison" :key="item.name">
                  <header><strong>{{ item.name }}</strong><span>健康 {{ item.health }}分 · 严重 {{ item.severe }}项</span></header>
                  <div class="warehouse-track"><i :style="{ width: `${Math.max(2, item.ratio * 100)}%` }" /></div>
                  <footer><b>{{ compactMoney(item.stagnant) }}</b><em>呆滞占比 {{ percent(item.ratio) }}</em></footer>
                </div>
              </div>
            </article>

            <article class="aging-panel heat-panel">
              <header class="panel-heading"><span class="panel-number">3</span><div><h3>风险热力矩阵</h3><p>WAREHOUSE × RISK</p></div></header>
              <div class="heat-matrix" :style="{ '--columns': heatCells.names.length }">
                <div class="heat-corner">等级</div><strong v-for="name in heatCells.names" :key="name">{{ name }}</strong>
                <template v-for="level in levelOrder" :key="level">
                  <span>{{ level }}</span>
                  <div v-for="name in heatCells.names" :key="`${level}-${name}`" :class="`heat-${levelClass(level)}`" :style="{ '--intensity': (heatCells.values.find((item) => item.level === level && item.warehouse === name)?.count || 0) / heatCells.max }">
                    {{ heatCells.values.find((item) => item.level === level && item.warehouse === name)?.count || 0 }}
                  </div>
                </template>
              </div>
              <div class="heat-legend"><span><i class="low" />低</span><span><i class="mid" />中</span><span><i class="high" />高</span></div>
            </article>

            <article id="aging-owners" class="aging-panel owner-panel">
              <header class="panel-heading"><span class="panel-number">6</span><div><h3>责任人任务跟进</h3><p>OWNER FOLLOW-UP</p></div><span class="source-note">处置台账</span></header>
              <div class="owner-table">
                <div class="owner-head"><span>责任人</span><span>待处置</span><span>P1 高</span><span>严重</span><span>涉及金额</span></div>
                <div v-for="item in ownerRows" :key="item.owner" class="owner-row"><strong><UserRoundCheck :size="14" /> {{ item.owner }}</strong><span>{{ item.tasks }}</span><b>{{ item.p1 }}</b><em>{{ item.severe }}</em><small>{{ compactMoney(item.amount) }}</small></div>
              </div>
            </article>
          </div>
        </section>

        <section class="aging-secondary-grid">
          <article class="aging-panel readiness-panel">
            <header class="panel-heading compact"><span class="panel-number">7</span><div><h3>处置准备度</h3><p>DISPOSAL READINESS</p></div></header>
            <div class="readiness-body"><div class="readiness-ring" :style="{ '--progress': `${readiness * 3.6}deg` }"><span><strong>{{ readiness }}%</strong><small>已建档</small></span></div><div class="readiness-list"><p><span>责任人覆盖</span><b>{{ percent(ownerCoverage, 0) }}</b></p><i><em :style="{ width: percent(ownerCoverage, 0) }" /></i><p><span>处置建议覆盖</span><b>{{ percent(actionCoverage, 0) }}</b></p><i><em :style="{ width: percent(actionCoverage, 0) }" /></i><small>完成率需接入“处置跟踪”状态后计算</small></div></div>
          </article>

          <article class="aging-panel action-score-panel">
            <header class="panel-heading compact"><span class="panel-number">8</span><div><h3>处置优先级</h3><p>PRIORITY SCORE</p></div></header>
            <div class="priority-score"><div><Target :size="34" /><strong>{{ p1Count }}</strong><span>P1 高优先级</span></div><ul><li><span>P1 高</span><b>{{ p1Count }} 项</b></li><li><span>P2 中</span><b>{{ p2Count }} 项</b></li><li><span>责任人组</span><b>{{ ownerRows.length }} 组</b></li></ul></div>
          </article>

          <article class="aging-panel value-panel">
            <header class="panel-heading compact"><span class="panel-number">9</span><div><h3>潜在价值挖掘</h3><p>VALUE OPPORTUNITY</p></div></header>
            <div class="value-body"><div class="value-gem"><Scale :size="36" /></div><dl><div><dt>可优先去化金额</dt><dd>{{ compactMoney(p1Amount) }}</dd></div><div><dt>全部呆滞金额</dt><dd>{{ compactMoney(stagnantAmount) }}</dd></div><div><dt>可推动处置 SKU</dt><dd>{{ stagnantSkus.length }} 个</dd></div></dl></div>
          </article>

          <article class="aging-panel action-center-panel">
            <header class="panel-heading compact"><span class="panel-number">10</span><div><h3>行动中心</h3><p>QUICK ACTIONS</p></div></header>
            <div class="action-buttons">
              <button type="button" @click="focusRisk('呆滞')"><ClipboardCheck :size="22" /><span><strong>呆滞处置建议</strong><small>查看推荐方案</small></span></button>
              <button type="button" @click="focusRisk('严重呆滞')"><ArrowDownRight :size="22" /><span><strong>严重呆滞清单</strong><small>优先风险处置</small></span></button>
              <button type="button" @click="scrollToSection(navItems[2])"><UserRoundCheck :size="22" /><span><strong>责任人跟进</strong><small>查看任务归属</small></span></button>
              <button type="button" @click="exportRiskList"><Download :size="22" /><span><strong>导出处置清单</strong><small>生成 CSV 文件</small></span></button>
            </div>
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
.aging-emblem i { position: absolute; inset: 8px; border: 1px solid rgba(86,200,255,.22); clip-path: inherit; }
.aging-emblem i:last-child { inset: 15px; }
.aging-emblem-menu { position: absolute; right: 5px; bottom: 9px; z-index: 2; display: grid; width: 16px; height: 16px; place-items: center; border: 1px solid rgba(142,224,255,.82); border-radius: 50%; color: #d6f5ff; background: #0c5591; box-shadow: 0 0 8px rgba(62,184,255,.75); }
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

/* Fixed 1600 x 900 canvas, uniformly scaled to fit any viewport. */
.aging-page { position: relative; z-index: 1; display: flex; width: 1600px; min-width: 1600px; max-width: none; height: 900px; min-height: 900px; max-height: none; aspect-ratio: 16 / 9; flex: none; flex-direction: column; margin: 0; overflow: hidden; container-type: inline-size; zoom: min(calc(100vw / 1600px), calc(100dvh / 900px)); }
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
.aging-toolbar { position: absolute; top: 54px; right: 0; left: 0; z-index: 32; min-height: 0; height: 43px; flex: none; padding: 4px 20px; border-bottom: 0; background: transparent; backdrop-filter: none; }
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
.panel-heading h3 { color: #e3f2ff; font-size: 13px; }
.panel-heading p { color: #6083a6; font-size: 6px; }
.risk-table-wrap { height: calc(100% - 34px); }
.risk-table-wrap th { height: 24px; font-size: 8px; }
.risk-table-wrap th, .risk-table-wrap td { padding: 4px 6px; }
.risk-table-wrap th, .risk-table-wrap td { padding-block: 3px; }
.risk-table-wrap td small { margin-top: 1px; }
.risk-table-wrap td { font-size: 8px; }
.risk-table-wrap td strong { font-size: 9px; }
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
.aging-secondary-grid { flex-basis: 158px; grid-template-columns: 1.05fr .78fr .88fr 1.22fr; gap: 12px; }
.readiness-ring { width: 88px; height: 88px; box-shadow: 0 0 18px rgba(61,222,209,.28); }
.readiness-ring strong { font-size: 23px; }
.priority-score > div strong { font-size: 29px; }
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
.warehouse-core { animation: warehouse-hover 4.8s ease-in-out infinite; }
.warehouse-core img { animation: warehouse-luminance 4.8s ease-in-out infinite; }
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
@keyframes warehouse-hover { 0%, 100% { margin-top: 3px; } 50% { margin-top: -4px; } }
@keyframes warehouse-luminance { 0%, 100% { filter: saturate(1.05) contrast(1.04) brightness(.94); } 50% { filter: saturate(1.2) contrast(1.08) brightness(1.08); } }
@keyframes warehouse-scan { 0%, 12% { top: 17%; opacity: 0; } 25% { opacity: 1; } 72% { opacity: .8; } 88%, 100% { top: 79%; opacity: 0; } }
@keyframes satellite-signal { 0%, 100% { filter: brightness(.92); } 50% { filter: brightness(1.13); } }
@keyframes progress-sweep { 0%, 28% { left: -45%; opacity: 0; } 45% { opacity: .85; } 68%, 100% { left: 120%; opacity: 0; } }

@media (prefers-reduced-motion: reduce) {
  .aging-page, .aging-hero::after, .aging-emblem, .aging-live span::after, .aging-kpi-icon, .aging-kpi::after,
  .aging-panel::before, .aging-panel::after, .health-core-panel::after, .panel-heading::after, .panel-number::after,
  .orbit, .orbit::before, .health-label.top, .warehouse-core, .warehouse-core img, .warehouse-scan, .health-satellite,
  .warehouse-track i::after, .readiness-list > i em::after { animation: none !important; }
}
</style>

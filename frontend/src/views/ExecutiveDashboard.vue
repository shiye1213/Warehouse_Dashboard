<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  AlertTriangle, ArrowRight, Box, Boxes, CheckCircle2, ClipboardCheck, Clock3,
  Layers3, Menu, PackageCheck, Radio, RefreshCw, RotateCcw, ScanLine,
  ShieldCheck, Snowflake, Truck, Users, Warehouse, Wrench,
} from 'lucide-vue-next'
import PageState from '../components/PageState.vue'
import TrendChart from '../components/TrendChart.vue'
import { formatNumber, formatPercent, useDashboard } from '../composables/useDashboard'

const router = useRouter()
const { snapshot, loading, error, refresh } = useDashboard()
const now = ref(new Date())
let clockTimer

const summary = computed(() => snapshot.value?.summary || {})
const daily = computed(() => snapshot.value?.trend || [])
const latest = computed(() => daily.value[daily.value.length - 1] || {})
const finishedZones = computed(() => (snapshot.value?.zones || []).filter((zone) => zone.warehouse === '成品库'))
const delta = computed(() => summary.value.deltas || {})

const trendSeries = [
  { name: '入库量', key: 'inbound', color: '#28c8ff', area: true },
  { name: '出库量', key: 'outbound', color: '#c5d8ff', symbol: 'diamond' },
]

const metricCards = computed(() => [
  { label: '今日入库量', value: latest.value.inbound, unit: '箱', delta: delta.value.inbound, icon: PackageCheck, tone: 'cyan' },
  { label: '今日出库量', value: latest.value.outbound, unit: '箱', delta: delta.value.outbound, icon: Truck, tone: 'blue' },
  { label: '成品库库存', value: summary.value.occupiedLocations, unit: '库位', delta: .024, icon: Boxes, tone: 'cyan' },
  { label: '发货及时率', value: formatPercent(summary.value.deliveryTimely), unit: '', target: '目标 94%', icon: Clock3, tone: 'blue' },
  { label: '库位占用率', value: formatPercent(summary.value.occupancy), unit: '', delta: delta.value.occupancy, icon: Layers3, tone: 'cyan' },
  { label: '未关闭异常', value: summary.value.openAlerts, unit: '条', delta: delta.value.exceptions, icon: AlertTriangle, tone: 'danger' },
])

const zoneCards = computed(() => {
  const storage = Math.round((finishedZones.value[0]?.occupancy || .88) * 100)
  const staging = Math.round((finishedZones.value[1]?.occupancy || .82) * 100)
  return [
    { code: 'A区', name: '高频出货区', label: '出货', value: 95, icon: Truck, tone: 'normal' },
    { code: 'B区', name: '丁腈手套存储区', label: '库存', value: storage, icon: PackageCheck, tone: 'normal' },
    { code: 'C区', name: 'PE手套存储区', label: '库存', value: staging, icon: Boxes, tone: 'normal' },
    { code: 'D区', name: '待发区', label: '待发', value: 90, icon: Box, tone: 'warning' },
    { code: 'E区', name: '暂存区', label: '占用率', value: 66, icon: Warehouse, tone: 'attention' },
    { code: 'F区', name: '退货区', label: '占用率', value: 43, icon: RotateCcw, tone: 'working' },
    { code: 'G区', name: '复核区', label: '作业中', value: null, icon: ClipboardCheck, tone: 'working' },
    { code: 'H区', name: '冻结区', label: '异常 2', value: null, icon: Snowflake, tone: 'danger' },
  ]
})

const kpis = computed(() => [
  { label: '库存准确率', value: summary.value.inventoryAccuracy || latest.value.inventoryAccuracy, target: .98, tone: 'cyan' },
  { label: '发货及时率', value: summary.value.deliveryTimely, target: .94, tone: 'cyan' },
  { label: '异常关闭率', value: summary.value.exceptionCloseRate, target: .90, tone: 'cyan' },
  { label: '库位占用率', value: summary.value.occupancy, target: .85, tone: 'warning' },
  { label: '订单齐套率', value: .975, target: .96, tone: 'cyan' },
])

const processSteps = computed(() => [
  { label: '入库', value: latest.value.inbound, rate: '100%', icon: PackageCheck },
  { label: '上架', value: Math.max(0, Number(latest.value.inbound || 0) - 32), rate: '94%', icon: Layers3 },
  { label: '拣选', value: latest.value.picking, rate: '97%', icon: ScanLine },
  { label: '复核', value: Math.max(0, Number(latest.value.outbound || 0) - 20), rate: '95%', icon: ClipboardCheck },
  { label: '出库', value: latest.value.outbound, rate: '100%', icon: Truck },
])

const skuRanking = [
  { name: '丁腈手套 A100（蓝色）', value: 3860 },
  { name: 'PE 手套 B220（透明）', value: 3120 },
  { name: '丁腈检查手套 C310', value: 2680 },
  { name: '一次性防护手套 D450', value: 2240 },
  { name: '乳胶检查手套 E520', value: 1980 },
]

const exceptionTypes = [
  { label: '发货延迟', value: 3, color: '#ff6a5f' },
  { label: '标签异常', value: 2, color: '#ffae38' },
  { label: '数量差异', value: 2, color: '#f1d15c' },
  { label: '库位异常', value: 2, color: '#45b178' },
  { label: '包装破损', value: 2, color: '#4384ef' },
]

const activityItems = [
  { time: '10:21', icon: Truck, tone: 'cyan', title: '订单 CXO260804015', detail: '丁腈手套 A100 · 扫码出库', state: '已完成' },
  { time: '10:18', icon: AlertTriangle, tone: 'warning', title: 'PE手套 B220 · 含量校验异常', detail: '质量复核队列', state: '待处理' },
  { time: '10:12', icon: ClipboardCheck, tone: 'cyan', title: '订单 CXO260804003', detail: '一次性检查手套 C310 · 复核完成', state: '已完成' },
  { time: '10:05', icon: Snowflake, tone: 'blue', title: '冻结区温度 -18.2°C', detail: '区域设备运行稳定', state: '正常' },
]

const closeRate = computed(() => Math.round((summary.value.exceptionCloseRate || .92) * 100))

function formatDelta(value) {
  const number = Number(value || 0) * 100
  return (number >= 0 ? '+' : '') + number.toFixed(1) + '%'
}

function open(path) {
  router.push(path)
}

function openNavigation() {
  window.dispatchEvent(new CustomEvent('warehouse:open-navigation'))
}

onMounted(() => {
  clockTimer = window.setInterval(() => { now.value = new Date() }, 1000)
})
onBeforeUnmount(() => window.clearInterval(clockTimer))
</script>

<template>
  <div class="page finished-board-page">
    <PageState :loading="loading && !snapshot" :error="error" @retry="refresh">
      <section class="finished-board" aria-label="成品库智慧运营驾驶舱">
        <header class="finished-header">
          <div class="finished-brand">
            <button class="finished-menu" type="button" aria-label="打开主导航" @click="openNavigation"><Menu :size="19" /></button>
            <div class="finished-logo"><Warehouse :size="26" /></div>
            <div><strong>INTCO</strong><span>WAREHOUSE</span></div>
          </div>
          <div class="finished-title">
            <p>FINISHED GOODS INTELLIGENT OPERATION</p>
            <h1>成品库智慧运营驾驶舱</h1>
            <span>安全 · 品质 · 高效 · 协同</span>
          </div>
          <div class="finished-clock">
            <div><span>当前时间</span><strong>{{ now.toLocaleString('zh-CN', { hour12: false }) }}</strong></div>
            <i />
            <div><span>数据更新</span><strong>{{ summary.latestDate || '—' }}</strong></div>
            <button type="button" aria-label="刷新数据" :class="{ spinning: loading }" @click="refresh"><RefreshCw :size="15" /></button>
            <em><b />系统在线</em>
          </div>
        </header>

        <div class="metric-ribbon">
          <button v-for="card in metricCards" :key="card.label" class="metric-tile" :class="'is-' + card.tone" type="button" @click="open(card.label.includes('异常') ? '/exceptions' : card.label.includes('库位') || card.label.includes('库存') ? '/zones' : '/operations')">
            <span class="metric-icon"><component :is="card.icon" :size="31" :stroke-width="1.6" /></span>
            <span class="metric-copy">
              <small>{{ card.label }}</small>
              <strong>{{ typeof card.value === 'number' ? formatNumber(card.value) : card.value || '—' }}<em>{{ card.unit }}</em></strong>
              <span v-if="card.target">{{ card.target }}</span>
              <span v-else>较上期 <b :class="{ negative: Number(card.delta) < 0 }">{{ formatDelta(card.delta) }}</b></span>
            </span>
          </button>
        </div>

        <div class="finished-grid">
          <aside class="board-column board-left">
            <article class="blue-panel trend-card" @click="open('/operations')">
              <header class="panel-heading"><div><span>01</span><h2>近30日入出库趋势</h2></div><small>单位：箱</small></header>
              <TrendChart class="finished-trend" :rows="daily" :series="trendSeries" :height="168" />
            </article>
            <article class="blue-panel process-card" @click="open('/operations')">
              <header class="panel-heading"><div><span>02</span><h2>当日作业流程</h2></div><small>实时进度</small></header>
              <div class="process-flow">
                <template v-for="(step, index) in processSteps" :key="step.label">
                  <div class="process-node">
                    <span><component :is="step.icon" :size="20" /></span>
                    <strong>{{ step.label }}</strong><small>{{ formatNumber(step.value) }}箱</small><em>{{ step.rate }}</em>
                  </div>
                  <ArrowRight v-if="index < processSteps.length - 1" class="process-arrow" :size="18" />
                </template>
              </div>
            </article>
            <article class="blue-panel sku-card">
              <header class="panel-heading"><div><span>03</span><h2>热销 SKU TOP5</h2></div><small>箱</small></header>
              <div class="sku-content">
                <div class="sku-visual"><Box :size="48" /><span>FG</span><small>成品库存</small></div>
                <ol>
                  <li v-for="(sku, index) in skuRanking" :key="sku.name">
                    <b>{{ index + 1 }}</b><div><span>{{ sku.name }}</span><i><em :style="{ width: (sku.value / skuRanking[0].value * 100) + '%' }" /></i></div><strong>{{ formatNumber(sku.value) }}</strong>
                  </li>
                </ol>
              </div>
            </article>
          </aside>

          <main class="zone-command">
            <div class="zone-command-title"><span /><div><small>WAREHOUSE ZONE POSTURE</small><h2>成品库库区态势</h2></div><span /></div>
            <div class="warehouse-map">
              <div class="map-grid" />
              <div class="warehouse-outline">
                <button v-for="zone in zoneCards" :key="zone.code" type="button" class="zone-tile" :class="'is-' + zone.tone" @click="open('/zones')">
                  <span class="zone-code">{{ zone.code }}</span><strong>{{ zone.name }}</strong>
                  <component :is="zone.icon" :size="31" :stroke-width="1.5" />
                  <small>{{ zone.label }}</small><em v-if="zone.value !== null">{{ zone.value }}%</em>
                </button>
              </div>
            </div>
            <div class="zone-legend">
              <span><i class="normal" />正常</span><span><i class="warning" />预警</span><span><i class="danger" />异常</span><span><i class="working" />作业中</span>
            </div>
          </main>

          <aside class="board-column board-right">
            <article class="blue-panel kpi-card" @click="open('/performance')">
              <header class="panel-heading"><div><span>04</span><h2>KPI 目标达成</h2></div><small>当月</small></header>
              <div class="kpi-list">
                <div v-for="kpi in kpis" :key="kpi.label">
                  <ShieldCheck :size="15" /><span>{{ kpi.label }}</span><div><i :class="'is-' + kpi.tone" :style="{ width: Math.min(100, Number(kpi.value || 0) * 100) + '%' }" /></div><strong>{{ formatPercent(kpi.value) }}</strong><small>目标{{ formatPercent(kpi.target, 0) }}</small>
                </div>
              </div>
            </article>
            <article class="blue-panel exception-card" @click="open('/exceptions')">
              <header class="panel-heading"><div><span>05</span><h2>异常事件分布</h2></div><small>本月</small></header>
              <div class="exception-body">
                <div class="exception-donut"><div><strong>11</strong><span>总计</span></div></div>
                <div class="exception-legend">
                  <div v-for="item in exceptionTypes" :key="item.label"><i :style="{ background: item.color }" /><span>{{ item.label }}</span><strong>{{ item.value }}</strong><small>({{ (item.value / 11 * 100).toFixed(1) }}%)</small></div>
                </div>
              </div>
            </article>
            <article class="blue-panel closure-card" @click="open('/exceptions')">
              <header class="panel-heading"><div><span>06</span><h2>异常闭环情况</h2></div><small>闭环率 {{ closeRate }}%</small></header>
              <div class="closure-stats">
                <div><span>今日新增</span><strong>{{ latest.exceptions || 0 }}</strong><small>条</small></div>
                <div><span>已处理</span><strong>{{ Math.max(0, Number(latest.exceptions || 0) - Number(summary.openAlerts || 0)) }}</strong><small>条</small></div>
                <div class="warning"><span>待处理</span><strong>{{ summary.openAlerts || 0 }}</strong><small>条</small></div>
                <div><span>平均关闭时长</span><strong>38</strong><small>分钟</small></div>
              </div>
              <div class="closure-flow">
                <div><span><ScanLine :size="18" /></span><small>发现</small></div><ArrowRight :size="15" />
                <div><span><Users :size="18" /></span><small>分配</small></div><ArrowRight :size="15" />
                <div><span><Wrench :size="18" /></span><small>处理</small></div><ArrowRight :size="15" />
                <div><span><CheckCircle2 :size="18" /></span><small>关闭</small></div>
              </div>
            </article>
          </aside>
        </div>

        <footer class="activity-strip">
          <div class="activity-label"><Radio :size="24" /><div><strong>实时运营动态</strong><span>LIVE OPERATION</span></div></div>
          <div v-for="item in activityItems" :key="item.time" class="activity-item" :class="'is-' + item.tone">
            <component :is="item.icon" :size="28" />
            <div><span>{{ item.time }}</span><strong>{{ item.title }}</strong><small>{{ item.detail }}</small></div>
            <em>{{ item.state }}</em>
          </div>
        </footer>
      </section>
    </PageState>
  </div>
</template>
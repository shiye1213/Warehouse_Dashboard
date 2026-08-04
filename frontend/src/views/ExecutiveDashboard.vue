<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { AlertTriangle, ArrowUpRight, Database, Menu, Warehouse } from 'lucide-vue-next'
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
const zones = computed(() => [...(snapshot.value?.zones || [])].sort((a, b) => b.occupancy - a.occupancy))
const openAlerts = computed(() => (snapshot.value?.alerts || []).filter((item) => item.status !== '已关闭').slice(0, 2))
const exceptionTotal = computed(() => daily.value.reduce((total, row) => total + Number(row.exceptions || 0), 0))
const averageLoad = computed(() => {
  const fleets = snapshot.value?.forklifts || []
  return fleets.length ? fleets.reduce((total, item) => total + Number(item.load || 0), 0) / fleets.length : 0
})

const trendSeries = [
  { name: '入库', key: 'inbound', color: '#2fdbc1', area: true },
  { name: '出库', key: 'outbound', color: '#4fc4ee', lineStyle: 'dashed', symbol: 'diamond' },
  { name: '拣货', key: 'picking', color: '#f4bc4b', smooth: false, lineStyle: 'dotted', symbol: 'rect' },
]

function open(path) {
  router.push(path)
}

function keyboardOpen(event, path) {
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    open(path)
  }
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
  <div class="page legacy-board-page">
    <PageState :loading="loading && !snapshot" :error="error" @retry="refresh">
      <section class="legacy-board" aria-label="仓库运营全景主板">
        <header class="board-masthead">
          <div class="board-brand">
            <button class="board-mobile-menu" type="button" aria-label="打开主导航" @click="openNavigation"><Menu :size="20" /></button>
            <div class="board-brand-mark" aria-hidden="true"><Warehouse :size="23" /></div>
            <div><p>MULTI SOURCE WAREHOUSE DATA</p><strong>{{ snapshot?.meta?.warehouseCount || 0 }} 类仓库 · {{ snapshot?.meta?.declaredZoneCount || 0 }} 个库区</strong></div>
          </div>
          <div class="board-title">
            <p>WAREHOUSE OPERATIONS OVERVIEW</p>
            <h1>仓库运营全景主板</h1>
          </div>
          <div class="board-time">
            <div><strong>{{ now.toLocaleTimeString('zh-CN', { hour12: false }) }}</strong><span>{{ now.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', weekday: 'short' }) }}</span></div>
            <span class="board-data-stamp"><i />数据截至 {{ summary.latestDate }}</span>
          </div>
        </header>

        <div class="legacy-board-grid">
          <article class="legacy-panel trend-panel is-clickable" role="link" tabindex="0" aria-label="查看作业运营详情" @click="open('/operations')" @keydown="keyboardOpen($event, '/operations')">
            <div class="legacy-panel-accent" />
            <header class="legacy-panel-title centered"><h2><span />近 31 日出入库<span /></h2><p>固定窗口 · 成品箱数</p></header>
            <div class="today-pair">
              <div><span>今日入库</span><strong>{{ formatNumber(latest.inbound) }}</strong><small>箱</small></div>
              <div><span>今日出库</span><strong>{{ formatNumber(latest.outbound) }}</strong><small>箱</small></div>
            </div>
            <TrendChart class="legacy-trend-chart" :rows="daily" :series="trendSeries" :height="220" />
            <span class="panel-corner-link">作业详情 <ArrowUpRight :size="14" /></span>
          </article>

          <article class="legacy-panel posture-panel is-clickable" role="link" tabindex="0" aria-label="查看经营总览说明" @click="open('/performance')" @keydown="keyboardOpen($event, '/performance')">
            <div class="legacy-panel-accent centered-accent" />
            <header class="legacy-panel-title split"><h2>仓库整体态势</h2><p>数据截至 {{ summary.latestDate }}</p></header>
            <div class="posture-visual">
              <div class="health-score"><span>健康度</span><strong>{{ summary.healthScore || 0 }}</strong><small>分</small></div>
              <div class="posture-status"><i /><strong>{{ summary.healthLabel || '总体平稳' }}</strong><span>{{ summary.attentionCount || 0 }} 项关注</span></div>
              <svg viewBox="0 0 640 150" aria-hidden="true">
                <defs>
                  <linearGradient id="floorFade" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#153b43" stop-opacity=".45"/><stop offset="1" stop-color="#07181d" stop-opacity="0"/></linearGradient>
                </defs>
                <path class="rack-floor" d="M72 126 320 78l248 48-248 20z" fill="url(#floorFade)"/>
                <g class="rack rack-left">
                  <path d="M135 31v90M268 31v90M135 50h133M135 80h133M135 109h133"/>
                  <rect x="147" y="36" width="43" height="23" rx="3"/><rect x="199" y="36" width="41" height="23" rx="3"/><rect x="147" y="65" width="70" height="26" rx="3"/><rect x="224" y="65" width="36" height="26" rx="3"/><rect x="147" y="95" width="48" height="23" rx="3"/><rect x="204" y="95" width="56" height="23" rx="3"/>
                </g>
                <g class="rack rack-center">
                  <path d="M303 48v75M428 48v75M303 48h125M303 82h125M303 116h125"/>
                  <rect x="317" y="58" width="45" height="25" rx="2"/><rect class="box-warn" x="371" y="58" width="45" height="25" rx="2"/><rect x="317" y="91" width="45" height="25" rx="2"/><rect x="371" y="91" width="45" height="25" rx="2"/>
                </g>
                <g class="rack rack-right">
                  <path d="M460 31v90M593 31v90M460 50h133M460 80h133M460 109h133"/>
                  <rect x="472" y="36" width="68" height="23" rx="3"/><rect x="548" y="36" width="37" height="23" rx="3"/><rect x="472" y="65" width="47" height="26" rx="3"/><rect x="527" y="65" width="58" height="26" rx="3"/><rect x="472" y="95" width="55" height="23" rx="3"/><rect x="535" y="95" width="50" height="23" rx="3"/>
                </g>
              </svg>
            </div>
            <div class="posture-metrics">
              <div><span>今日入库</span><strong>{{ formatNumber(latest.inbound) }}</strong><small>箱</small></div>
              <div><span>今日出库</span><strong>{{ formatNumber(latest.outbound) }}</strong><small>箱</small></div>
              <div><span>拣货任务</span><strong>{{ formatNumber(latest.picking) }}</strong><small>单</small></div>
              <div><span>叉车任务</span><strong>{{ formatNumber(latest.forkliftTasks) }}</strong><small>单</small></div>
              <div><span>同步库位</span><strong>{{ formatNumber(summary.totalLocations) }}</strong><small>个</small></div>
              <div><span>可用库位</span><strong>{{ formatNumber(summary.availableLocations) }}</strong><small>个</small></div>
            </div>
          </article>

          <article class="legacy-panel monthly-panel is-clickable" role="link" tabindex="0" aria-label="查看作业运营月度详情" @click="open('/operations')" @keydown="keyboardOpen($event, '/operations')">
            <div class="legacy-panel-accent" />
            <header class="legacy-panel-title centered"><h2><span />本月运营累计<span /></h2><p>31 天固定口径</p></header>
            <div class="month-total-grid">
              <div><span>成品入库</span><strong>{{ formatNumber(summary.monthInbound) }}</strong><small>箱</small></div>
              <div><span>成品出库</span><strong>{{ formatNumber(summary.monthOutbound) }}</strong><small>箱</small></div>
              <div><span>拣货任务</span><strong>{{ formatNumber(summary.monthPicking) }}</strong><small>单</small></div>
              <div><span>叉车任务</span><strong>{{ formatNumber(summary.monthForkliftTasks) }}</strong><small>单</small></div>
            </div>
            <div class="month-total-foot"><span>月度异常 <strong>{{ exceptionTotal }}</strong> 起</span><span>平均发货及时率 <strong>{{ formatPercent(summary.deliveryTimely) }}</strong></span></div>
          </article>

          <article class="legacy-panel capacity-panel-legacy is-clickable" role="link" tabindex="0" aria-label="查看空间与库存详情" @click="open('/zones')" @keydown="keyboardOpen($event, '/zones')">
            <div class="legacy-panel-accent" />
            <header class="legacy-panel-title centered"><h2><span />库容与空间<span /></h2><p>快照 {{ snapshot?.meta?.zoneSnapshotDate }} · 已同步 {{ zones.length }}/{{ snapshot?.meta?.declaredZoneCount || 0 }} 区</p></header>
            <div class="capacity-hero-row">
              <div class="legacy-capacity-ring" :style="{ '--value': `${(summary.occupancy || 0) * 100}%` }"><div><strong>{{ formatPercent(summary.occupancy, 0) }}</strong><span>平均占用</span></div></div>
              <div><strong>{{ formatNumber(summary.totalLocations) }} 个已同步库位</strong><span>已用 {{ formatNumber(summary.occupiedLocations) }} · 可用 {{ formatNumber(summary.availableLocations) }} · 冻结 {{ formatNumber(summary.frozenLocations) }}</span></div>
            </div>
            <div class="legacy-zone-bars">
              <div v-for="zone in zones" :key="zone.code"><span>{{ zone.name }}</span><div><i :class="{ warning: zone.occupancy >= .75 }" :style="{ width: formatPercent(zone.occupancy) }" /></div><strong>{{ formatPercent(zone.occupancy, 0) }}</strong></div>
            </div>
            <span class="panel-corner-link">空间详情 <ArrowUpRight :size="14" /></span>
          </article>

          <article class="legacy-panel process-panel-legacy is-clickable" role="link" tabindex="0" aria-label="查看作业链路详情" @click="open('/operations')" @keydown="keyboardOpen($event, '/operations')">
            <div class="legacy-panel-accent" />
            <header class="legacy-panel-title centered"><h2><span />今日作业链路<span /></h2><p>收货 → 上架 → 拣货 → 出库</p></header>
            <div class="legacy-process-flow">
              <div><span>01</span><strong>{{ formatNumber(latest.inbound) }}</strong><small>收货入库 · 箱</small><em>及时率 {{ formatPercent(latest.receivingTimely) }}</em></div>
              <div><span>02</span><strong>{{ formatNumber(latest.forkliftTasks) }}</strong><small>库内上架 · 任务</small><em>平均 {{ latest.receiptMinutes || 0 }} 分</em></div>
              <div><span>03</span><strong>{{ formatNumber(latest.picking) }}</strong><small>订单拣货 · 任务</small><em>平均 {{ latest.pickingMinutes || 0 }} 分</em></div>
              <div><span>04</span><strong>{{ formatNumber(latest.outbound) }}</strong><small>复核出库 · 箱</small><em>及时率 {{ formatPercent(latest.deliveryTimely) }}</em></div>
            </div>
            <div class="legacy-resource-row">
              <div class="resource-total"><span>分库叉车负荷</span><strong>{{ formatNumber(latest.forkliftTasks) }}</strong><small>当日叉车任务</small></div>
              <div class="legacy-fleet-bars">
                <div v-for="fleet in snapshot?.forklifts || []" :key="fleet.id"><span>{{ fleet.zone }}</span><div><i :style="{ width: formatPercent(fleet.load) }" /></div><strong>{{ fleet.tasks }}</strong></div>
                <div><span>综合负荷</span><div><i :style="{ width: formatPercent(averageLoad) }" /></div><strong>{{ formatPercent(averageLoad, 0) }}</strong></div>
              </div>
            </div>
          </article>

          <article class="legacy-panel quality-panel-legacy is-clickable" role="link" tabindex="0" aria-label="查看服务质量与风险详情" @click="open('/exceptions')" @keydown="keyboardOpen($event, '/exceptions')">
            <div class="legacy-panel-accent" />
            <header class="legacy-panel-title centered"><h2><span />服务质量与风险<span /></h2><p>目标达成及未关闭事件</p></header>
            <div class="legacy-quality-kpis">
              <div><span>库存准确率</span><strong>{{ formatPercent(latest.inventoryAccuracy) }}</strong></div>
              <div><span>收货及时率</span><strong :class="{ warning: latest.receivingTimely < .95 }">{{ formatPercent(latest.receivingTimely) }}</strong></div>
              <div><span>发货及时率</span><strong :class="{ warning: latest.deliveryTimely < .94 }">{{ formatPercent(latest.deliveryTimely) }}</strong></div>
            </div>
            <div class="legacy-alert-list">
              <div v-for="alert in openAlerts" :key="alert.id"><span :class="alert.severity === '紧急' ? 'danger' : 'warning'"><AlertTriangle :size="12" /></span><div><strong>{{ alert.title }}</strong><small>{{ alert.zone }} · {{ alert.owner }}</small></div><em>{{ alert.durationHours }}h</em></div>
              <div v-if="!openAlerts.length" class="no-alert"><Database :size="15" /> 当前无未关闭异常</div>
            </div>
            <div class="legacy-quality-foot"><span>未关闭 <strong>{{ summary.openAlerts || 0 }}</strong> 项</span><span>异常关闭率 <strong>{{ formatPercent(summary.exceptionCloseRate) }}</strong></span></div>
          </article>
        </div>
      </section>
    </PageState>
  </div>
</template>

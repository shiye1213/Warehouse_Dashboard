<script setup>
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import {
  AlertTriangle, ArrowDownToLine, Boxes, CheckCircle2, ChevronRight, Clock3,
  Gauge, PackageCheck, Route, Timer, Truck,
} from 'lucide-vue-next'
import MetricCard from '../components/MetricCard.vue'
import PageState from '../components/PageState.vue'
import PanelCard from '../components/PanelCard.vue'
import TrendChart from '../components/TrendChart.vue'
import { formatNumber } from '../composables/useDashboard'
import { useScopedDashboard } from '../composables/useWarehouseScope'

const { snapshot, loading, error, refresh, selectedWarehouse, volumeUnit } = useScopedDashboard()
const route = useRoute()
const range = ref(Number(route.query.range) || 14)
const rows = computed(() => (snapshot.value?.trend || []).slice(-range.value))
const totals = computed(() => rows.value.reduce((result, row) => ({
  inbound: result.inbound + Number(row.inbound || 0),
  outbound: result.outbound + Number(row.outbound || 0),
  picking: result.picking + Number(row.picking || 0),
  forkliftTasks: result.forkliftTasks + Number(row.forkliftTasks || 0),
  receiptMinutes: result.receiptMinutes + Number(row.receiptMinutes || 0),
}), { inbound: 0, outbound: 0, picking: 0, forkliftTasks: 0, receiptMinutes: 0 }))

const latestRow = computed(() => rows.value.at(-1) || {})
const summary = computed(() => snapshot.value?.summary || {})
const targets = computed(() => Object.fromEntries((snapshot.value?.targets || []).map((item) => [item.key, Number(item.target)])))
const percent = (value) => (Number(value || 0) * 100).toFixed(1) + '%'
const target = (key, fallback) => targets.value[key] || fallback

const processBottleneck = computed(() => {
  const candidates = [
    { label: '入库上架', score: Number(summary.value.receivingTimely || 0) },
    { label: '拣选复核', score: Number(summary.value.inventoryAccuracy || 0) },
    { label: '发运出库', score: Number(summary.value.deliveryTimely || 0) },
  ]
  return candidates.sort((a, b) => a.score - b.score)[0]?.label || '暂无'
})

const processSummary = computed(() => ({
  duration: Number(summary.value.avgReceiptMinutes || 0) + Number(summary.value.avgPickingMinutes || 0),
  attainment: (
    Number(summary.value.receivingTimely || 0)
    + Number(summary.value.inventoryAccuracy || 0)
    + Number(summary.value.deliveryTimely || 0)
  ) / 3,
}))

const processStages = computed(() => [
  {
    code: '01', label: '到货接收', icon: Timer, status: '正常', tone: 'normal',
    metrics: [
      { label: '平均收货', value: (summary.value.avgReceiptMinutes || 0) + ' 分钟', icon: Clock3 },
      { label: '今日入库', value: formatNumber(summary.value.todayInbound) + ' ' + volumeUnit.value, icon: ArrowDownToLine },
      { label: '当日异常', value: (latestRow.value.exceptions || 0) + ' 单', icon: AlertTriangle, alert: Number(latestRow.value.exceptions || 0) > 0 },
    ],
  },
  {
    code: '02', label: '入库上架', icon: Boxes,
    status: Number(summary.value.receivingTimely || 0) >= target('receivingTimely', .95) ? '正常' : '预警',
    tone: Number(summary.value.receivingTimely || 0) >= target('receivingTimely', .95) ? 'normal' : 'warning',
    metrics: [
      { label: '入库及时率', value: percent(summary.value.receivingTimely), icon: Clock3, progress: Number(summary.value.receivingTimely || 0) },
      { label: '叉车任务', value: formatNumber(latestRow.value.forkliftTasks) + ' 单', icon: Truck },
      { label: '目标值', value: percent(target('receivingTimely', .95)), icon: CheckCircle2 },
    ],
  },
  {
    code: '03', label: '拣选复核', icon: PackageCheck,
    status: Number(summary.value.avgPickingMinutes || 0) <= target('avgPickingMinutes', 45) ? '正常' : '预警',
    tone: Number(summary.value.avgPickingMinutes || 0) <= target('avgPickingMinutes', 45) ? 'normal' : 'warning',
    metrics: [
      { label: '平均拣货', value: (summary.value.avgPickingMinutes || 0) + ' 分钟', icon: Timer },
      { label: '今日任务', value: formatNumber(summary.value.todayPicking) + ' 单', icon: PackageCheck },
      { label: '库存准确率', value: percent(summary.value.inventoryAccuracy), icon: CheckCircle2, progress: Number(summary.value.inventoryAccuracy || 0) },
    ],
  },
  {
    code: '04', label: '发运出库', icon: Route,
    status: Number(summary.value.deliveryTimely || 0) >= target('deliveryTimely', .94) ? '正常' : '预警',
    tone: Number(summary.value.deliveryTimely || 0) >= target('deliveryTimely', .94) ? 'normal' : 'warning',
    metrics: [
      { label: '出库及时率', value: percent(summary.value.deliveryTimely), icon: Clock3, progress: Number(summary.value.deliveryTimely || 0) },
      { label: '今日出库', value: formatNumber(summary.value.todayOutbound) + ' ' + volumeUnit.value, icon: Route },
      { label: '未关闭异常', value: (summary.value.openAlerts || 0) + ' 单', icon: AlertTriangle, alert: Number(summary.value.openAlerts || 0) > 0 },
    ],
  },
].map((stage) => ({ ...stage, bottleneck: stage.label === processBottleneck.value })))

const series = computed(() => [
  { name: '入库量（' + volumeUnit.value + '）', key: 'inbound', color: '#56dfb5', area: true },
  { name: '出库量（' + volumeUnit.value + '）', key: 'outbound', color: '#55aef2' },
  { name: '拣货任务', key: 'picking', color: '#f3bf5a' },
])
</script>

<template>
  <div class="page">
    <PageState :loading="loading && !snapshot" :error="error" @retry="refresh">
      <div class="page-intro">
        <div><p>OPERATIONS CONTROL · {{ selectedWarehouse }}</p><h2>{{ selectedWarehouse }}端到端作业运营</h2><span>从入库接收到发运交付，识别流量波峰与效率瓶颈。</span></div>
        <div class="segmented-control"><button v-for="item in [7, 14, 31]" :key="item" :class="{ active: range === item }" @click="range = item">近 {{ item }} 日</button></div>
      </div>

      <section class="metric-grid four">
        <MetricCard label="入库总量" :value="formatNumber(totals.inbound)" :unit="volumeUnit" note="所选周期" tone="mint"><template #icon><ArrowDownToLine :size="18" /></template></MetricCard>
        <MetricCard label="出库总量" :value="formatNumber(totals.outbound)" :unit="volumeUnit" note="所选周期" tone="blue"><template #icon><Route :size="18" /></template></MetricCard>
        <MetricCard label="拣货任务" :value="formatNumber(totals.picking)" unit="单" note="所选周期" tone="amber"><template #icon><PackageCheck :size="18" /></template></MetricCard>
        <MetricCard label="叉车任务" :value="formatNumber(totals.forkliftTasks)" unit="单" note="所选周期" tone="violet"><template #icon><Truck :size="18" /></template></MetricCard>
      </section>

      <section class="operations-detail-stack">
        <PanelCard title="作业量趋势" subtitle="同一视图对比入库、出库和拣货节奏" eyebrow="DAILY THROUGHPUT">
          <TrendChart :rows="rows" :series="series" :height="330" />
        </PanelCard>

        <section class="operations-process-panel">
          <header class="operations-process-header">
            <div class="operations-process-title">
              <p>PROCESS FLOW</p>
              <div><h2>流程效率</h2><span>最新业务日的关键阶段</span></div>
            </div>
            <div class="operations-process-summary">
              <div><Clock3 :size="22" /><span><small>关键作业时长</small><strong>{{ processSummary.duration }}<em>分钟</em></strong></span></div>
              <div><Gauge :size="22" /><span><small>平均达成率</small><strong>{{ percent(processSummary.attainment) }}</strong></span></div>
              <div class="is-bottleneck"><AlertTriangle :size="22" /><span><small>当前瓶颈</small><strong>{{ processBottleneck }}</strong></span></div>
            </div>
          </header>

          <div class="operations-stage-flow">
            <template v-for="(stage, index) in processStages" :key="stage.code">
              <article class="operations-stage-card" :class="{ 'is-bottleneck': stage.bottleneck, 'is-warning': stage.tone === 'warning' }">
                <div class="operations-stage-heading">
                  <span>{{ stage.code }}</span>
                  <em>{{ stage.bottleneck ? '瓶颈关注' : stage.status }}</em>
                </div>
                <h3>{{ stage.label }}</h3>
                <div class="operations-stage-visual">
                  <span><component :is="stage.icon" :size="58" /></span>
                  <i /><i /><i />
                </div>
                <div class="operations-stage-metrics">
                  <div v-for="metric in stage.metrics" :key="metric.label" :class="{ 'is-alert': metric.alert }">
                    <component :is="metric.icon" :size="17" />
                    <span><small>{{ metric.label }}</small><strong>{{ metric.value }}</strong></span>
                    <i v-if="metric.progress !== undefined"><em :style="{ width: Math.min(metric.progress * 100, 100) + '%' }" /></i>
                  </div>
                </div>
              </article>
              <div v-if="index < processStages.length - 1" class="operations-stage-arrow"><i /><ChevronRight :size="31" /><i /></div>
            </template>
          </div>

          <footer class="operations-process-footer">
            <div><Gauge :size="27" /><span><strong>流程总览</strong><small>4 个阶段　|　串联执行　|　全链路可视</small></span></div>
            <div class="operations-process-legend">
              <span><i class="normal" /><strong>正常</strong><small>指标达成良好</small></span>
              <span><i class="warning" /><strong>预警</strong><small>指标接近阈值</small></span>
              <span><i class="bottleneck" /><strong>瓶颈关注</strong><small>当前最低达成率</small></span>
            </div>
          </footer>
        </section>
      </section>

      <PanelCard title="每日作业明细" subtitle="支持客户回顾与运营复盘" eyebrow="DAILY RECORDS">
        <div class="data-table-wrap"><table class="data-table"><thead><tr><th>日期</th><th>入库（{{ volumeUnit }}）</th><th>出库（{{ volumeUnit }}）</th><th>拣货任务</th><th>叉车任务</th><th>收货时长</th><th>加班工时</th></tr></thead><tbody><tr v-for="row in [...rows].reverse()" :key="row.date"><td><strong>{{ row.date }}</strong></td><td>{{ formatNumber(row.inbound) }}</td><td>{{ formatNumber(row.outbound) }}</td><td>{{ formatNumber(row.picking) }}</td><td>{{ formatNumber(row.forkliftTasks) }}</td><td>{{ row.receiptMinutes }} 分钟</td><td>{{ row.overtimeHours }} 小时</td></tr></tbody></table></div>
      </PanelCard>
    </PageState>
  </div>
</template>
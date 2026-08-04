<script setup>
import { computed } from 'vue'
import { Activity, BatteryCharging, Gauge, Route, Truck } from 'lucide-vue-next'
import MetricCard from '../components/MetricCard.vue'
import PageState from '../components/PageState.vue'
import PanelCard from '../components/PanelCard.vue'
import StatusBadge from '../components/StatusBadge.vue'
import TrendChart from '../components/TrendChart.vue'
import { formatNumber, formatPercent, useDashboard } from '../composables/useDashboard'

const { snapshot, loading, error, refresh } = useDashboard()
const fleets = computed(() => snapshot.value?.forklifts || [])
const rows = computed(() => (snapshot.value?.trend || []).slice(-14))
const totalTasks = computed(() => fleets.value.reduce((sum, item) => sum + Number(item.tasks || 0), 0))
const averageLoad = computed(() => fleets.value.length ? fleets.value.reduce((sum, item) => sum + Number(item.load || 0), 0) / fleets.value.length : 0)
const highLoad = computed(() => fleets.value.filter((item) => item.load >= 0.85).length)
const peakCapacity = computed(() => fleets.value.reduce((sum, item) => sum + Number(item.peakTasks || 0), 0))
const series = [{ name: '叉车任务', key: 'forkliftTasks', type: 'bar', color: '#56dfb5' }, { name: '月台利用率', key: 'dockUtilization', color: '#55aef2', percent: true }]
</script>

<template>
  <div class="page">
    <PageState :loading="loading && !snapshot" :error="error" @retry="refresh">
      <div class="page-intro"><div><p>RESOURCE ORCHESTRATION</p><h2>资源负荷与调度</h2><span>观察各仓任务池负荷，提前识别高峰和跨库支援机会。</span></div><div class="quality-seal"><Truck :size="21" /><span><strong>{{ fleets.length }} 个任务池在线</strong><small>总任务 {{ formatNumber(totalTasks) }} 单</small></span></div></div>
      <section class="metric-grid four">
        <MetricCard label="当前任务" :value="formatNumber(totalTasks)" unit="单" note="全部任务池" tone="mint"><template #icon><Route :size="18" /></template></MetricCard>
        <MetricCard label="平均负荷" :value="formatPercent(averageLoad, 0)" note="资源利用水平" tone="blue"><template #icon><Gauge :size="18" /></template></MetricCard>
        <MetricCard label="高负荷任务池" :value="highLoad" unit="个" note="负荷 ≥ 85%" tone="amber"><template #icon><Activity :size="18" /></template></MetricCard>
        <MetricCard label="峰值承载" :value="formatNumber(peakCapacity)" unit="单" note="任务池合计" tone="violet"><template #icon><BatteryCharging :size="18" /></template></MetricCard>
      </section>
      <section class="fleet-grid">
        <article v-for="fleet in fleets" :key="fleet.id" class="fleet-card">
          <div class="fleet-card-head"><div class="resource-icon"><Truck :size="22" /></div><StatusBadge :value="fleet.status" /></div>
          <p>{{ fleet.id }}</p><h3>{{ fleet.zone }}</h3>
          <div class="fleet-load"><div><span>任务负荷</span><strong>{{ formatPercent(fleet.load, 0) }}</strong></div><div class="progress-track"><i :style="{ width: formatPercent(fleet.load) }" /></div></div>
          <div class="fleet-stats"><span><small>当前任务</small><strong>{{ fleet.tasks }}</strong></span><span><small>历史峰值</small><strong>{{ fleet.peakTasks }}</strong></span><span><small>可承接余量</small><strong>{{ Math.max(0, fleet.peakTasks - fleet.tasks) }}</strong></span></div>
        </article>
      </section>
      <section class="detail-grid wide-left">
        <PanelCard title="任务与月台趋势" subtitle="近 14 日任务量及月台利用水平" eyebrow="LOAD TREND"><TrendChart :rows="rows" :series="series" :height="330" /></PanelCard>
        <PanelCard title="调度建议" subtitle="基于当前任务池负荷" eyebrow="DISPATCH GUIDANCE">
          <div class="dispatch-list"><article><span>01</span><div><strong>原料库优先保供</strong><p>原料库任务池接近高位，建议在到货集中时段预留支援车辆。</p></div></article><article><span>02</span><div><strong>成品库错峰发运</strong><p>结合出库计划拆分高峰波次，降低月台与叉车同时拥堵。</p></div></article><article><span>03</span><div><strong>箱盒库保留机动量</strong><p>维持当前任务余量，作为跨库紧急任务的首选支援池。</p></div></article></div>
        </PanelCard>
      </section>
    </PageState>
  </div>
</template>

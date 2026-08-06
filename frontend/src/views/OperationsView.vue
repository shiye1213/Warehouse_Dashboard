<script setup>
import { computed, ref } from 'vue'
import { ArrowDownToLine, Boxes, PackageCheck, Route, Timer, Truck } from 'lucide-vue-next'
import MetricCard from '../components/MetricCard.vue'
import PageState from '../components/PageState.vue'
import PanelCard from '../components/PanelCard.vue'
import TrendChart from '../components/TrendChart.vue'
import { formatNumber } from '../composables/useDashboard'
import { useScopedDashboard } from '../composables/useWarehouseScope'

const { snapshot, loading, error, refresh, selectedWarehouse, volumeUnit } = useScopedDashboard()
const range = ref(14)
const rows = computed(() => (snapshot.value?.trend || []).slice(-range.value))
const totals = computed(() => rows.value.reduce((result, row) => ({
  inbound: result.inbound + Number(row.inbound || 0),
  outbound: result.outbound + Number(row.outbound || 0),
  picking: result.picking + Number(row.picking || 0),
  forkliftTasks: result.forkliftTasks + Number(row.forkliftTasks || 0),
  receiptMinutes: result.receiptMinutes + Number(row.receiptMinutes || 0),
}), { inbound: 0, outbound: 0, picking: 0, forkliftTasks: 0, receiptMinutes: 0 }))

const series = computed(() => [
  { name: `入库量（${volumeUnit.value}）`, key: 'inbound', color: '#56dfb5', area: true },
  { name: `出库量（${volumeUnit.value}）`, key: 'outbound', color: '#55aef2' },
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

      <section class="detail-grid wide-left">
        <PanelCard title="作业量趋势" subtitle="同一视图对比入库、出库和拣货节奏" eyebrow="DAILY THROUGHPUT"><TrendChart :rows="rows" :series="series" :height="330" /></PanelCard>
        <PanelCard title="流程效率" subtitle="最新业务日的关键阶段" eyebrow="PROCESS FLOW">
          <div class="process-flow">
            <div><span class="process-index">01</span><div><strong>到货接收</strong><small>平均收货 {{ snapshot?.summary?.avgReceiptMinutes || 0 }} 分钟</small></div><Timer :size="18" /></div>
            <i />
            <div><span class="process-index">02</span><div><strong>入库上架</strong><small>及时率 {{ ((snapshot?.summary?.receivingTimely || 0) * 100).toFixed(1) }}%</small></div><Boxes :size="18" /></div>
            <i />
            <div><span class="process-index">03</span><div><strong>拣选复核</strong><small>平均拣货 {{ snapshot?.summary?.avgPickingMinutes || 0 }} 分钟</small></div><PackageCheck :size="18" /></div>
            <i />
            <div><span class="process-index">04</span><div><strong>发运出库</strong><small>及时率 {{ ((snapshot?.summary?.deliveryTimely || 0) * 100).toFixed(1) }}%</small></div><Route :size="18" /></div>
          </div>
        </PanelCard>
      </section>

      <PanelCard title="每日作业明细" subtitle="支持客户回顾与运营复盘" eyebrow="DAILY RECORDS">
        <div class="data-table-wrap"><table class="data-table"><thead><tr><th>日期</th><th>入库（{{ volumeUnit }}）</th><th>出库（{{ volumeUnit }}）</th><th>拣货任务</th><th>叉车任务</th><th>收货时长</th><th>加班工时</th></tr></thead><tbody><tr v-for="row in [...rows].reverse()" :key="row.date"><td><strong>{{ row.date }}</strong></td><td>{{ formatNumber(row.inbound) }}</td><td>{{ formatNumber(row.outbound) }}</td><td>{{ formatNumber(row.picking) }}</td><td>{{ formatNumber(row.forkliftTasks) }}</td><td>{{ row.receiptMinutes }} 分钟</td><td>{{ row.overtimeHours }} 小时</td></tr></tbody></table></div>
      </PanelCard>
    </PageState>
  </div>
</template>

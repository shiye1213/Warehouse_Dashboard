<script setup>
import { computed } from 'vue'
import { Award, CheckCircle2, Clock3, Crosshair } from 'lucide-vue-next'
import MetricCard from '../components/MetricCard.vue'
import PageState from '../components/PageState.vue'
import PanelCard from '../components/PanelCard.vue'
import StatusBadge from '../components/StatusBadge.vue'
import TrendChart from '../components/TrendChart.vue'
import { formatPercent, useDashboard } from '../composables/useDashboard'

const { snapshot, loading, error, refresh } = useDashboard()
const summary = computed(() => snapshot.value?.summary || {})
const rows = computed(() => snapshot.value?.trend || [])
const targetByKey = computed(() => Object.fromEntries((snapshot.value?.targets || []).map((item) => [item.key, item])))
const series = [
  { name: '库存准确率', key: 'inventoryAccuracy', color: '#56dfb5', percent: true, area: true },
  { name: '入库及时率', key: 'receivingTimely', color: '#55aef2', percent: true },
  { name: '出库及时率', key: 'deliveryTimely', color: '#f3bf5a', percent: true },
]

function actualFor(target) {
  const map = {
    inventoryAccuracy: summary.value.inventoryAccuracy,
    receivingTimely: summary.value.receivingTimely,
    deliveryTimely: summary.value.deliveryTimely,
    occupancy: summary.value.occupancy,
    openExceptions: summary.value.openAlerts,
    exceptionCloseRate: summary.value.exceptionCloseRate,
    pickingMinutes: summary.value.avgPickingMinutes,
  }
  return map[target.key]
}

function isPass(target) {
  const actual = Number(actualFor(target) || 0)
  return target.rule?.includes('高于') ? actual <= Number(target.target) : actual >= Number(target.target)
}

function display(value, target) {
  return target.unit === '%' ? formatPercent(value) : `${Number(value || 0).toFixed(target.unit === '分钟' ? 1 : 0)} ${target.unit}`
}
</script>

<template>
  <div class="page">
    <PageState :loading="loading && !snapshot" :error="error" @retry="refresh">
      <div class="page-intro"><div><p>SERVICE COMMITMENT</p><h2>履约质量与客户承诺</h2><span>让时效、准确性和异常闭环都可量化、可追踪、可解释。</span></div><div class="quality-seal"><Award :size="21" /><span><strong>服务水平稳定</strong><small>3 项核心承诺持续监测</small></span></div></div>
      <section class="metric-grid four">
        <MetricCard label="库存准确率" :value="formatPercent(summary.inventoryAccuracy)" :delta="summary.deltas?.inventoryAccuracy" note="目标 98.0%" tone="mint"><template #icon><Crosshair :size="18" /></template></MetricCard>
        <MetricCard label="入库及时率" :value="formatPercent(summary.receivingTimely)" :delta="summary.deltas?.receivingTimely" note="目标 95.0%" tone="blue"><template #icon><Clock3 :size="18" /></template></MetricCard>
        <MetricCard label="出库及时率" :value="formatPercent(summary.deliveryTimely)" :delta="summary.deltas?.deliveryTimely" note="目标 94.0%" tone="amber"><template #icon><CheckCircle2 :size="18" /></template></MetricCard>
        <MetricCard label="异常关闭率" :value="formatPercent(summary.exceptionCloseRate)" note="目标 90.0%" tone="violet"><template #icon><CheckCircle2 :size="18" /></template></MetricCard>
      </section>
      <section class="detail-grid wide-left">
        <PanelCard title="服务水平趋势" subtitle="日度表现，百分比口径" eyebrow="QUALITY TREND"><TrendChart :rows="rows" :series="series" :height="350" /></PanelCard>
        <PanelCard title="目标达成矩阵" subtitle="来源、定义与达标状态" eyebrow="TARGET MATRIX">
          <div class="target-list">
            <div v-for="target in snapshot?.targets || []" :key="target.key" class="target-row">
              <div><strong>{{ target.name }}</strong><small>{{ target.definition }}</small></div>
              <span>{{ display(actualFor(target), target) }} / {{ display(target.target, target) }}</span>
              <StatusBadge :value="isPass(target) ? '达标' : '需关注'" />
            </div>
          </div>
        </PanelCard>
      </section>
      <PanelCard title="指标口径说明" subtitle="为客户报告和管理复盘提供统一解释" eyebrow="METRIC DEFINITIONS">
        <div class="definition-grid"><article v-for="target in snapshot?.targets || []" :key="target.key"><span>{{ target.name }}</span><strong>{{ target.definition }}</strong><small>数据来源：{{ target.source }}</small></article></div>
      </PanelCard>
    </PageState>
  </div>
</template>

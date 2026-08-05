<script setup>
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { AlertOctagon, CheckCircle2, Clock3, ShieldAlert } from 'lucide-vue-next'
import MetricCard from '../components/MetricCard.vue'
import PageState from '../components/PageState.vue'
import PanelCard from '../components/PanelCard.vue'
import StatusBadge from '../components/StatusBadge.vue'
import { formatPercent } from '../composables/useDashboard'
import { useScopedDashboard } from '../composables/useWarehouseScope'

const route = useRoute()
const { snapshot, loading, error, refresh, selectedWarehouse } = useScopedDashboard()
const filter = ref(route.query.focus ? '全部' : '未关闭')
const focus = ref(route.query.focus || '')
const filters = ['未关闭', '全部', '紧急', '已关闭']
const all = computed(() => snapshot.value?.alerts || [])
const rows = computed(() => all.value.filter((item) => {
  if (focus.value) return item.id === focus.value
  if (filter.value === '未关闭') return item.status !== '已关闭'
  if (filter.value === '紧急') return item.severity === '紧急'
  if (filter.value === '已关闭') return item.status === '已关闭'
  return true
}))
const openCount = computed(() => all.value.filter((item) => item.status !== '已关闭').length)
const criticalCount = computed(() => all.value.filter((item) => item.severity === '紧急' && item.status !== '已关闭').length)
const closeRate = computed(() => all.value.length ? all.value.filter((item) => item.status === '已关闭').length / all.value.length : 0)
const avgResponse = computed(() => all.value.length ? all.value.reduce((sum, item) => sum + Number(item.responseMinutes || 0), 0) / all.value.length : 0)

function changeFilter(value) { filter.value = value; focus.value = '' }
</script>

<template>
  <div class="page">
    <PageState :loading="loading && !snapshot" :error="error" @retry="refresh">
      <div class="page-intro"><div><p>RISK RESPONSE · {{ selectedWarehouse }}</p><h2>{{ selectedWarehouse }}风险与异常处置</h2><span>聚合严重程度、SLA、责任人与建议动作，推动异常闭环。</span></div><div class="segmented-control"><button v-for="item in filters" :key="item" :class="{ active: filter === item && !focus }" @click="changeFilter(item)">{{ item }}</button></div></div>
      <section class="metric-grid four">
        <MetricCard label="未关闭异常" :value="openCount" unit="项" note="当前待处置" tone="rose"><template #icon><AlertOctagon :size="18" /></template></MetricCard>
        <MetricCard label="紧急异常" :value="criticalCount" unit="项" note="优先响应" tone="amber"><template #icon><ShieldAlert :size="18" /></template></MetricCard>
        <MetricCard label="平均响应" :value="avgResponse.toFixed(0)" unit="分钟" note="全部事件" tone="blue"><template #icon><Clock3 :size="18" /></template></MetricCard>
        <MetricCard label="关闭率" :value="formatPercent(closeRate)" note="事件闭环" tone="mint"><template #icon><CheckCircle2 :size="18" /></template></MetricCard>
      </section>
      <PanelCard title="异常事件台账" :subtitle="focus ? `正在查看 ${focus}` : `当前筛选 ${rows.length} 条记录`" eyebrow="EXCEPTION REGISTER">
        <div v-if="focus" class="focus-banner">已从经营总览定位到事件 {{ focus }}<button @click="focus = ''; filter = '全部'">查看全部事件</button></div>
        <div class="exception-list">
          <article v-for="item in rows" :key="item.id" class="exception-card">
            <div class="exception-main"><span class="exception-id">{{ item.id }}</span><div><h3>{{ item.title }}</h3><p>{{ item.description }}</p><div class="exception-tags"><StatusBadge :value="item.severity" /><StatusBadge :value="item.status" /><span>{{ item.warehouse }} / {{ item.zone }}</span></div></div></div>
            <div class="exception-meta"><div><span>责任人</span><strong>{{ item.owner }}</strong></div><div><span>首次响应</span><strong>{{ item.responseMinutes }} 分钟</strong></div><div><span>SLA</span><strong :class="{ danger: item.slaBreached }">{{ item.slaBreached ? '已超时' : `${item.slaHours} 小时内` }}</strong></div><div><span>持续时长</span><strong>{{ item.durationHours }} 小时</strong></div></div>
            <div class="exception-action"><span>建议动作</span><strong>{{ item.recommendation }}</strong><small>{{ item.material }} · {{ item.project }}</small></div>
          </article>
        </div>
        <div v-if="!rows.length" class="empty-inline"><CheckCircle2 :size="20" /> 当前筛选范围内暂无异常</div>
      </PanelCard>
    </PageState>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { AlertTriangle, ArrowLeftRight, Boxes, ClipboardCheck, Layers3, UserRound } from 'lucide-vue-next'
import MetricCard from '../components/MetricCard.vue'
import PageState from '../components/PageState.vue'
import PanelCard from '../components/PanelCard.vue'
import StatusBadge from '../components/StatusBadge.vue'
import { formatNumber, formatPercent, useDashboard } from '../composables/useDashboard'

const route = useRoute()
const router = useRouter()
const { snapshot, loading, error, refresh } = useDashboard()
const zone = computed(() => (snapshot.value?.zones || []).find((item) => item.code === route.params.code))
const relatedAlerts = computed(() => (snapshot.value?.alerts || []).filter((item) => item.zoneCode === route.params.code || item.zone === zone.value?.name))
const pressure = computed(() => zone.value?.occupancy >= 0.85 ? '高' : zone.value?.occupancy >= 0.75 ? '中' : '低')
</script>

<template>
  <div class="page">
    <PageState :loading="loading && !snapshot" :error="error || (!loading && !zone ? '未找到该库区' : '')" @retry="refresh">
      <template v-if="zone">
        <section class="zone-hero">
          <div><p>ZONE / {{ zone.code }}</p><h2>{{ zone.name }}</h2><span>{{ zone.warehouse }} · 快照日期 {{ zone.snapshotDate }}</span><div class="zone-hero-badges"><StatusBadge :value="zone.status" /><span>空间压力 {{ pressure }}</span></div></div>
          <div class="zone-score" :style="{ '--value': `${zone.occupancy * 100}%` }"><strong>{{ formatPercent(zone.occupancy) }}</strong><span>库位占用</span></div>
        </section>
        <section class="metric-grid four">
          <MetricCard label="容量库位" :value="formatNumber(zone.capacity)" unit="个" note="规划总容量" tone="blue"><template #icon><Layers3 :size="18" /></template></MetricCard>
          <MetricCard label="已用库位" :value="formatNumber(zone.occupied)" unit="个" note="当前占用" tone="mint"><template #icon><Boxes :size="18" /></template></MetricCard>
          <MetricCard label="可用库位" :value="formatNumber(zone.available)" unit="个" note="剩余承载" tone="violet"><template #icon><ArrowLeftRight :size="18" /></template></MetricCard>
          <MetricCard label="物料种类" :value="formatNumber(zone.materialTypes)" unit="类" note="在库 SKU 口径" tone="amber"><template #icon><ClipboardCheck :size="18" /></template></MetricCard>
        </section>
        <section class="detail-grid">
          <PanelCard title="库位使用结构" subtitle="按可用、占用与冻结状态分解" eyebrow="SPACE STRUCTURE">
            <div class="stacked-capacity"><i class="occupied" :style="{ width: `${zone.occupied / zone.capacity * 100}%` }" /><i class="frozen" :style="{ width: `${zone.frozen / zone.capacity * 100}%` }" /></div>
            <div class="structure-legend"><div><i class="occupied" /><span>已占用</span><strong>{{ zone.occupied }}</strong></div><div><i class="available" /><span>可用</span><strong>{{ zone.available }}</strong></div><div><i class="frozen" /><span>冻结</span><strong>{{ zone.frozen }}</strong></div></div>
            <div class="manager-card"><UserRound :size="21" /><div><span>库区负责人</span><strong>{{ zone.manager }}</strong></div><button @click="router.push('/exceptions')">查看责任事项</button></div>
          </PanelCard>
          <PanelCard title="运营建议" subtitle="根据空间占用与异常状态自动生成" eyebrow="ACTION PLAN">
            <div class="action-plan"><span>01</span><div><strong>释放高龄库存占用</strong><p>对库龄较长的物料进行复核，优先处理冻结库位与待判物料。</p></div></div>
            <div class="action-plan"><span>02</span><div><strong>预留作业缓冲位</strong><p>根据近期出入库波峰，为收货与发运分别保留临时缓冲空间。</p></div></div>
            <div class="action-plan"><span>03</span><div><strong>复核库位准确性</strong><p>结合下一次盘点任务核对系统占用与现场实物状态。</p></div></div>
          </PanelCard>
        </section>
        <PanelCard title="关联异常" :subtitle="`${relatedAlerts.length} 条与本库区相关的事件记录`" eyebrow="RELATED EXCEPTIONS">
          <div v-if="relatedAlerts.length" class="data-table-wrap"><table class="data-table"><thead><tr><th>事件</th><th>等级</th><th>状态</th><th>责任人</th><th>持续时长</th><th>建议</th></tr></thead><tbody><tr v-for="item in relatedAlerts" :key="item.id"><td><strong>{{ item.title }}</strong><small>{{ item.id }}</small></td><td><StatusBadge :value="item.severity" /></td><td><StatusBadge :value="item.status" /></td><td>{{ item.owner }}</td><td>{{ item.durationHours }} 小时</td><td>{{ item.recommendation }}</td></tr></tbody></table></div><div v-else class="empty-inline"><AlertTriangle :size="19" /> 暂无与该库区关联的异常记录</div>
        </PanelCard>
      </template>
    </PageState>
  </div>
</template>

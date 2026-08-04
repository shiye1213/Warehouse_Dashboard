<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Boxes, Layers3, LockKeyhole, Warehouse } from 'lucide-vue-next'
import MetricCard from '../components/MetricCard.vue'
import PageState from '../components/PageState.vue'
import PanelCard from '../components/PanelCard.vue'
import StatusBadge from '../components/StatusBadge.vue'
import { formatNumber, formatPercent, useDashboard } from '../composables/useDashboard'

const router = useRouter()
const { snapshot, loading, error, refresh } = useDashboard()
const summary = computed(() => snapshot.value?.summary || {})
const zones = computed(() => [...(snapshot.value?.zones || [])].sort((a, b) => b.occupancy - a.occupancy))

function openZone(zone) { router.push(`/zones/${encodeURIComponent(zone.code)}`) }
function tone(zone) { return zone.occupancy >= 0.85 ? 'danger' : zone.occupancy >= 0.75 ? 'warning' : 'normal' }
</script>

<template>
  <div class="page">
    <PageState :loading="loading && !snapshot" :error="error" @retry="refresh">
      <div class="page-intro"><div><p>SPACE INTELLIGENCE</p><h2>空间与库存态势</h2><span>统一查看库容、占用结构和可用空间，支持高峰期调拨决策。</span></div><div class="snapshot-note"><span class="live-dot" /><div><strong>库区快照</strong><small>{{ snapshot?.meta?.zoneSnapshotDate }} · {{ zones.length }} 个已同步库区</small></div></div></div>
      <section class="metric-grid four">
        <MetricCard label="总库位" :value="formatNumber(summary.totalLocations)" unit="个" note="已同步范围" tone="blue"><template #icon><Warehouse :size="18" /></template></MetricCard>
        <MetricCard label="已用库位" :value="formatNumber(summary.occupiedLocations)" unit="个" note="动态占用" tone="mint"><template #icon><Boxes :size="18" /></template></MetricCard>
        <MetricCard label="可用库位" :value="formatNumber(summary.availableLocations)" unit="个" note="可承接空间" tone="violet"><template #icon><Layers3 :size="18" /></template></MetricCard>
        <MetricCard label="冻结库位" :value="formatNumber(summary.frozenLocations)" unit="个" note="暂不可用" tone="amber"><template #icon><LockKeyhole :size="18" /></template></MetricCard>
      </section>

      <section class="detail-grid wide-left">
        <PanelCard title="仓库空间分布" subtitle="颜色表示占用压力，点击库区查看明细" eyebrow="WAREHOUSE MAP">
          <div class="warehouse-map">
            <button v-for="(zone, index) in zones" :key="zone.code" class="zone-tile" :class="`is-${tone(zone)}`" :style="{ '--fill': `${zone.occupancy * 100}%`, '--delay': `${index * 70}ms` }" @click="openZone(zone)">
              <span class="zone-code">{{ zone.code }}</span>
              <div><strong>{{ zone.name }}</strong><small>{{ zone.warehouse }} · {{ zone.materialTypes }} 类物料</small></div>
              <b>{{ formatPercent(zone.occupancy) }}</b>
              <i /><ArrowRight :size="16" />
            </button>
          </div>
          <div class="map-legend"><span><i class="normal" />低于 75%</span><span><i class="warning" />75%–85%</span><span><i class="danger" />高于 85%</span></div>
        </PanelCard>
        <PanelCard title="容量结构" subtitle="综合占用与剩余承载" eyebrow="CAPACITY MIX">
          <div class="large-capacity-ring" :style="{ '--value': `${summary.occupancy * 100}%` }"><div><strong>{{ formatPercent(summary.occupancy) }}</strong><span>综合占用率</span></div></div>
          <div class="capacity-breakdown"><div><span>已占用</span><strong>{{ formatNumber(summary.occupiedLocations) }}</strong></div><div><span>可用</span><strong>{{ formatNumber(summary.availableLocations) }}</strong></div><div><span>冻结</span><strong>{{ formatNumber(summary.frozenLocations) }}</strong></div></div>
          <div class="recommendation-box"><strong>空间建议</strong><span>当前整体低于 85% 管理上限。优先关注成品存储区，出库高峰前建议预留缓冲库位。</span></div>
        </PanelCard>
      </section>

      <PanelCard title="库区经营明细" subtitle="负责人、容量、物料与状态" eyebrow="ZONE DIRECTORY">
        <div class="zone-card-grid"><button v-for="zone in zones" :key="zone.code" class="zone-card" @click="openZone(zone)"><div class="zone-card-top"><span>{{ zone.code }}</span><StatusBadge :value="zone.status" /></div><h3>{{ zone.name }}</h3><p>{{ zone.warehouse }} · 负责人 {{ zone.manager }}</p><div class="zone-card-meter"><i :style="{ width: formatPercent(zone.occupancy) }" /></div><div class="zone-card-stats"><span><small>占用</small><strong>{{ zone.occupied }} / {{ zone.capacity }}</strong></span><span><small>物料种类</small><strong>{{ zone.materialTypes }}</strong></span><span><small>冻结</small><strong>{{ zone.frozen }}</strong></span></div><em>查看库区详情 <ArrowRight :size="15" /></em></button></div>
      </PanelCard>
    </PageState>
  </div>
</template>

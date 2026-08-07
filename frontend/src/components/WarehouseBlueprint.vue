<script setup>
import { computed } from 'vue'
import { ArrowRight, Boxes, PackageCheck, Ruler, Warehouse } from 'lucide-vue-next'
import { formatNumber, formatPercent } from '../composables/useDashboard'

const props = defineProps({
  zones: { type: Array, default: () => [] },
  selectedWarehouse: { type: String, default: '全部仓库' },
})

const emit = defineEmits(['open-zone'])
const isOverview = computed(() => props.selectedWarehouse === '全部仓库')
const totalCapacity = computed(() => props.zones.reduce((total, zone) => total + Number(zone.capacity || 0), 0))
const occupiedLocations = computed(() => props.zones.reduce((total, zone) => total + Number(zone.occupied || 0), 0))
const averageOccupancy = computed(() => totalCapacity.value ? occupiedLocations.value / totalCapacity.value : 0)
const categoryLabel = computed(() => isOverview.value ? '全仓总览' : props.selectedWarehouse)

function tone(zone) {
  return zone.occupancy >= 0.85 ? 'danger' : zone.occupancy >= 0.75 ? 'warning' : 'normal'
}
</script>

<template>
  <section class="blueprint" :class="[{ 'is-overview': isOverview, 'is-focused': !isOverview }, `zones-${zones.length}`]">
    <header class="blueprint-header">
      <div class="blueprint-heading">
        <span>WAREHOUSE MAP</span>
        <h2>仓库空间分布</h2>
        <p>颜色表示占用压力，点击库区查看明细</p>
      </div>
      <div class="blueprint-category">
        <Warehouse v-if="selectedWarehouse === '成品库'" :size="24" />
        <PackageCheck v-else-if="selectedWarehouse === '箱盒库'" :size="24" />
        <Boxes v-else :size="24" />
        <strong>{{ categoryLabel }}</strong>
      </div>
      <div class="blueprint-legend" aria-label="占用率图例">
        <span><i class="normal" />正常</span>
        <span><i class="warning" />偏高</span>
        <span><i class="danger" />高负荷</span>
      </div>
    </header>

    <div class="blueprint-stage">
      <aside v-if="isOverview" class="blueprint-summary">
        <div><Warehouse :size="24" /><span>规划库位<strong>{{ formatNumber(totalCapacity) }}<small>个</small></strong></span></div>
        <div><Boxes :size="24" /><span>库区总数<strong>{{ zones.length }}<small>个</small></strong></span></div>
        <div><Ruler :size="24" /><span>平均占用率<strong>{{ formatPercent(averageOccupancy) }}</strong></span></div>
        <footer>快照日期：2026-07-01</footer>
      </aside>

      <div class="blueprint-yard">
        <div class="yard-grid" />



        <div class="warehouse-frame">
          <div class="frame-corners" />


          <div class="zone-grid">
            <button
              v-for="(zone, index) in zones"
              :key="zone.code"
              type="button"
              class="blueprint-zone"
              :class="`is-${tone(zone)}`"
              :style="{ '--zone-delay': `${index * 45}ms`, '--zone-fill': `${Math.min(zone.occupancy * 100, 100)}%` }"
              @click="emit('open-zone', zone)"
            >
              <span class="rack-pattern" />
              <span class="zone-code">{{ zone.code }}</span>
              <strong>{{ zone.name }}</strong>
              <small>{{ zone.warehouse }} · {{ zone.materialTypes }} 类物料</small>
              <b>{{ formatPercent(zone.occupancy) }}</b>
              <span class="zone-meter"><i /></span>
              <ArrowRight class="zone-arrow" :size="21" />
            </button>
          </div>

        </div>
      </div>
    </div>


  </section>
</template>

<style scoped>
.blueprint {
  --blue: #38aaff;
  --blue-soft: #7bc8ff;
  --normal: #54ce79;
  --warning: #ff9d29;
  --danger: #ff5d55;
  position: relative;
  min-width: 0;
  overflow: hidden;
  border: 1px solid rgba(46, 150, 234, .43);
  border-radius: 18px;
  color: #eaf6ff;
  background: radial-gradient(circle at 50% 42%, rgba(17, 82, 145, .25), transparent 52%), #020f1c;
  box-shadow: inset 0 0 70px rgba(16, 93, 160, .12), 0 22px 70px rgba(0, 0, 0, .28);
}
.blueprint::before { position: absolute; inset: 0; content: ''; pointer-events: none; opacity: .38; background-image: linear-gradient(rgba(37, 115, 174, .11) 1px, transparent 1px), linear-gradient(90deg, rgba(37, 115, 174, .11) 1px, transparent 1px); background-size: 28px 28px; }
.blueprint-header { position: relative; z-index: 4; display: grid; min-height: 88px; grid-template-columns: 1fr auto 1fr; align-items: center; gap: 22px; padding: 18px 28px; border-bottom: 1px solid rgba(46, 150, 234, .3); background: rgba(1, 13, 24, .76); }
.blueprint-heading { display: flex; align-items: baseline; gap: 20px; }
.blueprint-heading > span { color: #68dfd1; font: 750 10px 'Bahnschrift', sans-serif; letter-spacing: .2em; white-space: nowrap; }
.blueprint-heading h2 { margin: 0; color: #91d2ff; font-size: clamp(22px, 2vw, 34px); letter-spacing: .03em; text-shadow: 0 0 18px rgba(50, 163, 255, .3); white-space: nowrap; }
.blueprint-heading p { margin: 0; color: #278fe8; font-size: 12px; white-space: nowrap; }
.blueprint-category { display: inline-flex; align-items: center; gap: 9px; color: var(--blue); }
.blueprint-category strong { font-size: 16px; letter-spacing: .05em; }
.blueprint-legend { display: flex; justify-content: flex-end; gap: 28px; }
.blueprint-legend span { display: inline-flex; align-items: center; gap: 8px; color: #a8c5de; font-size: 12px; white-space: nowrap; }
.blueprint-legend i { width: 18px; height: 12px; border-radius: 3px; }
.blueprint-legend .normal { background: var(--normal); box-shadow: 0 0 9px rgba(84, 206, 121, .35); }
.blueprint-legend .warning { background: var(--warning); box-shadow: 0 0 9px rgba(255, 157, 41, .35); }
.blueprint-legend .danger { background: var(--danger); box-shadow: 0 0 9px rgba(255, 93, 85, .35); }
.blueprint-stage { position: relative; z-index: 2; display: grid; min-height: 900px; grid-template-columns: 180px minmax(0, 1fr); align-items: stretch; gap: 12px; padding: 20px 22px 0; }
.is-focused .blueprint-stage { min-height: 620px; grid-template-columns: 1fr; }
.blueprint-summary { align-self: start; overflow: hidden; border: 1px solid rgba(46, 150, 234, .45); border-radius: 12px; background: rgba(3, 20, 39, .78); box-shadow: inset 0 0 24px rgba(28, 117, 193, .12); }
.blueprint-summary > div { display: grid; min-height: 78px; grid-template-columns: 34px 1fr; align-items: center; gap: 8px; padding: 12px 14px; border-bottom: 1px solid rgba(46, 150, 234, .22); color: var(--blue); }
.blueprint-summary span { display: flex; flex-direction: column; color: #9fc6e6; font-size: 10px; }
.blueprint-summary strong { margin-top: 5px; color: #f4f8fb; font: 650 20px 'Bahnschrift', sans-serif; }
.blueprint-summary small { margin-left: 3px; font-size: 10px; }
.blueprint-summary footer { padding: 11px 14px; color: #6793bc; font-size: 8px; }
.blueprint-yard { position: relative; min-width: 0; padding: 18px 20px 28px; }
.yard-grid { position: absolute; inset: 0; opacity: .45; background-image: linear-gradient(rgba(48, 135, 202, .09) 1px, transparent 1px), linear-gradient(90deg, rgba(48, 135, 202, .09) 1px, transparent 1px); background-size: 22px 22px; }
.warehouse-frame { position: relative; height: 100%; min-height: 810px; padding: 28px 36px; border: 2px solid rgba(111, 194, 255, .72); background: linear-gradient(180deg, rgba(12, 43, 76, .7), rgba(4, 26, 49, .88)); box-shadow: inset 0 0 28px rgba(45, 145, 229, .24), 0 0 15px rgba(38, 143, 230, .24); clip-path: polygon(2% 0, 98% 0, 100% 3%, 100% 97%, 98% 100%, 2% 100%, 0 97%, 0 3%); }
.is-focused .warehouse-frame { min-height: 540px; }
.warehouse-frame::before { position: absolute; inset: 10px; border: 1px dashed rgba(92, 173, 236, .32); content: ''; pointer-events: none; }
.frame-corners::before, .frame-corners::after { position: absolute; top: 14px; bottom: 14px; width: 18px; content: ''; border-block: 8px double rgba(148, 211, 255, .58); }
.frame-corners::before { left: 8px; border-left: 8px double rgba(148, 211, 255, .58); }
.frame-corners::after { right: 8px; border-right: 8px double rgba(148, 211, 255, .58); }
.zone-grid { position: relative; z-index: 3; display: grid; height: 100%; grid-template-columns: repeat(2, minmax(0, 1fr)); grid-template-rows: repeat(5, minmax(112px, 1fr)); gap: 18px 46px; }
.is-focused .zone-grid { grid-template-rows: 1fr; }
.is-focused.zones-2 .zone-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.is-focused.zones-8 .zone-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); grid-template-rows: repeat(2, minmax(190px, 1fr)); gap: 48px 38px; }
.blueprint-zone { position: relative; display: flex; min-width: 0; flex-direction: column; align-items: center; justify-content: center; padding: 16px 24px; cursor: pointer; overflow: hidden; border: 2px solid currentColor; border-radius: 10px; color: var(--normal); background: linear-gradient(145deg, rgba(22, 95, 58, .75), rgba(7, 43, 35, .92)); box-shadow: inset 0 0 38px rgba(73, 223, 112, .16), 0 0 12px currentColor; animation: blueprint-tile-in .52s both; animation-delay: var(--zone-delay); }
.blueprint-zone.is-warning { color: var(--warning); background: linear-gradient(145deg, rgba(133, 67, 10, .8), rgba(63, 36, 8, .94)); box-shadow: inset 0 0 38px rgba(255, 151, 33, .18), 0 0 13px rgba(255, 142, 31, .72); }
.blueprint-zone.is-danger { color: var(--danger); background: linear-gradient(145deg, rgba(128, 35, 31, .82), rgba(62, 19, 22, .95)); box-shadow: inset 0 0 38px rgba(255, 72, 65, .22), 0 0 14px rgba(255, 72, 65, .7); }
.blueprint-zone:hover { z-index: 6; transform: translateY(-3px) scale(1.01); filter: brightness(1.1); }
.rack-pattern { position: absolute; inset: 12px; opacity: .23; background-image: repeating-linear-gradient(0deg, currentColor 0 1px, transparent 1px 18px), repeating-linear-gradient(90deg, currentColor 0 1px, transparent 1px 37px); mask-image: linear-gradient(#000, transparent 92%); }
.rack-pattern::after { position: absolute; inset: 7px; content: ''; background: repeating-linear-gradient(35deg, transparent 0 8px, currentColor 8px 9px, transparent 9px 18px); opacity: .45; }
.blueprint-zone > :not(.rack-pattern) { position: relative; z-index: 2; }
.zone-code { color: #fff; font: 760 clamp(20px, 2.1vw, 38px)/1 'Bahnschrift', sans-serif; letter-spacing: .04em; text-shadow: 0 2px 10px rgba(0, 0, 0, .6); }
.blueprint-zone strong { margin-top: 9px; color: #fff; font-size: clamp(13px, 1.15vw, 19px); }
.blueprint-zone small { margin-top: 8px; color: rgba(239, 247, 252, .72); font-size: 10px; }
.blueprint-zone b { margin-top: 10px; color: #fff; font: 750 clamp(21px, 2vw, 34px)/1 'Bahnschrift', sans-serif; }
.zone-meter { width: min(78%, 420px); height: 7px; margin-top: 15px; overflow: hidden; border-radius: 7px; background: rgba(255, 255, 255, .16); }
.zone-meter i { display: block; width: var(--zone-fill); height: 100%; border-radius: inherit; background: currentColor; box-shadow: 0 0 10px currentColor; }
.zone-arrow { position: absolute !important; right: 18px; bottom: 15px; }
.is-overview .blueprint-zone { padding: 11px 18px; }
.is-overview .zone-code { font-size: clamp(18px, 1.65vw, 28px); }
.is-overview .blueprint-zone strong { margin-top: 6px; font-size: clamp(11px, .9vw, 15px); }
.is-overview .blueprint-zone small { display: none; }
.is-overview .blueprint-zone b { margin-top: 7px; font-size: clamp(18px, 1.55vw, 27px); }
.is-overview .zone-meter { display: none; }
.central-lane { position: absolute; z-index: 4; top: 24px; bottom: 64px; left: 50%; display: flex; width: 34px; flex-direction: column; align-items: center; justify-content: space-around; color: #ffd51e; transform: translateX(-50%); pointer-events: none; }
.central-lane i { width: 1px; flex: 1; margin: 7px 0; border-left: 1px dashed rgba(126, 201, 255, .58); }
.cross-lanes { position: absolute; z-index: 2; inset: 28px 30px 64px; display: flex; flex-direction: column; justify-content: space-evenly; pointer-events: none; }
.cross-lanes span { height: 22px; margin-inline: -16px; border-block: 1px dashed rgba(127, 198, 247, .32); background: rgba(11, 50, 83, .5); }
.dock { position: absolute; z-index: 4; top: 27%; display: grid; color: #8fbce2; }
.dock svg { padding: 5px; border: 1px solid rgba(101, 177, 235, .38); }
.dock span { margin-bottom: 8px; font-size: 10px; line-height: 1.5; }
.dock-left { left: 20px; justify-items: end; }
.dock-right { right: 20px; justify-items: start; }
.entrance { position: absolute; z-index: 4; bottom: 7px; left: 50%; display: flex; flex-direction: column; align-items: center; color: var(--blue); transform: translateX(-50%); }
.entrance span { font: 800 23px/1 sans-serif; letter-spacing: 4px; }
.entrance strong { margin-top: 3px; color: #9ec8e9; font-size: 10px; font-weight: 500; white-space: nowrap; }
.blueprint-footer { position: relative; z-index: 4; display: flex; min-height: 72px; align-items: center; justify-content: space-between; gap: 22px; padding: 8px 30px 18px; }
.north { display: inline-flex; align-items: center; gap: 8px; color: var(--blue); }
.scale { display: flex; align-items: flex-end; color: #80b5df; font-size: 9px; }
.scale i { width: 42px; height: 7px; margin-inline: -8px 4px; border-bottom: 2px solid #32a9fb; border-right: 1px solid #32a9fb; }
.route-legend { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 18px; padding: 9px 14px; border: 1px solid rgba(46, 150, 234, .35); border-radius: 10px; color: #90b9dc; font-size: 9px; background: rgba(3, 20, 39, .75); }
.route-legend span { display: inline-flex; align-items: center; gap: 7px; }
.route-legend .lane { width: 28px; border-top: 1px dashed #7fc9ff; }
.route-legend .safe { width: 28px; height: 8px; background: repeating-linear-gradient(135deg, transparent 0 4px, #2d9cf6 4px 5px); border: 1px solid #2d9cf6; }
.route-legend svg:last-child { color: #ffc400; }
@keyframes blueprint-tile-in { from { opacity: 0; transform: scale(.965); } to { opacity: 1; transform: scale(1); } }
@media (max-width: 1250px) {
  .blueprint-header { grid-template-columns: 1fr auto; }
  .blueprint-category { display: none; }
  .blueprint-stage { grid-template-columns: 150px minmax(0, 1fr); padding-inline: 14px; }
  .blueprint-yard { padding-inline: 14px; }
  .is-focused.zones-8 .zone-grid { gap: 40px 28px; }
}
@media (max-width: 920px) {
  .blueprint-heading p { display: none; }
  .blueprint-stage { min-height: 860px; grid-template-columns: 1fr; }
  .blueprint-summary { display: none; }
  .blueprint-yard { padding-inline: 14px; }
  .dock { display: none; }
  .warehouse-frame { min-height: 610px; padding-inline: 22px; }
  .is-focused.zones-8 .zone-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); grid-template-rows: repeat(4, minmax(150px, 1fr)); }
  .is-focused.zones-8 .warehouse-frame { min-height: 790px; }
  .blueprint-footer { flex-wrap: wrap; }
  .route-legend { width: 100%; justify-content: center; }
}
@media (max-width: 640px) {
  .blueprint-header { grid-template-columns: 1fr; padding: 16px; }
  .blueprint-heading { gap: 10px; flex-wrap: wrap; }
  .blueprint-heading > span { width: 100%; }
  .blueprint-legend { justify-content: flex-start; gap: 13px; }
  .blueprint-legend span { font-size: 9px; }
  .blueprint-legend i { width: 12px; height: 9px; }
  .blueprint-stage { min-height: 0; padding: 10px 8px 0; }
  .blueprint-yard { padding: 8px 0 12px; }
  .warehouse-frame, .is-focused .warehouse-frame, .is-focused.zones-8 .warehouse-frame { min-height: 0; padding: 18px 14px; clip-path: none; }
  .zone-grid, .is-focused.zones-2 .zone-grid, .is-focused.zones-8 .zone-grid { height: auto; grid-template-columns: 1fr; grid-template-rows: none; gap: 22px; }
  .blueprint-zone, .is-overview .blueprint-zone { min-height: 150px; }
  .central-lane, .cross-lanes { display: none; }
  .blueprint-footer { padding-inline: 16px; }
  .north, .scale { display: none; }
}
@media (prefers-reduced-motion: reduce) { .blueprint-zone { animation: none; transition: none; } }
</style>

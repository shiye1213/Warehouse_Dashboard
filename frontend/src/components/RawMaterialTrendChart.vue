<script setup>
import * as echarts from 'echarts/core'
import { AriaComponent, GridComponent, TooltipComponent } from 'echarts/components'
import { BarChart, LineChart } from 'echarts/charts'
import { CanvasRenderer } from 'echarts/renderers'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useProjectRefresh } from '../composables/useProjectRefresh'

echarts.use([AriaComponent, GridComponent, TooltipComponent, BarChart, LineChart, CanvasRenderer])

const props = defineProps({
  rows: { type: Array, default: () => [] },
  series: { type: Array, default: () => [] },
  height: { type: Number, default: 260 },
  showLegend: { type: Boolean, default: true },
  unit: { type: String, default: '' },
  showAxisUnit: { type: Boolean, default: true },
  categoryBoundaryGap: { type: Boolean, default: false },
  axisBottom: { type: Number, default: 8 },
  xAxisLabelMargin: { type: Number, default: 8 },
  niceYAxis: { type: Boolean, default: false },
  yAxisSplitNumber: { type: Number, default: 5 },
  dimensional: { type: Boolean, default: false },
  light: { type: Boolean, default: false },
})

const chartEl = ref(null)
const initializing = ref(true)
const { refreshing, refreshVersion } = useProjectRefresh()
let chart
let observer
let initialLoadingTimer
let refreshRenderTimer
const reduceMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
const isLoading = computed(() => initializing.value || refreshing.value)
const seriesValues = computed(() => props.series.flatMap((item) => props.rows.map((row) => (
  Number(row[item.key] || 0) * (item.percent ? 100 : 1)
))))

function getNiceInterval(maxValue, splitNumber) {
  if (!Number.isFinite(maxValue) || maxValue <= 0) return 1
  const roughInterval = maxValue / Math.max(1, splitNumber)
  const magnitude = 10 ** Math.floor(Math.log10(roughInterval))
  const normalized = roughInterval / magnitude
  const niceNormalized = [1, 1.5, 2, 2.5, 3, 5, 10].find((candidate) => normalized <= candidate) || 10
  return niceNormalized * magnitude
}

const yAxisScale = computed(() => {
  if (!props.niceYAxis) return null
  const dataMax = Math.max(0, ...seriesValues.value)
  const interval = getNiceInterval(dataMax, props.yAxisSplitNumber)
  const max = Math.max(interval, Math.ceil(dataMax / interval) * interval)
  return { min: 0, max: Number(max.toPrecision(12)), interval: Number(interval.toPrecision(12)) }
})

function formatAxisValue(value) {
  const numericValue = Number(value)
  if (Math.abs(numericValue) >= 1000) {
    return `${Number((numericValue / 1000).toFixed(1))}k`
  }
  const interval = yAxisScale.value?.interval || 1
  const decimals = interval < 1 ? Math.min(3, Math.max(1, Math.ceil(-Math.log10(interval)))) : 0
  return Number(numericValue.toFixed(decimals)).toString()
}

const chartAriaLabel = computed(() => {
  const names = props.series.map((item) => item.name).join('、')
  const range = props.rows.length > 1
    ? `${props.rows[0]?.date || props.rows[0]?.label || ''}至${props.rows.at(-1)?.date || props.rows.at(-1)?.label || ''}`
    : ''
  return `${range}${names}趋势图，共 ${props.rows.length} 个数据点${props.unit ? `，单位${props.unit}` : ''}`
})

const options = computed(() => ({
  animation: !reduceMotion,
  animationDuration: reduceMotion ? 0 : 2800,
  animationEasing: 'cubicInOut',
  animationDurationUpdate: reduceMotion ? 0 : 1800,
  animationEasingUpdate: 'cubicInOut',
  aria: { enabled: true, description: chartAriaLabel.value },
  color: props.series.map((item) => item.color),
  grid: { left: props.showAxisUnit ? 12 : 7, right: 14, top: props.showLegend ? 42 : 18, bottom: props.axisBottom, containLabel: true },
  tooltip: {
    trigger: 'axis',
    appendToBody: true,
    confine: false,
    enterable: false,
    transitionDuration: reduceMotion ? 0 : .16,
    backgroundColor: props.light ? 'rgba(255, 255, 255, .97)' : 'rgba(4, 17, 19, .96)',
    borderColor: props.light ? 'rgba(58, 99, 116, .24)' : props.dimensional ? 'rgba(176, 224, 218, .32)' : 'rgba(155, 196, 190, .18)',
    borderWidth: 1,
    extraCssText: `z-index:80!important;pointer-events:none;${props.dimensional ? `box-shadow:0 14px 32px ${props.light ? 'rgba(58,83,96,.18)' : 'rgba(0,0,0,.42)'},inset 0 1px rgba(255,255,255,.5);backdrop-filter:blur(10px);border-radius:9px;` : ''}`,
    textStyle: { color: props.light ? '#17313f' : '#e8f2ef', fontSize: 12 },
    axisPointer: { type: 'line', lineStyle: { color: props.light ? 'rgba(8, 127, 131, .3)' : 'rgba(91, 223, 190, .28)' } },
    valueFormatter: (value) => `${Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 3 })}${props.unit ? ` ${props.unit}` : ''}`,
  },
  xAxis: {
    type: 'category',
    boundaryGap: props.categoryBoundaryGap || props.series.some((item) => item.type === 'bar'),
    data: props.rows.map((item) => item.date?.slice(5).replace('-', '/') || item.label || ''),
    axisLine: { lineStyle: { color: props.light ? 'rgba(58, 99, 116, .25)' : props.dimensional ? 'rgba(154, 205, 207, .3)' : 'rgba(150, 184, 180, .14)', width: props.dimensional ? 1.4 : 1 } },
    axisTick: { show: false },
    axisLabel: { color: props.light ? '#536b78' : '#6f8584', fontSize: 10, interval: Math.max(0, Math.floor(props.rows.length / 7) - 1), margin: props.xAxisLabelMargin, hideOverlap: true },
  },
  yAxis: {
    type: 'value',
    name: props.showAxisUnit ? props.unit : '',
    nameGap: 12,
    nameTextStyle: { color: props.light ? '#536b78' : '#71889a', fontSize: 10, align: 'right' },
    splitNumber: props.niceYAxis ? props.yAxisSplitNumber : 4,
    ...(yAxisScale.value || {}),
    axisLabel: { color: props.light ? '#536b78' : '#71889a', fontSize: 10, hideOverlap: props.niceYAxis ? false : true, showMinLabel: true, showMaxLabel: true, margin: 9, formatter: props.niceYAxis ? formatAxisValue : undefined },
    splitLine: { lineStyle: { color: props.light ? 'rgba(58, 99, 116, .13)' : props.dimensional ? 'rgba(133, 190, 205, .16)' : 'rgba(133, 169, 187, .11)', type: props.dimensional ? 'solid' : 'dashed' } },
  },
  series: props.series.flatMap((item, index) => {
    const type = item.type || 'line'
    const isLine = type === 'line'
    const data = props.rows.map((row) => Number(row[item.key] || 0) * (item.percent ? 100 : 1))
    const mainSeries = {
      name: item.name,
      type,
      smooth: item.smooth !== false,
      symbol: item.symbol || 'circle',
      showSymbol: props.rows.length < 10,
      symbolSize: props.dimensional ? 7 : 5,
      barMaxWidth: 20,
      data,
      zlevel: isLine ? 1 : 0,
      z: props.dimensional ? 4 + index : 2,
      lineStyle: {
        width: item.lineWidth || 2.5,
        type: item.lineStyle || 'solid',
        color: item.color,
        cap: 'round',
        join: 'round',
        shadowBlur: props.dimensional ? 11 : 0,
        shadowColor: props.dimensional ? `${item.color}88` : 'transparent',
        shadowOffsetY: props.dimensional ? 3 : 0,
      },
      itemStyle: {
        color: item.color,
        borderColor: props.dimensional && isLine ? (props.light ? '#ffffff' : '#eaffff') : 'transparent',
        borderWidth: props.dimensional && isLine ? 1.2 : 0,
        borderRadius: item.type === 'bar' ? [4, 4, 0, 0] : 0,
        shadowBlur: props.dimensional && isLine ? 9 : 0,
        shadowColor: props.dimensional ? `${item.color}99` : 'transparent',
      },
      emphasis: props.dimensional && isLine ? {
        focus: 'series',
        scale: 1.65,
        lineStyle: { width: (item.lineWidth || 2.5) + .8, shadowBlur: 16, shadowColor: `${item.color}bb` },
        itemStyle: { borderColor: '#ffffff', borderWidth: 1.5, shadowBlur: 14, shadowColor: item.color },
      } : undefined,
      areaStyle: item.area || (props.dimensional && isLine) ? {
        opacity: 1,
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: `${item.color}${item.area ? '42' : '20'}` },
          { offset: .45, color: `${item.color}${item.area ? '18' : '0c'}` },
          { offset: 1, color: `${item.color}00` },
        ]),
      } : undefined,
    }

    if (!props.dimensional || !isLine) return [mainSeries]
    return [
      {
        name: `${item.name}-depth`,
        type: 'line',
        data,
        smooth: item.smooth !== false,
        symbol: 'none',
        showSymbol: false,
        silent: true,
        zlevel: 1,
        z: 1 + index,
        tooltip: { show: false },
        emphasis: { disabled: true },
        lineStyle: {
          width: (item.lineWidth || 2.5) + 5,
          type: item.lineStyle || 'solid',
          color: `${item.color}18`,
          cap: 'round',
          join: 'round',
          shadowBlur: 14,
          shadowColor: `${item.color}70`,
          shadowOffsetY: 7,
        },
      },
      mainSeries,
    ]
  }),
}))

function render() {
  if (!chartEl.value || chartEl.value.clientWidth <= 0 || chartEl.value.clientHeight <= 0) return
  if (chart?.isDisposed?.()) chart = undefined
  if (!chart) chart = echarts.init(chartEl.value)
  chart.setOption(options.value, true)
}

function resizeOrRender() {
  if (!chartEl.value || chartEl.value.clientWidth <= 0 || chartEl.value.clientHeight <= 0) return
  if (!chart || chart.isDisposed?.()) render()
  else chart.resize()
}

watch(options, () => nextTick(render), { deep: true })
watch(refreshVersion, () => {
  if (chart && !chart.isDisposed?.()) chart.clear()
  window.clearTimeout(refreshRenderTimer)
  refreshRenderTimer = window.setTimeout(() => nextTick(render), reduceMotion ? 0 : 180)
})
onMounted(() => {
  render()
  observer = new ResizeObserver(resizeOrRender)
  observer.observe(chartEl.value)
  initialLoadingTimer = window.setTimeout(() => { initializing.value = false }, reduceMotion ? 0 : 2800)
})
onBeforeUnmount(() => {
  window.clearTimeout(initialLoadingTimer)
  window.clearTimeout(refreshRenderTimer)
  observer?.disconnect()
  const activeChart = chart
  chart = undefined
  if (activeChart && !activeChart.isDisposed?.()) activeChart.dispose()
})
</script>

<template>
  <figure class="trend-chart-shell" :class="{ 'is-dimensional': dimensional, 'is-light': light }" :style="{ height: `${height}px` }" role="img" :aria-label="`${chartAriaLabel}${isLoading ? '，数据更新中' : ''}`" :aria-busy="isLoading" :data-y-axis-max="yAxisScale?.max" :data-y-axis-interval="yAxisScale?.interval">
    <div v-if="showLegend" class="trend-chart-legend" aria-hidden="true">
      <span v-for="item in series" :key="item.name" class="trend-legend-item">
        <i class="trend-legend-line" :class="item.lineStyle || 'solid'" :style="{ '--series-color': item.color }">
          <b :class="item.symbol || 'circle'" />
        </i>
        {{ item.name }}
      </span>
    </div>
    <div ref="chartEl" class="chart-canvas" />
  </figure>
</template>

<style scoped>
.trend-chart-shell {
  position: relative;
  width: 100%;
  margin: 0;
}

.trend-chart-shell.is-dimensional { isolation: isolate; perspective: 620px; }
.trend-chart-shell.is-dimensional::before {
  position: absolute;
  z-index: 0;
  top: 34px;
  right: 10px;
  bottom: 9px;
  left: 31px;
  border: 1px solid color-mix(in srgb, var(--rm-accent, #35d3c7) 16%, transparent);
  border-radius: 8px;
  content: "";
  pointer-events: none;
  background:
    linear-gradient(color-mix(in srgb, var(--rm-accent, #35d3c7) 12%, transparent) 1px, transparent 1px) 0 100% / 100% 25%,
    linear-gradient(90deg, color-mix(in srgb, var(--rm-accent, #35d3c7) 7%, transparent) 1px, transparent 1px) 0 0 / 12.5% 100%,
    linear-gradient(180deg, color-mix(in srgb, var(--rm-accent, #35d3c7) 9%, transparent), rgba(1,13,22,.03) 62%, rgba(1,8,14,.2));
  box-shadow: inset 0 1px rgba(221,249,250,.05), inset 0 -16px 24px rgba(1,8,15,.18), 0 11px 17px rgba(0,0,0,.16);
  transform: rotateX(4deg);
  transform-origin: center bottom;
}
.trend-chart-shell.is-dimensional::after {
  position: absolute;
  z-index: 0;
  right: 16px;
  bottom: 5px;
  left: 35px;
  height: 9px;
  border-radius: 50%;
  content: "";
  pointer-events: none;
  opacity: .6;
  background: radial-gradient(ellipse, color-mix(in srgb, var(--rm-accent, #35d3c7) 22%, transparent), rgba(0,0,0,0) 68%);
  filter: blur(2px);
  transform: rotateX(68deg);
}
.trend-chart-shell.is-dimensional.is-light::before {
  background:
    linear-gradient(color-mix(in srgb, var(--rm-accent, #087f83) 12%, transparent) 1px, transparent 1px) 0 100% / 100% 25%,
    linear-gradient(90deg, color-mix(in srgb, var(--rm-accent, #087f83) 7%, transparent) 1px, transparent 1px) 0 0 / 12.5% 100%,
    linear-gradient(180deg, rgba(255,255,255,.28), rgba(220,235,240,.12));
  box-shadow: inset 0 1px rgba(255,255,255,.82), inset 0 -16px 24px rgba(83,110,122,.06), 0 10px 16px rgba(60,90,105,.08);
}

.chart-canvas {
  position: relative;
  z-index: 1;
  width: 100%;
  height: 100%;
}
.chart-canvas :deep(canvas[data-zr-dom-id="zr_1"]) {
  clip-path: inset(0 0 0 0);
  animation: trend-line-reveal 2.8s cubic-bezier(.4,0,.2,1) both;
}

@keyframes trend-line-reveal {
  from { clip-path: inset(0 100% 0 0); }
  to { clip-path: inset(0 0 0 0); }
}

.trend-chart-legend {
  position: absolute;
  z-index: 3;
  top: 7px;
  right: 12px;
  display: flex;
  align-items: center;
  gap: 16px;
  color: #a7bac9;
  font-size: 11px;
  line-height: 1;
  pointer-events: none;
}
.trend-chart-shell.is-light .trend-chart-legend { color: #425d6b; }
.trend-chart-shell.is-light .trend-legend-line b { border-color: #f6fafb; }

.trend-legend-item {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  white-space: nowrap;
}

.trend-legend-line {
  position: relative;
  display: inline-block;
  width: 24px;
  height: 0;
  border-top: 2px solid var(--series-color);
  filter: drop-shadow(0 3px 3px color-mix(in srgb, var(--series-color) 45%, transparent));
}

.trend-legend-line.dashed { border-top-style: dashed; }
.trend-legend-line.dotted { border-top-style: dotted; }
.trend-legend-line b {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 6px;
  height: 6px;
  border: 1px solid #081521;
  background: var(--series-color);
  transform: translate(-50%, -50%);
}
.trend-legend-line b.circle { border-radius: 50%; }
.trend-legend-line b.diamond { transform: translate(-50%, -50%) rotate(45deg); }
.trend-legend-line b.rect { border-radius: 1px; }

@media (max-width: 520px) {
  .trend-chart-legend { gap: 10px; font-size: 10px; }
  .trend-legend-line { width: 19px; }
  .trend-chart-shell.is-dimensional::before { transform: none; }
}

@media (prefers-reduced-motion: reduce) {
  .chart-canvas :deep(canvas[data-zr-dom-id="zr_1"]) { animation: none; }
  .trend-chart-shell.is-dimensional::before, .trend-chart-shell.is-dimensional::after { transform: none; }
}
</style>

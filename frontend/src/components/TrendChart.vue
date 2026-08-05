<script setup>
import * as echarts from 'echarts/core'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { BarChart, LineChart } from 'echarts/charts'
import { CanvasRenderer } from 'echarts/renderers'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

echarts.use([GridComponent, LegendComponent, TooltipComponent, BarChart, LineChart, CanvasRenderer])

const props = defineProps({
  rows: { type: Array, default: () => [] },
  series: { type: Array, default: () => [] },
  height: { type: Number, default: 260 },
  showLegend: { type: Boolean, default: true },
  drawAnimation: { type: Boolean, default: false },
  compact: { type: Boolean, default: false },
})

const chartEl = ref(null)
const drawProgress = ref(props.drawAnimation ? 0 : props.rows.length)
let chart
let observer
let drawAnimationFrame

const options = computed(() => ({
  animationDuration: props.drawAnimation ? 0 : 650,
  animationDurationUpdate: props.drawAnimation ? 90 : 300,
  animationEasing: 'cubicOut',
  animationEasingUpdate: 'linear',
  color: props.series.map((item) => item.color),
  grid: props.compact
    ? { left: 2, right: 2, top: 7, bottom: 2, containLabel: false }
    : { left: 12, right: 14, top: props.showLegend ? 44 : 18, bottom: 8, containLabel: true },
  legend: props.showLegend ? {
    top: 2,
    right: 4,
    icon: 'roundRect',
    itemWidth: 12,
    itemHeight: 4,
    textStyle: { color: '#8ea4a4', fontSize: 11 },
  } : undefined,
  tooltip: {
    trigger: 'axis',
    backgroundColor: 'rgba(4, 17, 19, .96)',
    borderColor: 'rgba(155, 196, 190, .18)',
    textStyle: { color: '#e8f2ef', fontSize: 12 },
    axisPointer: { type: 'line', lineStyle: { color: 'rgba(91, 223, 190, .28)' } },
  },
  xAxis: {
    type: 'category',
    boundaryGap: props.series.some((item) => item.type === 'bar'),
    data: props.rows.map((item) => item.date?.slice(5).replace('-', '/') || item.label || ''),
    axisLine: { show: !props.compact, lineStyle: { color: 'rgba(150, 184, 180, .14)' } },
    axisTick: { show: false },
    axisLabel: { show: !props.compact, color: '#6f8584', fontSize: 10, interval: Math.max(0, Math.floor(props.rows.length / 7) - 1) },
  },
  yAxis: {
    type: 'value',
    splitNumber: 4,
    axisLabel: { show: !props.compact, color: '#6f8584', fontSize: 10 },
    splitLine: { lineStyle: { color: props.compact ? 'rgba(115, 170, 211, .07)' : 'rgba(150, 184, 180, .08)' } },
  },
  series: props.series.map((item) => ({
    name: item.name,
    type: item.type || 'line',
    smooth: item.smooth !== false,
    symbol: item.symbol || 'circle',
    showSymbol: !props.compact && props.rows.length < 10,
    symbolSize: 5,
    barMaxWidth: 20,
    data: props.rows.map((row, index) => (
      !props.drawAnimation || index < drawProgress.value
        ? Number(row[item.key] || 0) * (item.percent ? 100 : 1)
        : null
    )),
    lineStyle: { width: props.compact ? 1.7 : 2, type: item.lineStyle || 'solid' },
    itemStyle: { borderRadius: item.type === 'bar' ? [4, 4, 0, 0] : 0 },
    areaStyle: item.area ? {
      opacity: 1,
      color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
        { offset: 0, color: `${item.color}35` },
        { offset: 1, color: `${item.color}00` },
      ]),
    } : undefined,
  })),
}))

function render() {
  if (!chartEl.value) return
  if (!chart) chart = echarts.init(chartEl.value)
  chart.setOption(options.value, true)
}

function playDrawAnimation() {
  cancelAnimationFrame(drawAnimationFrame)
  const pointCount = props.rows.length
  const reducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  if (!props.drawAnimation || reducedMotion || pointCount < 2) {
    drawProgress.value = pointCount
    return
  }

  drawProgress.value = 0
  const duration = 1650
  let startedAt
  let lastCount = -1
  const tick = (timestamp) => {
    startedAt ??= timestamp
    const progress = Math.min((timestamp - startedAt) / duration, 1)
    const easedProgress = 1 - Math.pow(1 - progress, 2.4)
    const nextCount = Math.min(pointCount, Math.ceil(easedProgress * pointCount))
    if (nextCount !== lastCount) {
      drawProgress.value = nextCount
      lastCount = nextCount
    }
    if (progress < 1) drawAnimationFrame = requestAnimationFrame(tick)
  }
  drawAnimationFrame = requestAnimationFrame(tick)
}

watch(options, () => nextTick(render), { deep: true })
watch(() => props.rows, () => nextTick(playDrawAnimation), { deep: true })
onMounted(() => {
  render()
  observer = new ResizeObserver(() => chart?.resize())
  observer.observe(chartEl.value)
  playDrawAnimation()
})
onBeforeUnmount(() => {
  cancelAnimationFrame(drawAnimationFrame)
  observer?.disconnect()
  chart?.dispose()
})
</script>

<template><div ref="chartEl" class="chart-canvas" :style="{ height: `${height}px` }" /></template>

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
})

const chartEl = ref(null)
let chart
let observer

const options = computed(() => ({
  animationDuration: 650,
  animationEasing: 'cubicOut',
  color: props.series.map((item) => item.color),
  grid: { left: 12, right: 14, top: props.showLegend ? 44 : 18, bottom: 8, containLabel: true },
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
    axisLine: { lineStyle: { color: 'rgba(150, 184, 180, .14)' } },
    axisTick: { show: false },
    axisLabel: { color: '#6f8584', fontSize: 10, interval: Math.max(0, Math.floor(props.rows.length / 7) - 1) },
  },
  yAxis: {
    type: 'value',
    splitNumber: 4,
    axisLabel: { color: '#6f8584', fontSize: 10 },
    splitLine: { lineStyle: { color: 'rgba(150, 184, 180, .08)' } },
  },
  series: props.series.map((item) => ({
    name: item.name,
    type: item.type || 'line',
    smooth: item.smooth !== false,
    symbol: item.symbol || 'circle',
    showSymbol: props.rows.length < 10,
    symbolSize: 5,
    barMaxWidth: 20,
    data: props.rows.map((row) => Number(row[item.key] || 0) * (item.percent ? 100 : 1)),
    lineStyle: { width: 2, type: item.lineStyle || 'solid' },
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

watch(options, () => nextTick(render), { deep: true })
onMounted(() => {
  render()
  observer = new ResizeObserver(() => chart?.resize())
  observer.observe(chartEl.value)
})
onBeforeUnmount(() => { observer?.disconnect(); chart?.dispose() })
</script>

<template><div ref="chartEl" class="chart-canvas" :style="{ height: `${height}px` }" /></template>

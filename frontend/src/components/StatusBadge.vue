<script setup>
import { computed } from 'vue'

const props = defineProps({ value: { type: String, default: '' } })
const tone = computed(() => {
  if (['正常', '已关闭', '达标', '在线'].some((item) => props.value.includes(item))) return 'success'
  if (['紧急', '超时', '高负荷', '异常'].some((item) => props.value.includes(item))) return 'danger'
  if (['偏高', '重要', '处理中', '关注', '忙碌'].some((item) => props.value.includes(item))) return 'warning'
  return 'neutral'
})
</script>

<template><span class="status-badge" :class="`is-${tone}`"><i />{{ value || '未标记' }}</span></template>

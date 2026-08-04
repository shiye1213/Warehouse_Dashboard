<script setup>
import { computed } from 'vue'
import { ArrowDownRight, ArrowUpRight, Minus } from 'lucide-vue-next'

const props = defineProps({
  label: { type: String, required: true },
  value: { type: [String, Number], required: true },
  unit: { type: String, default: '' },
  note: { type: String, default: '' },
  delta: { type: Number, default: null },
  inverse: { type: Boolean, default: false },
  tone: { type: String, default: 'mint' },
  compact: { type: Boolean, default: false },
})

const deltaState = computed(() => {
  if (props.delta === null || Math.abs(props.delta) < 0.01) return 'flat'
  const rising = props.delta > 0
  return (rising && !props.inverse) || (!rising && props.inverse) ? 'good' : 'bad'
})
</script>

<template>
  <article class="metric-card" :class="[`tone-${tone}`, { compact }]">
    <div class="metric-top"><span>{{ label }}</span><slot name="icon" /></div>
    <div class="metric-value"><strong>{{ value }}</strong><small v-if="unit">{{ unit }}</small></div>
    <div class="metric-foot">
      <span v-if="delta !== null" class="metric-delta" :class="deltaState">
        <ArrowUpRight v-if="delta > 0" :size="14" />
        <ArrowDownRight v-else-if="delta < 0" :size="14" />
        <Minus v-else :size="14" />
        {{ Math.abs(delta).toFixed(1) }}%
      </span>
      <span>{{ note }}</span>
    </div>
  </article>
</template>

<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps({
  value: { type: [Number, String], default: 0 },
  formatter: { type: Function, default: (value) => String(Math.round(value)) },
  animationKey: { type: [Number, String], default: 0 },
  duration: { type: Number, default: 2000 },
})

const displayedValue = ref('0')
let animationFrame

function finalValue() {
  return Number(props.value || 0)
}

function render(value) {
  displayedValue.value = props.formatter(value)
}

function animate() {
  window.cancelAnimationFrame(animationFrame)

  const target = finalValue()
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches || props.duration <= 0) {
    render(target)
    return
  }

  const startedAt = performance.now()
  render(0)

  const step = (timestamp) => {
    const progress = Math.min((timestamp - startedAt) / props.duration, 1)
    const easedProgress = progress < 0.5
      ? 4 * progress ** 3
      : 1 - ((-2 * progress + 2) ** 3) / 2
    render(target * easedProgress)
    if (progress < 1) animationFrame = window.requestAnimationFrame(step)
    else render(target)
  }

  animationFrame = window.requestAnimationFrame(step)
}

watch([() => props.value, () => props.animationKey], animate, { immediate: true })

onBeforeUnmount(() => window.cancelAnimationFrame(animationFrame))
</script>

<template>
  <span class="animated-number">{{ displayedValue }}</span>
</template>

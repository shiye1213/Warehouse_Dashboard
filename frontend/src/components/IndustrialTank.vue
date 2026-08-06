<script setup>
import { computed, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'

const props = defineProps({
  fillRate: { type: Number, default: 0 },
  materialForm: { type: String, default: 'granule' },
  animationKey: { type: [Number, String], default: 0 },
})

const uid = useId().replace(/:/g, '')
const clipId = `tank-interior-${uid}`
const metalId = `tank-metal-${uid}`
const domeId = `tank-dome-${uid}`
const liquidId = `tank-liquid-${uid}`
const clampedFill = computed(() => Math.min(1, Math.max(0, Number(props.fillRate || 0))))
const reduceMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
const displayedFill = ref(reduceMotion ? clampedFill.value : 0)
let riseFrame

function riseFromBottom() {
  window.cancelAnimationFrame(riseFrame)
  if (reduceMotion) {
    displayedFill.value = clampedFill.value
    return
  }
  displayedFill.value = 0
  riseFrame = window.requestAnimationFrame(() => {
    riseFrame = window.requestAnimationFrame(() => {
      displayedFill.value = clampedFill.value
    })
  })
}

const liquidY = computed(() => 155 - displayedFill.value * 113)
const liquidHeight = computed(() => 155 - liquidY.value)
const liquidSurfaceRadius = computed(() => liquidY.value <= 124
  ? 32
  : Math.max(7, 32 * ((155 - liquidY.value) / 31)))

watch(clampedFill, (value) => { displayedFill.value = value })
watch(() => props.animationKey, riseFromBottom)
onMounted(riseFromBottom)
onBeforeUnmount(() => window.cancelAnimationFrame(riseFrame))
</script>

<template>
  <svg
    class="industrial-tank"
    :class="`is-${materialForm}`"
    viewBox="0 0 120 190"
    role="presentation"
    focusable="false"
    aria-hidden="true"
  >
    <defs>
      <linearGradient :id="metalId" x1="0" x2="1">
        <stop offset="0" stop-color="#536b78" />
        <stop offset=".16" stop-color="#b7cbd2" />
        <stop offset=".34" stop-color="#f4fbfc" />
        <stop offset=".5" stop-color="#c5d7dc" />
        <stop offset=".68" stop-color="#8da6b0" />
        <stop offset=".84" stop-color="#dce9ec" />
        <stop offset="1" stop-color="#506a77" />
      </linearGradient>
      <linearGradient :id="domeId" x1="0" x2="0" y1="0" y2="1">
        <stop offset="0" stop-color="#f8fdfe" />
        <stop offset=".46" stop-color="#b9cdd3" />
        <stop offset="1" stop-color="#597481" />
      </linearGradient>
      <linearGradient :id="liquidId" x1="0" x2="0" y1="0" y2="1">
        <stop offset="0" stop-color="var(--tank-fill-top)" />
        <stop offset=".48" stop-color="var(--tank-fill-mid)" />
        <stop offset="1" stop-color="var(--tank-fill-bottom)" />
      </linearGradient>
      <clipPath :id="clipId">
        <path d="M28 42 Q60 29 92 42 L92 124 L73 155 H47 L28 124 Z" />
      </clipPath>
      <filter :id="`tank-shadow-${uid}`" x="-30%" y="-20%" width="160%" height="150%">
        <feDropShadow dx="0" dy="5" stdDeviation="4" flood-color="#000b12" flood-opacity=".7" />
      </filter>
    </defs>

    <ellipse cx="60" cy="178" rx="47" ry="7" fill="rgba(0, 7, 13, .55)" />

    <g :filter="`url(#tank-shadow-${uid})`" transform="translate(-3 0) scale(1.05 1)">
      <g :clip-path="`url(#${clipId})`">
        <path d="M25 34 H95 V159 H25 Z" :fill="`url(#${metalId})`" opacity=".48" />
        <rect
          class="industrial-tank__liquid"
          x="27"
          :y="liquidY"
          width="66"
          :height="liquidHeight"
          :fill="`url(#${liquidId})`"
        />
        <ellipse
          class="industrial-tank__surface"
          cx="60"
          :cy="liquidY"
          :rx="liquidSurfaceRadius"
          ry="4.2"
          fill="var(--tank-fill-top)"
          :style="{ opacity: displayedFill > .01 ? 1 : 0 }"
        />
        <path d="M38 39 V143" stroke="rgba(255,255,255,.34)" stroke-width="5" stroke-linecap="round" />
        <path d="M80 42 V139" stroke="rgba(4,26,37,.22)" stroke-width="8" stroke-linecap="round" />
      </g>

      <path d="M28 42 Q60 29 92 42" :fill="`url(#${domeId})`" stroke="#d8e8ec" stroke-width="1.5" />
      <path d="M28 42 Q60 52 92 42 V124 L73 155 H47 L28 124 Z" fill="rgba(178,205,213,.12)" stroke="#b9d1d8" stroke-width="1.6" />
      <path d="M29 74 H91 M29 106 H91" stroke="rgba(226,241,244,.44)" stroke-width="1" />
      <path d="M28 123 Q60 130 92 123" fill="none" stroke="rgba(222,239,242,.52)" stroke-width="1.1" />

      <path d="M48 30 V40 M72 30 V40" stroke="#75909c" stroke-width="2" />
      <path d="M47 29 Q60 23 73 29" :fill="`url(#${domeId})`" stroke="#d7e7ea" stroke-width="1.2" />
      <rect x="53" y="19" width="14" height="10" rx="2" :fill="`url(#${metalId})`" stroke="#d7e7ea" stroke-width="1" />
      <ellipse cx="60" cy="18.5" rx="10" ry="3.2" :fill="`url(#${domeId})`" stroke="#ecf7f8" stroke-width="1" />

      <path d="M28 102 L18 164 M92 102 L102 164" stroke="#8fa8b2" stroke-width="4" />
      <path d="M18 164 V177 M102 164 V177" stroke="#c7d8dc" stroke-width="4" />
      <path d="M38 149 L20 174 M82 149 L100 174 M20 145 L45 169 M100 145 L75 169" stroke="#708b97" stroke-width="2" />
      <path d="M17 177 H27 M93 177 H103" stroke="#e0ecee" stroke-width="3" stroke-linecap="round" />
      <path d="M47 155 H73 L68 165 H52 Z" :fill="`url(#${metalId})`" stroke="#c8dce1" stroke-width="1.1" />
      <rect x="56" y="164" width="8" height="10" rx="1" :fill="`url(#${metalId})`" />
    </g>
  </svg>
</template>

<style scoped>
.industrial-tank {
  --tank-fill-top: #42ebe0;
  --tank-fill-mid: #12bfc1;
  --tank-fill-bottom: #087a89;
  display: block;
  width: 100%;
  height: 100%;
  overflow: visible;
  filter: drop-shadow(0 0 5px color-mix(in srgb, var(--tank-fill-mid) 28%, transparent));
}
.industrial-tank.is-powder {
  --tank-fill-top: #f6c762;
  --tank-fill-mid: #d89328;
  --tank-fill-bottom: #8f5d12;
}
.industrial-tank.is-roll { --tank-fill-top: #39e4df; --tank-fill-mid: #12b6c0; --tank-fill-bottom: #087885; }
.industrial-tank.is-liquid { --tank-fill-top: #3ce8cf; --tank-fill-mid: #12b8aa; --tank-fill-bottom: #087a6f; }
.industrial-tank__liquid,
.industrial-tank__surface {
  transition:
    y 2.8s cubic-bezier(.4,0,.2,1),
    height 2.8s cubic-bezier(.4,0,.2,1),
    cy 2.8s cubic-bezier(.4,0,.2,1),
    rx 2.8s cubic-bezier(.4,0,.2,1),
    opacity .24s ease-out;
}
.industrial-tank__surface { filter: drop-shadow(0 2px 5px color-mix(in srgb, var(--tank-fill-top) 62%, transparent)); }

@media (prefers-reduced-motion: reduce) {
  .industrial-tank__liquid,
  .industrial-tank__surface { transition: none; }
}
</style>

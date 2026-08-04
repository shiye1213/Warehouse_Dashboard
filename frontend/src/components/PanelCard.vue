<script setup>
import { ArrowUpRight } from 'lucide-vue-next'

defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
  eyebrow: { type: String, default: '' },
  linkLabel: { type: String, default: '' },
  interactive: { type: Boolean, default: false },
  padded: { type: Boolean, default: true },
})

defineEmits(['open'])
</script>

<template>
  <section class="panel-card" :class="{ interactive, 'no-padding': !padded }" @click="interactive && $emit('open')">
    <header v-if="title" class="panel-heading">
      <div>
        <p v-if="eyebrow">{{ eyebrow }}</p>
        <h2>{{ title }}</h2>
        <span v-if="subtitle">{{ subtitle }}</span>
      </div>
      <button v-if="linkLabel" class="panel-link" type="button" @click.stop="$emit('open')">
        {{ linkLabel }} <ArrowUpRight :size="16" />
      </button>
      <slot name="action" />
    </header>
    <div class="panel-content"><slot /></div>
  </section>
</template>

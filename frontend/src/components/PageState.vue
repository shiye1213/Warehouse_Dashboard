<script setup>
import { AlertCircle, LoaderCircle } from 'lucide-vue-next'

defineProps({ loading: Boolean, error: { type: String, default: '' } })
defineEmits(['retry'])
</script>

<template>
  <div v-if="loading" class="page-state"><LoaderCircle class="spin" :size="28" /><strong>正在汇总运营数据</strong><span>请稍候…</span></div>
  <div v-else-if="error" class="page-state is-error"><AlertCircle :size="28" /><strong>暂时无法连接数据服务</strong><span>{{ error }}</span><button class="secondary-button" @click="$emit('retry')">重新加载</button></div>
  <slot v-else />
</template>

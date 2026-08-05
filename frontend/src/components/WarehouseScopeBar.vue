<script setup>
import { Check, Warehouse } from 'lucide-vue-next'
import { useWarehouseScope } from '../composables/useWarehouseScope'

const { selectedWarehouse, warehouseOptions, selectWarehouse } = useWarehouseScope()
</script>

<template>
  <section class="warehouse-scope" aria-label="仓库分类切换">
    <div class="warehouse-scope-copy">
      <span class="warehouse-scope-icon"><Warehouse :size="17" /></span>
      <div><strong>数据范围</strong><small>当前优先展示 {{ selectedWarehouse }}</small></div>
    </div>
    <div class="warehouse-scope-tabs" role="group" aria-label="选择仓库分类">
      <button
        v-for="item in warehouseOptions"
        :key="item.value"
        type="button"
        :class="{ active: selectedWarehouse === item.value }"
        :aria-pressed="selectedWarehouse === item.value"
        @click="selectWarehouse(item.value)"
      >
        <Check v-if="selectedWarehouse === item.value" :size="13" />
        {{ item.label }}
      </button>
    </div>
  </section>
</template>


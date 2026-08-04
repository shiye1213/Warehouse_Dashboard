<script setup>
import { computed, ref } from 'vue'
import { CheckCircle2, Database, Download, FileDown, FileSpreadsheet, RefreshCw, UploadCloud } from 'lucide-vue-next'
import PageState from '../components/PageState.vue'
import PanelCard from '../components/PanelCard.vue'
import { useDashboard } from '../composables/useDashboard'
import { dashboardApi } from '../services/api'

const { snapshot, loading, error, refresh } = useDashboard()
const dragActive = ref(false)
const importing = ref(false)
const selectedFile = ref(null)
const result = ref(null)
const actionError = ref('')
const exporting = ref('')
const fileInput = ref(null)

const accepted = computed(() => selectedFile.value && /\.(xlsx|csv)$/i.test(selectedFile.value.name))

function chooseFile(event) {
  selectedFile.value = event.target.files?.[0] || null
  result.value = null
  actionError.value = ''
}

function onDrop(event) {
  dragActive.value = false
  const file = event.dataTransfer?.files?.[0]
  if (file) selectedFile.value = file
}

async function importData() {
  if (!accepted.value) { actionError.value = '请选择 .xlsx 或 .csv 文件'; return }
  importing.value = true
  actionError.value = ''
  try {
    result.value = await dashboardApi.importFile(selectedFile.value)
    await refresh()
    selectedFile.value = null
    if (fileInput.value) fileInput.value.value = ''
  } catch (cause) {
    actionError.value = cause.message
  } finally { importing.value = false }
}

async function exportData(format) {
  exporting.value = format
  actionError.value = ''
  try { await dashboardApi.download(format) } catch (cause) { actionError.value = cause.message } finally { exporting.value = '' }
}
</script>

<template>
  <div class="page">
    <PageState :loading="loading && !snapshot" :error="error" @retry="refresh">
      <div class="page-intro"><div><p>DATA EXCHANGE</p><h2>数据导入与导出</h2><span>通过标准模板更新日指标，或导出管理层报告与原始明细。</span></div><div class="snapshot-note"><Database :size="19" /><div><strong>当前数据集</strong><small>{{ snapshot?.meta?.period }} · {{ snapshot?.trend?.length || 0 }} 个业务日</small></div></div></div>
      <section class="data-center-grid">
        <PanelCard title="导入运营数据" subtitle="支持 Excel 工作簿和 UTF-8 CSV" eyebrow="IMPORT DATA">
          <div class="upload-zone" :class="{ active: dragActive, ready: accepted }" @dragenter.prevent="dragActive = true" @dragover.prevent="dragActive = true" @dragleave.prevent="dragActive = false" @drop.prevent="onDrop" @click="fileInput?.click()">
            <input ref="fileInput" type="file" accept=".xlsx,.csv" hidden @change="chooseFile" />
            <div class="upload-icon"><UploadCloud :size="30" /></div>
            <template v-if="selectedFile"><strong>{{ selectedFile.name }}</strong><span>{{ (selectedFile.size / 1024).toFixed(1) }} KB · 点击重新选择</span></template>
            <template v-else><strong>拖放文件到此处，或点击选择</strong><span>单次导入日指标数据；同日期记录将被更新</span></template>
          </div>
          <div class="format-hints"><span><FileSpreadsheet :size="16" /> .xlsx</span><span><FileSpreadsheet :size="16" /> .csv</span><button @click="dashboardApi.downloadTemplate()"><FileDown :size="16" /> 下载标准模板</button></div>
          <div v-if="result" class="result-banner success"><CheckCircle2 :size="19" /><div><strong>导入完成</strong><span>已处理 {{ result.importedRows }} 行，数据范围 {{ result.startDate }} 至 {{ result.endDate }}</span></div></div>
          <div v-if="actionError" class="result-banner error"><span>!</span><div><strong>操作未完成</strong><span>{{ actionError }}</span></div></div>
          <button class="primary-button full" :disabled="!accepted || importing" @click="importData"><RefreshCw v-if="importing" class="spin" :size="17" /><UploadCloud v-else :size="17" />{{ importing ? '正在校验并导入…' : '校验并导入数据' }}</button>
        </PanelCard>

        <PanelCard title="导出与交付" subtitle="按不同使用场景生成文件" eyebrow="EXPORT DATA">
          <div class="export-options">
            <button @click="exportData('xlsx')"><span class="export-icon excel"><FileSpreadsheet :size="24" /></span><div><strong>完整运营工作簿</strong><small>包含日指标、库区、异常、目标和资源 5 个工作表</small></div><Download :size="18" /></button>
            <button @click="exportData('csv')"><span class="export-icon csv"><FileDown :size="24" /></span><div><strong>日指标 CSV</strong><small>适合继续分析、系统交换与轻量归档</small></div><Download :size="18" /></button>
          </div>
          <div v-if="exporting" class="export-progress"><RefreshCw class="spin" :size="17" /> 正在生成 {{ exporting.toUpperCase() }} 文件…</div>
          <div class="data-governance"><h3>数据口径保障</h3><div><span>01</span><p><strong>日期主键合并</strong>相同业务日期更新，新增日期追加。</p></div><div><span>02</span><p><strong>字段级校验</strong>日期与数值格式不合法时终止导入。</p></div><div><span>03</span><p><strong>统一导出口径</strong>导出始终使用当前后端有效数据。</p></div></div>
        </PanelCard>
      </section>
    </PageState>
  </div>
</template>

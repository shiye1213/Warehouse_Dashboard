import { readFile } from 'node:fs/promises'

const dashboardUrl = new URL('../src/data/raw-material-dashboard.json', import.meta.url)
const dashboard = JSON.parse(await readFile(dashboardUrl, 'utf8'))
const summaryCount = Number(dashboard.summary?.openExceptions || 0)
const detailRows = Array.isArray(dashboard.openExceptions) ? dashboard.openExceptions : []
const detailCount = detailRows.length
const uniqueCount = new Set(detailRows.map((item) => item.id)).size

if (summaryCount !== detailCount) {
  throw new Error(`未关闭异常对账失败：汇总 ${summaryCount} 项，明细 ${detailCount} 条`)
}

if (uniqueCount !== detailCount) {
  throw new Error(`异常编号不唯一：${detailCount} 条明细中只有 ${uniqueCount} 个唯一编号`)
}

if (detailRows.some((item) => item.closedAt)) {
  throw new Error('未关闭异常明细中混入了已关闭记录')
}

console.log(`未关闭异常对账通过：${summaryCount} 项汇总 = ${detailCount} 条明细`)

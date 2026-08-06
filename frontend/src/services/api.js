const API_BASE = import.meta.env.VITE_API_BASE || '/api'

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, options)
  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new Error(body?.message || `请求失败（${response.status}）`)
  }
  const contentType = response.headers.get('content-type') || ''
  return contentType.includes('application/json') ? response.json() : response
}

export const dashboardApi = {
  getSnapshot: (range = 31) => request(`/dashboard/overview?range=${range}`),
  getWarehouseSnapshot: (warehouseId, range = 31) => request(`/dashboard/warehouses/${encodeURIComponent(warehouseId)}?range=${range}`),
  getWarehouses: () => request('/warehouses'),
  getDataStatus: () => request('/data/status'),
  getZone: (code) => request(`/zones/${encodeURIComponent(code)}`),
  getHealth: () => request('/health'),
  importFile: (file) => {
    const body = new FormData()
    body.append('file', file)
    return request('/data/import', { method: 'POST', body })
  },
  download: async (format = 'xlsx') => {
    const response = await request(`/data/export?format=${format}`)
    const blob = await response.blob()
    const disposition = response.headers.get('content-disposition') || ''
    const match = disposition.match(/filename\*=UTF-8''([^;]+)/i)
    const filename = match ? decodeURIComponent(match[1]) : `warehouse-data.${format}`
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = filename
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    URL.revokeObjectURL(url)
  },
  downloadTemplate: async () => {
    const response = await request('/data/template')
    const blob = await response.blob()
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = '仓库日指标导入模板.xlsx'
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    URL.revokeObjectURL(url)
  },
}

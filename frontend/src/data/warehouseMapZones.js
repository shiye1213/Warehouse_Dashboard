const zoneCatalog = [
  { warehouse: '成品库', code: 'FG-C01', name: '成品存储区', occupancy: 0.814, materialTypes: 41, capacity: 940, frozen: 3, manager: '赵工' },
  { warehouse: '成品库', code: 'FG-C02', name: '发运暂存区', occupancy: 0.91, materialTypes: 31, capacity: 480, frozen: 8, manager: '赵工' },
  { warehouse: '箱盒库', code: 'PK-P01', name: '箱盒主库', occupancy: 0.88, materialTypes: 36, capacity: 640, frozen: 4, manager: '孙工' },
  { warehouse: '箱盒库', code: 'PK-P02', name: '箱盒待检区', occupancy: 0.879, materialTypes: 22, capacity: 520, frozen: 6, manager: '孙工' },
  { warehouse: '原料库', code: 'RM-A01', name: '原材料库A区', occupancy: 0.857, materialTypes: 41, capacity: 720, frozen: 7, manager: '张工' },
  { warehouse: '原料库', code: 'RM-A02', name: '原材料库B区', occupancy: 0.86, materialTypes: 38, capacity: 760, frozen: 5, manager: '张工' },
  { warehouse: '原料库', code: 'RM-A03', name: '原材料库C区', occupancy: 0.854, materialTypes: 34, capacity: 680, frozen: 4, manager: '张工' },
  { warehouse: '原料库', code: 'RM-A04', name: '原材料库D区', occupancy: 0.86, materialTypes: 29, capacity: 660, frozen: 6, manager: '李工' },
  { warehouse: '原料库', code: 'RM-C01', name: '成品暂存区A区', occupancy: 0.86, materialTypes: 32, capacity: 560, frozen: 5, manager: '李工' },
  { warehouse: '原料库', code: 'RM-C02', name: '成品暂存区B区', occupancy: 0.86, materialTypes: 27, capacity: 540, frozen: 8, manager: '李工' },
  { warehouse: '原料库', code: 'RM-P01', name: '包材暂存区A区', occupancy: 0.86, materialTypes: 24, capacity: 500, frozen: 3, manager: '王工' },
  { warehouse: '原料库', code: 'RM-P02', name: '包材暂存区B区', occupancy: 0.837, materialTypes: 21, capacity: 480, frozen: 4, manager: '王工' },
]

const overviewCodes = ['FG-C02', 'PK-P01', 'PK-P02', 'RM-A02', 'RM-C01', 'RM-P01', 'RM-A04', 'RM-C02', 'RM-A01', 'RM-A03']
const warehouseCodes = {
  成品库: ['FG-C02', 'FG-C01'],
  箱盒库: ['PK-P01', 'PK-P02'],
  原料库: ['RM-A02', 'RM-C01', 'RM-P01', 'RM-A04', 'RM-C02', 'RM-A01', 'RM-A03', 'RM-P02'],
}

function normalize(zone) {
  const capacity = Number(zone.capacity || 0)
  const occupancy = Number(zone.occupancy || 0)
  const occupied = Number.isFinite(Number(zone.occupied)) ? Number(zone.occupied) : Math.round(capacity * occupancy)
  const available = Number.isFinite(Number(zone.available)) ? Number(zone.available) : Math.max(0, capacity - occupied)
  return {
    snapshotDate: '2026-07-01',
    status: occupancy >= 0.85 ? '高负荷' : occupancy >= 0.75 ? '偏高' : '正常',
    abnormal: 0,
    ...zone,
    capacity,
    occupancy,
    occupied,
    available,
  }
}

export function mergeWarehouseMapZones(sourceZones = [], warehouse = '全部仓库') {
  const sourceByCode = new Map(sourceZones.map((zone) => [zone.code, zone]))
  const merged = zoneCatalog.map((zone) => normalize({ ...zone, ...sourceByCode.get(zone.code) }))
  if (warehouse === '全部仓库') return overviewCodes.map((code) => merged.find((zone) => zone.code === code)).filter(Boolean)
  return (warehouseCodes[warehouse] || []).map((code) => merged.find((zone) => zone.code === code)).filter(Boolean)
}

export function findWarehouseMapZone(code, sourceZones = []) {
  const sourceByCode = new Map(sourceZones.map((zone) => [zone.code, zone]))
  const zone = zoneCatalog.find((item) => item.code === code)
  return zone ? normalize({ ...zone, ...sourceByCode.get(code) }) : sourceByCode.has(code) ? normalize(sourceByCode.get(code)) : undefined
}


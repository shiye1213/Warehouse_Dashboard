import assert from 'node:assert/strict'
import { calculateAvailableRate } from '../src/utils/rawMaterialMetrics.js'

assert.ok(Math.abs(calculateAvailableRate(81.582, 79.379) - 0.972996) < 0.000001)
assert.equal(calculateAvailableRate(0, 0), 0)
assert.equal(calculateAvailableRate(10, -1), 0)
assert.equal(calculateAvailableRate(10, 12), 1)

console.log('Raw material metric checks passed.')

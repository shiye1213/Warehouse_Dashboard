import { spawn } from 'node:child_process'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const chromePath = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const profilePath = await mkdtemp(join(tmpdir(), 'raw-board-interactions-'))
const chrome = spawn(chromePath, [
  '--headless=new',
  '--disable-gpu',
  '--no-sandbox',
  '--remote-debugging-port=0',
  `--user-data-dir=${profilePath}`,
  'about:blank',
], { windowsHide: true, stdio: ['ignore', 'ignore', 'pipe'] })

function wait(duration) {
  return new Promise((resolve) => setTimeout(resolve, duration))
}

function waitForDebuggerUrl() {
  return new Promise((resolve, reject) => {
    let output = ''
    const timeout = setTimeout(() => reject(new Error('Chrome debugging endpoint timed out')), 8000)
    chrome.stderr.on('data', (chunk) => {
      output += chunk.toString()
      const match = output.match(/DevTools listening on (ws:\/\/[^\s]+)/)
      if (!match) return
      clearTimeout(timeout)
      resolve(match[1])
    })
    chrome.once('exit', (code) => reject(new Error(`Chrome exited before setup (${code})`)))
  })
}

function createCdpClient(url) {
  const socket = new WebSocket(url)
  const pending = new Map()
  let sequence = 0

  socket.addEventListener('message', (event) => {
    const message = JSON.parse(event.data)
    if (!message.id || !pending.has(message.id)) return
    const { resolve, reject } = pending.get(message.id)
    pending.delete(message.id)
    if (message.error) reject(new Error(message.error.message))
    else resolve(message.result)
  })

  const ready = new Promise((resolve, reject) => {
    socket.addEventListener('open', resolve, { once: true })
    socket.addEventListener('error', reject, { once: true })
  })

  return {
    ready,
    send(method, params = {}, sessionId) {
      const id = ++sequence
      socket.send(JSON.stringify({ id, method, params, ...(sessionId ? { sessionId } : {}) }))
      return new Promise((resolve, reject) => pending.set(id, { resolve, reject }))
    },
    close: () => socket.close(),
  }
}

function initialProbe() {
  const forbiddenLabels = ['查看详情', '查看全部', '查看罐区详情', '库位明细']
  const buttons = [...document.querySelectorAll('.raw-board button')]
  const blockLinks = [...document.querySelectorAll('.raw-kpi-card[role="link"], .raw-panel[role="link"]')]
  const violations = []

  const forbiddenButtons = buttons.filter((button) => forbiddenLabels.some((label) => button.textContent?.includes(label)))
  if (forbiddenButtons.length) violations.push(`navigation-buttons-remain:${forbiddenButtons.length}`)
  if (blockLinks.length !== 10) violations.push(`block-link-count:${blockLinks.length}`)
  if (!blockLinks.every((block) => block.tabIndex === 0 && block.getAttribute('aria-label'))) {
    violations.push('block-link-accessibility')
  }

  const chart = document.querySelector('.raw-trend-chart')
  const chartHeight = chart?.getBoundingClientRect().height || 0
  const yAxisMax = Number(chart?.dataset.yAxisMax)
  const yAxisInterval = Number(chart?.dataset.yAxisInterval)
  if (chartHeight < 150) violations.push(`trend-chart-height:${chartHeight}`)
  if (yAxisMax < 1.2 || Math.abs(yAxisInterval - 0.2) > 0.001) {
    violations.push(`trend-chart-y-axis:${yAxisMax}/${yAxisInterval}`)
  }

  const zoneOverviewNumber = document.querySelector('.raw-zone-overview .animated-number')
  if (zoneOverviewNumber && getComputedStyle(zoneOverviewNumber).display !== 'inline') {
    violations.push(`zone-overview-number-display:${getComputedStyle(zoneOverviewNumber).display}`)
  }

  const refreshButton = buttons.find((button) => button.getAttribute('aria-label') === '刷新全部数据')
  if (!refreshButton) violations.push('refresh-button-missing')
  refreshButton?.click()

  const panelRect = document.querySelector('.raw-flow-panel')?.getBoundingClientRect()
  return {
    violations,
    chartHeight,
    yAxisMax,
    yAxisInterval,
    panelPoint: panelRect ? { x: panelRect.left + panelRect.width / 2, y: panelRect.top + panelRect.height / 2 } : null,
  }
}

function numericValue(selector) {
  const text = document.querySelector(selector)?.textContent || ''
  return Number(text.replace(/[^\d+.-]/g, ''))
}

let client
try {
  client = createCdpClient(await waitForDebuggerUrl())
  await client.ready
  const { targetId } = await client.send('Target.createTarget', { url: 'about:blank' })
  const { sessionId } = await client.send('Target.attachToTarget', { targetId, flatten: true })
  await client.send('Emulation.setDeviceMetricsOverride', { width: 1366, height: 768, deviceScaleFactor: 1, mobile: false }, sessionId)
  await client.send('Page.enable', {}, sessionId)
  await client.send('Page.navigate', { url: 'http://127.0.0.1:5173/raw-material' }, sessionId)
  await wait(2200)

  const initialResponse = await client.send('Runtime.evaluate', { expression: `(${initialProbe.toString()})()`, returnByValue: true }, sessionId)
  const result = initialResponse.result.value
  await wait(120)
  const earlyResponse = await client.send('Runtime.evaluate', { expression: `(${numericValue.toString()})('.raw-kpi-card .animated-number')`, returnByValue: true }, sessionId)
  await wait(2050)
  const finalResponse = await client.send('Runtime.evaluate', { expression: `(${numericValue.toString()})('.raw-kpi-card .animated-number')`, returnByValue: true }, sessionId)
  result.numberAnimation = { early: earlyResponse.result.value, final: finalResponse.result.value }
  if (!(Math.abs(result.numberAnimation.early) < Math.abs(result.numberAnimation.final))) {
    result.violations.push(`number-animation:${result.numberAnimation.early}/${result.numberAnimation.final}`)
  }

  if (!result.panelPoint) result.violations.push('flow-panel-missing')
  else {
    await client.send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: result.panelPoint.x, y: result.panelPoint.y }, sessionId)
    await wait(250)
    const hoverResponse = await client.send('Runtime.evaluate', {
      expression: `(() => { const transform = getComputedStyle(document.querySelector('.raw-flow-panel')).transform; return transform === 'none' ? 1 : new DOMMatrix(transform).a })()`,
      returnByValue: true,
    }, sessionId)
    result.hoverScale = hoverResponse.result.value
    if (result.hoverScale <= 1) result.violations.push(`hover-scale:${result.hoverScale}`)
  }

  await client.send('Runtime.evaluate', { expression: `document.querySelector('.raw-flow-panel').click()` }, sessionId)
  await wait(250)
  const routeResponse = await client.send('Runtime.evaluate', { expression: 'location.pathname', returnByValue: true }, sessionId)
  result.clickedRoute = routeResponse.result.value
  if (result.clickedRoute !== '/operations') result.violations.push(`panel-route:${result.clickedRoute}`)

  await client.send('Emulation.setEmulatedMedia', {
    features: [{ name: 'prefers-reduced-motion', value: 'reduce' }],
  }, sessionId)
  await client.send('Page.navigate', { url: 'http://127.0.0.1:5173/raw-material' }, sessionId)
  await wait(2200)
  await client.send('Runtime.evaluate', {
    expression: `(() => { [...document.querySelectorAll('.raw-board button')].find((button) => button.getAttribute('aria-label') === '刷新全部数据')?.click() })()`,
  }, sessionId)
  await wait(80)
  const reducedValueResponse = await client.send('Runtime.evaluate', { expression: `(${numericValue.toString()})('.raw-kpi-card .animated-number')`, returnByValue: true }, sessionId)
  result.reducedMotionValue = reducedValueResponse.result.value
  if (Math.abs(result.reducedMotionValue - result.numberAnimation.final) > 0.001) {
    result.violations.push(`reduced-motion-number:${result.reducedMotionValue}/${result.numberAnimation.final}`)
  }

  const reducedPointResponse = await client.send('Runtime.evaluate', {
    expression: `(() => { const rect = document.querySelector('.raw-flow-panel').getBoundingClientRect(); return { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 } })()`,
    returnByValue: true,
  }, sessionId)
  await client.send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: reducedPointResponse.result.value.x, y: reducedPointResponse.result.value.y }, sessionId)
  await wait(80)
  const reducedScaleResponse = await client.send('Runtime.evaluate', {
    expression: `(() => { const transform = getComputedStyle(document.querySelector('.raw-flow-panel')).transform; return transform === 'none' ? 1 : new DOMMatrix(transform).a })()`,
    returnByValue: true,
  }, sessionId)
  result.reducedMotionScale = reducedScaleResponse.result.value
  if (result.reducedMotionScale !== 1) result.violations.push(`reduced-motion-scale:${result.reducedMotionScale}`)

  result.verdict = result.violations.length ? 'FAIL' : 'PASS'
  delete result.panelPoint
  console.log(JSON.stringify(result, null, 2))
  if (result.verdict !== 'PASS') process.exitCode = 1
  await client.send('Browser.close').catch(() => {})
} finally {
  client?.close()
  chrome.kill()
  await rm(profilePath, { recursive: true, force: true }).catch(() => {})
}

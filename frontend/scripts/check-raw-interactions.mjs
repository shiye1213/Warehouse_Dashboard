import { spawn } from 'node:child_process'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const chromePath = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const dashboardUrl = process.env.RAW_DASHBOARD_URL || 'http://127.0.0.1:5173/raw-material'
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

  const title = document.querySelector('.raw-title h1')
  const titleStyle = title ? getComputedStyle(title) : null
  const titleFrame = document.querySelector('.raw-title-frame')
  const titleFrameRect = titleFrame?.getBoundingClientRect()
  const titleRect = title?.getBoundingClientRect()
  const titleFramePaths = titleFrame?.querySelectorAll('path, line, circle') || []
  if (!titleStyle || !titleFrameRect || titleFramePaths.length < 5 || !titleRect || titleFrameRect.left > titleRect.left || titleFrameRect.right < titleRect.right || titleFrameRect.bottom < titleRect.bottom) {
    violations.push(`dashboard-title-reference-frame:${titleFramePaths.length}`)
  }

  const techFrame = document.querySelector('.raw-board-tech-frame')
  const frameCorners = techFrame?.querySelectorAll('.raw-board-frame-corner') || []
  const frameRails = techFrame?.querySelectorAll('.raw-board-frame-rail') || []
  if (!techFrame || frameCorners.length !== 4 || frameRails.length !== 4 || getComputedStyle(techFrame).pointerEvents !== 'none') {
    violations.push(`dashboard-tech-frame:${frameCorners.length}/${frameRails.length}`)
  }

  const alertProgress = document.querySelector('.raw-alert-progress')
  const alertViewport = document.querySelector('.raw-alert-viewport')
  const alertScrubber = alertProgress?.querySelector('input[type="range"]')
  const alertProgressRect = alertProgress?.getBoundingClientRect()
  const alertViewportRect = alertViewport?.getBoundingClientRect()
  if (!alertProgressRect || alertProgressRect.width < 12 || alertProgressRect.height < 60 || !alertViewportRect || alertProgressRect.left < alertViewportRect.right - 1) {
    violations.push(`alert-progress-size:${alertProgressRect?.width || 0}x${alertProgressRect?.height || 0}`)
  }
  if (!alertScrubber || alertScrubber.getAttribute('aria-orientation') !== 'vertical') violations.push('alert-progress-not-draggable')

  const tankCards = [...document.querySelectorAll('.raw-silo-card')]
  const thresholdMarkers = document.querySelectorAll('.raw-silo-low-threshold')
  const materialTextures = document.querySelectorAll('.industrial-tank__texture')
  const materialForms = new Set(tankCards.flatMap((card) => [...card.classList].filter((name) => name.startsWith('is-') && !['is-high', 'is-low'].includes(name))))
  const lowWarningMismatches = tankCards.filter((card) => {
    const fillPercent = Number.parseFloat(card.querySelector('.raw-silo-rate')?.textContent || '')
    return Number.isFinite(fillPercent) && fillPercent <= 25 && !card.classList.contains('is-low')
  })
  if (!tankCards.length || thresholdMarkers.length !== tankCards.length) {
    violations.push(`tank-low-thresholds:${thresholdMarkers.length}/${tankCards.length}`)
  }
  if (materialTextures.length !== tankCards.length || materialForms.size < 4) {
    violations.push(`tank-material-effects:${materialTextures.length}/${materialForms.size}`)
  }
  if (lowWarningMismatches.length) violations.push(`tank-low-warning-state:${lowWarningMismatches.length}`)

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
    titleFrame: { elements: titleFramePaths.length, width: titleFrameRect?.width || 0, height: titleFrameRect?.height || 0 },
    techFrame: { corners: frameCorners.length, rails: frameRails.length },
    alertProgressSize: alertProgressRect ? [alertProgressRect.width, alertProgressRect.height] : [0, 0],
    alertProgressAtSide: Boolean(alertProgressRect && alertViewportRect && alertProgressRect.left >= alertViewportRect.right - 1),
    tankEffects: { cards: tankCards.length, thresholds: thresholdMarkers.length, textures: materialTextures.length, forms: materialForms.size, lowWarningMismatches: lowWarningMismatches.length },
    panelPoint: panelRect ? { x: panelRect.left + panelRect.width / 2, y: panelRect.top + panelRect.height / 2 } : null,
  }
}

function numericValue(selector) {
  const text = document.querySelector(selector)?.textContent || ''
  return Number(text.replace(/[^\d+.-]/g, ''))
}

function alertScrollProbe() {
  const viewport = document.querySelector('.raw-alert-viewport')
  if (!viewport) return null
  const track = viewport.querySelector('.raw-alert-track')
  const progress = document.querySelector('.raw-alert-progress input[type="range"]')
  const rect = viewport.getBoundingClientRect()
  const progressRect = progress?.getBoundingClientRect()
  const transform = getComputedStyle(track).transform
  return {
    point: { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 },
    progressRect: progressRect ? { left: progressRect.left, top: progressRect.top, width: progressRect.width, height: progressRect.height } : null,
    transformY: transform === 'none' ? 0 : new DOMMatrix(transform).m42,
    listCopies: viewport.querySelectorAll('.raw-alert-list').length,
    animationPlayState: getComputedStyle(track).animationPlayState,
    progressScale: Number(progress?.value || 0),
    positionText: document.querySelector('.raw-alert-position')?.textContent?.trim() || '',
    alertCount: viewport.querySelector('.raw-alert-list')?.children.length || 0,
  }
}

let client
try {
  client = createCdpClient(await waitForDebuggerUrl())
  await client.ready
  const { targetId } = await client.send('Target.createTarget', { url: 'about:blank' })
  const { sessionId } = await client.send('Target.attachToTarget', { targetId, flatten: true })
  await client.send('Emulation.setDeviceMetricsOverride', { width: 1366, height: 768, deviceScaleFactor: 1, mobile: false }, sessionId)
  await client.send('Page.enable', {}, sessionId)
  await client.send('Page.addScriptToEvaluateOnNewDocument', {
    source: `(() => { const nativeSetInterval = window.setInterval.bind(window); window.setInterval = (handler, delay, ...args) => nativeSetInterval(handler, delay === 30000 ? 5000 : delay, ...args) })()`,
  }, sessionId)
  await client.send('Page.navigate', { url: dashboardUrl }, sessionId)
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

  const refreshVersionBeforeResponse = await client.send('Runtime.evaluate', { expression: `Number(document.querySelector('.raw-material-page')?.dataset.refreshVersion || 0)`, returnByValue: true }, sessionId)
  await wait(1000)
  const refreshVersionAfterResponse = await client.send('Runtime.evaluate', { expression: `Number(document.querySelector('.raw-material-page')?.dataset.refreshVersion || 0)`, returnByValue: true }, sessionId)
  result.autoRefresh = { before: refreshVersionBeforeResponse.result.value, after: refreshVersionAfterResponse.result.value }
  if (result.autoRefresh.after <= result.autoRefresh.before) result.violations.push(`auto-refresh:${result.autoRefresh.before}/${result.autoRefresh.after}`)

  const alertProbeResponse = await client.send('Runtime.evaluate', { expression: `(${alertScrollProbe.toString()})()`, returnByValue: true }, sessionId)
  const alertProbe = alertProbeResponse.result.value
  if (!alertProbe || alertProbe.listCopies !== 2) result.violations.push('alert-scroll-viewport-missing')
  else {
    await client.send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: alertProbe.point.x, y: alertProbe.point.y }, sessionId)
    await wait(300)
    const middleResponse = await client.send('Runtime.evaluate', { expression: `(${alertScrollProbe.toString()})()`, returnByValue: true }, sessionId)
    const middle = middleResponse.result.value
    await wait(300)
    const continuedResponse = await client.send('Runtime.evaluate', { expression: `(${alertScrollProbe.toString()})()`, returnByValue: true }, sessionId)
    const continued = continuedResponse.result.value
    result.alertLoop = { startAt: alertProbe.transformY, middleAt: middle.transformY, continuedAt: continued.transformY, playState: continued.animationPlayState, progress: [alertProbe.progressScale, middle.progressScale, continued.progressScale], position: continued.positionText }
    if (middle.transformY >= alertProbe.transformY - 1 || continued.transformY >= middle.transformY - 1) {
      result.violations.push(`alert-loop-not-continuous:${alertProbe.transformY}/${middle.transformY}/${continued.transformY}`)
    }
    const [position, total] = continued.positionText.split('/').map(Number)
    if (middle.progressScale <= alertProbe.progressScale || continued.progressScale <= middle.progressScale || !/^\d+\/\d+$/.test(continued.positionText) || position < 1 || position > total || total !== continued.alertCount || Math.abs(continued.progressScale - position / total) > .03) {
      result.violations.push(`alert-progress:${alertProbe.progressScale}/${middle.progressScale}/${continued.progressScale}/${continued.positionText}`)
    }

    await client.send('Runtime.evaluate', {
      expression: `(() => { const input = document.querySelector('.raw-alert-progress input[type="range"]'); if (!input) return false; input.value = '0.76'; input.dispatchEvent(new Event('input', { bubbles: true })); return true })()`,
      returnByValue: true,
    }, sessionId)
    await wait(120)
    const scrubbedResponse = await client.send('Runtime.evaluate', { expression: `(${alertScrollProbe.toString()})()`, returnByValue: true }, sessionId)
    const scrubbed = scrubbedResponse.result.value
    const scrubbedPosition = Number(scrubbed.positionText.split('/')[0])
    result.alertScrub = { progress: scrubbed.progressScale, position: scrubbed.positionText, playState: scrubbed.animationPlayState }
    if (Math.abs(scrubbed.progressScale - 0.76) > 0.02 || scrubbedPosition < 37 || scrubbedPosition > 39) {
      result.violations.push(`alert-scrub:${scrubbed.progressScale}/${scrubbed.positionText}`)
    }
    await wait(500)
    const resumedResponse = await client.send('Runtime.evaluate', { expression: `(${alertScrollProbe.toString()})()`, returnByValue: true }, sessionId)
    const resumed = resumedResponse.result.value
    if (resumed.animationPlayState !== 'running' || resumed.progressScale <= scrubbed.progressScale) {
      result.violations.push(`alert-scrub-resume:${scrubbed.progressScale}/${resumed.progressScale}/${resumed.animationPlayState}`)
    }

    if (resumed.progressRect) {
      const scrubX = resumed.progressRect.left + resumed.progressRect.width / 2
      const scrubStartY = resumed.progressRect.top + 7
      const scrubEndY = resumed.progressRect.top + resumed.progressRect.height - 7
      await client.send('Input.dispatchMouseEvent', { type: 'mousePressed', x: scrubX, y: scrubStartY, button: 'left', buttons: 1, clickCount: 1 }, sessionId)
      await client.send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: scrubX, y: scrubEndY, button: 'left', buttons: 1 }, sessionId)
      await client.send('Input.dispatchMouseEvent', { type: 'mouseReleased', x: scrubX, y: scrubEndY, button: 'left', buttons: 0, clickCount: 1 }, sessionId)
      await wait(120)
      const pointerScrubbedResponse = await client.send('Runtime.evaluate', { expression: `(${alertScrollProbe.toString()})()`, returnByValue: true }, sessionId)
      const pointerScrubbed = pointerScrubbedResponse.result.value
      const routeAfterScrubResponse = await client.send('Runtime.evaluate', { expression: 'location.pathname', returnByValue: true }, sessionId)
      result.alertPointerScrub = { progress: pointerScrubbed.progressScale, position: pointerScrubbed.positionText, route: routeAfterScrubResponse.result.value }
      if (pointerScrubbed.progressScale < 0.85 || routeAfterScrubResponse.result.value !== '/raw-material') {
        result.violations.push(`alert-pointer-scrub:${pointerScrubbed.progressScale}/${pointerScrubbed.positionText}/${routeAfterScrubResponse.result.value}`)
      }
    }
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
  await client.send('Page.navigate', { url: dashboardUrl }, sessionId)
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

  const reducedAlertStartResponse = await client.send('Runtime.evaluate', { expression: `(${alertScrollProbe.toString()})()?.transformY || 0`, returnByValue: true }, sessionId)
  await wait(600)
  const reducedAlertEndResponse = await client.send('Runtime.evaluate', { expression: `(${alertScrollProbe.toString()})()?.transformY || 0`, returnByValue: true }, sessionId)
  result.reducedMotionAlertScroll = { start: reducedAlertStartResponse.result.value, end: reducedAlertEndResponse.result.value }
  if (Math.abs(result.reducedMotionAlertScroll.end - result.reducedMotionAlertScroll.start) > 1) {
    result.violations.push(`reduced-motion-alert-scroll:${result.reducedMotionAlertScroll.start}/${result.reducedMotionAlertScroll.end}`)
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

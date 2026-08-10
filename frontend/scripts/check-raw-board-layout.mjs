import { spawn } from 'node:child_process'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const [width = 1366, height = 768] = process.argv.slice(2).map(Number)
const dashboardUrl = process.env.RAW_DASHBOARD_URL || 'http://127.0.0.1:5173/raw-material'
const chromePath = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const profilePath = await mkdtemp(join(tmpdir(), 'raw-board-layout-'))
const chrome = spawn(chromePath, [
  '--headless=new',
  '--disable-gpu',
  '--no-sandbox',
  '--remote-debugging-port=0',
  `--user-data-dir=${profilePath}`,
  'about:blank',
], { windowsHide: true, stdio: ['ignore', 'ignore', 'pipe'] })

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

function probeLayout() {
  const board = document.querySelector('.raw-board')
  const panels = [...document.querySelectorAll('.raw-panel')]
  const boardSections = [...document.querySelectorAll('.raw-masthead, .raw-kpi-strip, .raw-board-grid')]
  const violations = []
  const geometry = []

  if (!board || panels.length !== 5) return { verdict: 'FAIL', violations: ['raw-board-not-ready'], geometry }

  const boardRect = board.getBoundingClientRect()
  if (Math.abs(boardRect.width / boardRect.height - 16 / 9) > 0.01) {
    violations.push(`board-ratio:${(boardRect.width / boardRect.height).toFixed(4)}`)
  }
  if (board.scrollHeight > board.clientHeight + 1 || board.scrollWidth > board.clientWidth + 1) {
    violations.push(`board-overflow:${board.scrollWidth}x${board.scrollHeight}/${board.clientWidth}x${board.clientHeight}`)
  }

  boardSections.forEach((section) => {
    if (section.scrollHeight > section.clientHeight + 1 || section.scrollWidth > section.clientWidth + 1) {
      violations.push(`${section.className}-overflow:${section.scrollWidth}x${section.scrollHeight}/${section.clientWidth}x${section.clientHeight}`)
    }
  })
  for (let index = 0; index < boardSections.length - 1; index += 1) {
    const current = boardSections[index].getBoundingClientRect()
    const next = boardSections[index + 1].getBoundingClientRect()
    if (current.bottom > next.top + 1) violations.push(`board-section-overlap:${boardSections[index].className}-${boardSections[index + 1].className}`)
  }

  panels.forEach((panel, index) => {
    const rect = panel.getBoundingClientRect()
    const name = panel.className.split(' ').find((item) => item.endsWith('-panel') && item !== 'raw-panel') || `panel-${index}`
    geometry.push({ name, width: +rect.width.toFixed(1), height: +rect.height.toFixed(1), scrollHeight: panel.scrollHeight, clientHeight: panel.clientHeight })
    if (panel.scrollHeight > panel.clientHeight + 1 || panel.scrollWidth > panel.clientWidth + 1) {
      violations.push(`${name}-overflow:${panel.scrollWidth}x${panel.scrollHeight}/${panel.clientWidth}x${panel.clientHeight}`)
    }
    ;[...panel.children].forEach((child) => {
      const style = getComputedStyle(child)
      if (style.display === 'none' || style.position === 'absolute') return
      const childRect = child.getBoundingClientRect()
      if (childRect.bottom > rect.bottom + 1 || childRect.right > rect.right + 1 || childRect.left < rect.left - 1) {
        violations.push(`${name}-clips-${child.className || child.tagName}`)
      }
    })
  })

  const zoneCards = [...document.querySelectorAll('.raw-zone-card')]
  zoneCards.forEach((card, index) => {
    if (card.scrollHeight > card.clientHeight + 1 || card.scrollWidth > card.clientWidth + 1) {
      violations.push(`zone-card-${index}-overflow:${card.scrollWidth}x${card.scrollHeight}/${card.clientWidth}x${card.clientHeight}`)
    }
  })
  for (let first = 0; first < zoneCards.length; first += 1) {
    const a = zoneCards[first].getBoundingClientRect()
    for (let second = first + 1; second < zoneCards.length; second += 1) {
      const b = zoneCards[second].getBoundingClientRect()
      const overlapX = Math.min(a.right, b.right) - Math.max(a.left, b.left)
      const overlapY = Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top)
      if (overlapX > 1 && overlapY > 1) violations.push(`zone-card-overlap:${first}-${second}:${overlapX.toFixed(1)}x${overlapY.toFixed(1)}`)
    }
  }

  for (let first = 0; first < panels.length; first += 1) {
    const a = panels[first].getBoundingClientRect()
    for (let second = first + 1; second < panels.length; second += 1) {
      const b = panels[second].getBoundingClientRect()
      const overlapX = Math.min(a.right, b.right) - Math.max(a.left, b.left)
      const overlapY = Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top)
      if (overlapX > 1 && overlapY > 1) violations.push(`panel-overlap:${first}-${second}:${overlapX.toFixed(1)}x${overlapY.toFixed(1)}`)
    }
  }

  const flowPanel = document.querySelector('.raw-flow-panel')
  const flowFooter = flowPanel?.querySelector('.raw-flow-foot')
  const flowFooterTextBottom = flowFooter
    ? Math.max(...[...flowFooter.children].map((element) => element.getBoundingClientRect().bottom))
    : 0
  const flowFooterClearance = flowPanel && flowFooter
    ? flowPanel.getBoundingClientRect().bottom - flowFooterTextBottom
    : 0
  if (!flowPanel || !flowFooter || flowFooterClearance < 12) {
    violations.push(`flow-footer-clearance:${flowFooterClearance.toFixed(1)}`)
  }

  const monthPanel = document.querySelector('.raw-month-panel')
  const monthSections = monthPanel
    ? [...monthPanel.children].filter((element) => {
        const style = getComputedStyle(element)
        return style.display !== 'none' && style.position !== 'absolute'
      })
    : []
  monthSections.forEach((section) => {
    if (section.scrollHeight > section.clientHeight + 1 || section.scrollWidth > section.clientWidth + 1) {
      violations.push(`month-section-overflow:${section.className || section.tagName}:${section.scrollWidth}x${section.scrollHeight}/${section.clientWidth}x${section.clientHeight}`)
    }
  })
  for (let first = 0; first < monthSections.length; first += 1) {
    const a = monthSections[first].getBoundingClientRect()
    for (let second = first + 1; second < monthSections.length; second += 1) {
      const b = monthSections[second].getBoundingClientRect()
      const overlapX = Math.min(a.right, b.right) - Math.max(a.left, b.left)
      const overlapY = Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top)
      if (overlapX > 1 && overlapY > 1) {
        violations.push(`month-section-overlap:${monthSections[first].className}-${monthSections[second].className}:${overlapX.toFixed(1)}x${overlapY.toFixed(1)}`)
      }
    }
  }

  return {
    verdict: violations.length ? 'FAIL' : 'PASS',
    viewport: [window.innerWidth, window.innerHeight],
    board: { width: +boardRect.width.toFixed(1), height: +boardRect.height.toFixed(1) },
    violations,
    geometry,
    flowFooterClearance: +flowFooterClearance.toFixed(1),
    monthSections: monthSections.map((section) => {
      const rect = section.getBoundingClientRect()
      return { name: section.className || section.tagName, top: +rect.top.toFixed(1), bottom: +rect.bottom.toFixed(1), height: +rect.height.toFixed(1) }
    }),
  }
}

function probeTrendTooltip() {
  const tooltip = [...document.querySelectorAll('div')].find((element) => {
    const style = getComputedStyle(element)
    return style.position === 'absolute'
      && style.display !== 'none'
      && /\d{2}\/\d{2}/.test(element.textContent || '')
      && element.textContent?.includes('原料入库')
      && element.textContent?.includes('生产领用')
  })
  if (!tooltip) return { verdict: 'FAIL', violations: ['trend-tooltip-not-rendered'] }

  const rect = tooltip.getBoundingClientRect()
  const clippingAncestors = []
  let ancestor = tooltip.parentElement
  while (ancestor && ancestor !== document.body) {
    const style = getComputedStyle(ancestor)
    const ancestorRect = ancestor.getBoundingClientRect()
    const clipsX = ['hidden', 'clip', 'auto', 'scroll'].includes(style.overflowX)
      && (rect.left < ancestorRect.left - 1 || rect.right > ancestorRect.right + 1)
    const clipsY = ['hidden', 'clip', 'auto', 'scroll'].includes(style.overflowY)
      && (rect.top < ancestorRect.top - 1 || rect.bottom > ancestorRect.bottom + 1)
    if (clipsX || clipsY) clippingAncestors.push(ancestor.className || ancestor.tagName)
    ancestor = ancestor.parentElement
  }

  const violations = []
  if (clippingAncestors.length) violations.push(`trend-tooltip-clipped-by:${clippingAncestors.join(',')}`)
  if (rect.left < 0 || rect.top < 0 || rect.right > window.innerWidth || rect.bottom > window.innerHeight) {
    violations.push(`trend-tooltip-outside-viewport:${rect.left.toFixed(1)},${rect.top.toFixed(1)},${rect.right.toFixed(1)},${rect.bottom.toFixed(1)}`)
  }
  return {
    verdict: violations.length ? 'FAIL' : 'PASS',
    violations,
    parent: tooltip.parentElement?.className || tooltip.parentElement?.tagName,
    rect: { left: +rect.left.toFixed(1), top: +rect.top.toFixed(1), right: +rect.right.toFixed(1), bottom: +rect.bottom.toFixed(1) },
    clippingAncestors,
  }
}

let client
try {
  client = createCdpClient(await waitForDebuggerUrl())
  await client.ready
  const { targetId } = await client.send('Target.createTarget', { url: 'about:blank' })
  const { sessionId } = await client.send('Target.attachToTarget', { targetId, flatten: true })
  await client.send('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: false }, sessionId)
  await client.send('Page.enable', {}, sessionId)
  await client.send('Page.navigate', { url: dashboardUrl }, sessionId)
  await new Promise((resolve) => setTimeout(resolve, 1800))
  const response = await client.send('Runtime.evaluate', { expression: `(${probeLayout.toString()})()`, returnByValue: true }, sessionId)
  const result = response.result.value
  const chartPointResponse = await client.send('Runtime.evaluate', {
    expression: `(() => { const rect = document.querySelector('.raw-trend-chart canvas')?.getBoundingClientRect(); return rect ? { x: rect.left + rect.width * .7, y: rect.top + rect.height * .42 } : null })()`,
    returnByValue: true,
  }, sessionId)
  const chartPoint = chartPointResponse.result.value
  if (chartPoint) {
    await client.send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: 1, y: 1, button: 'none', buttons: 0, pointerType: 'mouse' }, sessionId)
    await client.send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: chartPoint.x, y: chartPoint.y }, sessionId)
    await client.send('Runtime.evaluate', {
      expression: `(() => { const canvas = document.querySelector('.raw-trend-chart canvas'); const rect = canvas?.getBoundingClientRect(); if (!canvas || !rect) return false; canvas.dispatchEvent(new MouseEvent('mousemove', { bubbles: true, clientX: rect.left + rect.width * .7, clientY: rect.top + rect.height * .42, view: window })); return true })()`,
    }, sessionId)
    await new Promise((resolve) => setTimeout(resolve, 400))
  }
  const tooltipResponse = await client.send('Runtime.evaluate', { expression: `(${probeTrendTooltip.toString()})()`, returnByValue: true }, sessionId)
  result.tooltip = tooltipResponse.result.value
  if (result.tooltip.verdict !== 'PASS') {
    result.verdict = 'FAIL'
    result.violations.push(...result.tooltip.violations)
  }
  console.log(JSON.stringify(result, null, 2))
  if (result.verdict !== 'PASS') process.exitCode = 1
  await client.send('Browser.close').catch(() => {})
} finally {
  client?.close()
  chrome.kill()
  await rm(profilePath, { recursive: true, force: true }).catch(() => {})
}

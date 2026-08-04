(function () {
  'use strict';

  var DATA = window.WAREHOUSE_DATA || { meta: {}, daily: [], zones: [], alerts: [], targets: [], forklifts: [] };
  var VARIANTS = {
    A: { name: '整体运营全景', note: '客户与主管共用视图' },
    B: { name: '流程作战板', note: '端到端履约优先' },
    C: { name: '空间态势图', note: '库区与资源优先' }
  };
  var query = new URLSearchParams(window.location.search);
  var initialVariant = String(query.get('variant') || 'A').toUpperCase();
  var dates = (DATA.daily || []).map(function (row) { return row.date; });
  var state = {
    variant: VARIANTS[initialVariant] ? initialVariant : 'A',
    range: 'day',
    selectedDate: dates[dates.length - 1] || DATA.meta.latestDate || '2026-07-31',
    selectedZone: null
  };

  var app = document.getElementById('app');
  var overlayRoot = document.getElementById('overlay-root');
  var numberFormat = new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 0 });
  var decimalFormat = new Intl.NumberFormat('zh-CN', { minimumFractionDigits: 1, maximumFractionDigits: 1 });

  function clamp(value, min, max) {
    return Math.min(max, Math.max(min, Number(value) || 0));
  }

  function sum(rows, key) {
    return rows.reduce(function (total, row) { return total + (Number(row[key]) || 0); }, 0);
  }

  function avg(rows, key) {
    if (!rows.length) return 0;
    var valid = rows.filter(function (row) { return row[key] !== null && row[key] !== undefined && row[key] !== ''; });
    if (!valid.length) return 0;
    return sum(valid, key) / valid.length;
  }

  function normalizeRate(value) {
    var n = Number(value) || 0;
    return n > 1 ? n / 100 : n;
  }

  function fmt(value) {
    return numberFormat.format(Math.round(Number(value) || 0));
  }

  function fmt1(value) {
    return decimalFormat.format(Number(value) || 0);
  }

  function pct(value, digits) {
    var n = normalizeRate(value) * 100;
    return n.toFixed(digits === undefined ? 1 : digits) + '%';
  }

  function escapeHtml(value) {
    return String(value === undefined || value === null ? '' : value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  function parseDateLabel(value) {
    var parts = String(value).split('-');
    if (parts.length !== 3) return value;
    return Number(parts[1]) + '月' + Number(parts[2]) + '日';
  }

  function getEndIndex() {
    var index = (DATA.daily || []).findIndex(function (row) { return row.date === state.selectedDate; });
    return index >= 0 ? index : Math.max(0, (DATA.daily || []).length - 1);
  }

  function getRowsForRange(range, before) {
    var all = DATA.daily || [];
    if (!all.length) return [];
    var end = getEndIndex();
    var count = range === 'day' ? 1 : range === 'week' ? 7 : end + 1;
    if (before) {
      var priorEnd = end - count;
      if (priorEnd < 0) return [];
      return all.slice(Math.max(0, priorEnd - count + 1), priorEnd + 1);
    }
    return all.slice(Math.max(0, end - count + 1), end + 1);
  }

  function getTrendRows() {
    var all = DATA.daily || [];
    if (!all.length) return [];
    var end = getEndIndex();
    var count = state.range === 'month' ? Math.min(31, end + 1) : state.range === 'week' ? 7 : 7;
    return all.slice(Math.max(0, end - count + 1), end + 1);
  }

  function selectedAlerts() {
    var activeRows = getRowsForRange(state.range);
    var selectedDates = new Set(activeRows.map(function (row) { return row.date; }));
    var alerts = (DATA.alerts || []).filter(function (alert) {
      return !alert.date || selectedDates.has(alert.date) || alert.status !== '已关闭';
    });
    var rank = { '紧急': 0, '高': 0, '严重': 0, '重要': 1, '中': 1, '一般': 2, '低': 2 };
    return alerts.sort(function (a, b) {
      var ra = rank[a.severity] === undefined ? 3 : rank[a.severity];
      var rb = rank[b.severity] === undefined ? 3 : rank[b.severity];
      if (ra !== rb) return ra - rb;
      return String(b.date || '').localeCompare(String(a.date || ''));
    });
  }

  function getSummary() {
    var rows = getRowsForRange(state.range);
    var prior = getRowsForRange(state.range, true);
    var zones = DATA.zones || [];
    var detailAlerts = DATA.alerts || [];
    var openAlerts = detailAlerts.filter(function (item) { return item.status !== '已关闭'; });
    var closedAlerts = detailAlerts.filter(function (item) { return item.status === '已关闭'; });
    var occupiedLocations = zones.reduce(function (t, z) { return t + (Number(z.occupied) || 0); }, 0);
    var totalLocations = zones.reduce(function (t, z) { return t + (Number(z.capacity) || 0); }, 0);
    return {
      rows: rows,
      inbound: sum(rows, 'inbound'),
      outbound: sum(rows, 'outbound'),
      picking: sum(rows, 'picking'),
      forkliftTasks: sum(rows, 'forkliftTasks'),
      inventoryAccuracy: avg(rows, 'inventoryAccuracy'),
      receivingTimely: avg(rows, 'receivingTimely'),
      deliveryTimely: avg(rows, 'deliveryTimely'),
      pickAccuracy: avg(rows, 'pickAccuracy'),
      averageDuration: avg(rows, 'averageDuration'),
      exceptionCount: sum(rows, 'exceptions'),
      occupancy: totalLocations ? occupiedLocations / totalLocations : 0,
      occupiedLocations: occupiedLocations,
      totalLocations: totalLocations,
      availableLocations: zones.reduce(function (t, z) { return t + (Number(z.available) || 0); }, 0),
      frozenLocations: zones.reduce(function (t, z) { return t + (Number(z.frozen) || 0); }, 0),
      abnormalLocations: zones.reduce(function (t, z) { return t + (Number(z.abnormal) || 0); }, 0),
      openAlerts: openAlerts,
      closedAlerts: closedAlerts,
      closeRate: detailAlerts.length ? closedAlerts.length / detailAlerts.length : 0,
      prior: {
        inbound: sum(prior, 'inbound'),
        outbound: sum(prior, 'outbound'),
        picking: sum(prior, 'picking'),
        forkliftTasks: sum(prior, 'forkliftTasks'),
        exceptionCount: sum(prior, 'exceptions'),
        inventoryAccuracy: avg(prior, 'inventoryAccuracy'),
        deliveryTimely: avg(prior, 'deliveryTimely')
      }
    };
  }

  function delta(current, previous, isRate) {
    if (!previous) return { text: '暂无对比', tone: 'warn' };
    var change = isRate ? (normalizeRate(current) - normalizeRate(previous)) * 100 : ((current - previous) / Math.abs(previous)) * 100;
    var sign = change > 0 ? '+' : '';
    return {
      text: sign + change.toFixed(1) + (isRate ? 'pp' : '%'),
      tone: change >= 0 ? 'good' : 'bad'
    };
  }

  function rangeLabel() {
    return state.range === 'day' ? '当日' : state.range === 'week' ? '近7日' : '本月';
  }

  function radialGauge(value, label, tone, size) {
    var n = clamp(normalizeRate(value), 0, 1);
    var radius = 42;
    var circumference = 2 * Math.PI * radius;
    var dash = n * circumference;
    var color = tone === 'bad' ? 'var(--red)' : tone === 'warn' ? 'var(--amber)' : 'var(--mint)';
    return '<div class="radial-gauge" style="' + (size ? 'width:' + size + 'px;height:' + size + 'px' : '') + '">' +
      '<svg viewBox="0 0 100 100" aria-hidden="true">' +
        '<circle cx="50" cy="50" r="' + radius + '" fill="none" stroke="rgba(157,205,211,.1)" stroke-width="7"></circle>' +
        '<circle cx="50" cy="50" r="' + radius + '" fill="none" stroke="' + color + '" stroke-width="7" stroke-linecap="round" stroke-dasharray="' + dash.toFixed(2) + ' ' + (circumference - dash).toFixed(2) + '"></circle>' +
      '</svg>' +
      '<div class="gauge-value"><strong>' + pct(n, 0) + '</strong><small>' + escapeHtml(label) + '</small></div>' +
    '</div>';
  }

  function progress(value, tone) {
    return '<div class="progress-track"><span class="' + (tone || '') + '" style="width:' + clamp(normalizeRate(value) * 100, 2, 100) + '%"></span></div>';
  }

  function panelHeader(title, subtitle, action, actionId) {
    return '<div class="panel-header"><div><h3 class="panel-title">' + escapeHtml(title) + '</h3>' +
      (subtitle ? '<p class="panel-subtitle">' + escapeHtml(subtitle) + '</p>' : '') +
      '</div>' +
      (action ? '<button class="panel-action" type="button" data-action="' + escapeHtml(actionId || '') + '">' + escapeHtml(action) + '</button>' : '') +
      '</div>';
  }

  function kpiCard(label, value, unit, deltaInfo, progressValue, tone, primary, footLabel) {
    var deltaText = deltaInfo ? deltaInfo.text : '目标监测';
    var deltaTone = deltaInfo ? deltaInfo.tone : 'good';
    return '<div class="kpi ' + (primary ? 'is-primary' : '') + '">' +
      '<div class="kpi-label"><span>' + escapeHtml(label) + '</span><span class="status-pill ' + (tone || '') + '">' + rangeLabel() + '</span></div>' +
      '<div class="kpi-value-row"><strong class="kpi-value">' + escapeHtml(value) + '</strong><span class="kpi-unit">' + escapeHtml(unit || '') + '</span></div>' +
      '<div class="kpi-foot"><span>' + escapeHtml(footLabel || '较上一周期') + '</span><span class="delta ' + deltaTone + '">' + escapeHtml(deltaText) + '</span></div>' +
      '<div class="mini-track ' + (tone === 'warn' ? 'is-amber' : tone === 'bad' ? 'is-red' : tone === 'cyan' ? 'is-cyan' : '') + '"><span style="width:' + clamp(progressValue || 0, 3, 100) + '%"></span></div>' +
    '</div>';
  }

  function kpiRail(summary) {
    var throughput = summary.inbound + summary.outbound;
    var priorThroughput = summary.prior.inbound + summary.prior.outbound;
    var closeTone = summary.closeRate < targetValue('exceptionCloseRate', 0.9) ? 'bad' : '';
    return '<section class="kpi-rail" aria-label="核心运营指标">' +
      kpiCard('入出库吞吐', fmt(throughput), '箱', delta(throughput, priorThroughput), Math.min(100, throughput / Math.max(1, priorThroughput) * 75), '', true) +
      kpiCard('成品入库', fmt(summary.inbound), '箱', delta(summary.inbound, summary.prior.inbound), Math.min(100, summary.inbound / Math.max(1, summary.outbound) * 90), '') +
      kpiCard('成品出库', fmt(summary.outbound), '箱', delta(summary.outbound, summary.prior.outbound), Math.min(100, summary.outbound / Math.max(1, summary.inbound) * 88), 'cyan') +
      kpiCard('库存准确率', pct(summary.inventoryAccuracy), '', delta(summary.inventoryAccuracy, summary.prior.inventoryAccuracy, true), normalizeRate(summary.inventoryAccuracy) * 100, summary.inventoryAccuracy < targetValue('inventoryAccuracy', 0.98) ? 'warn' : '') +
      kpiCard('发货及时率', pct(summary.deliveryTimely), '', delta(summary.deliveryTimely, summary.prior.deliveryTimely, true), normalizeRate(summary.deliveryTimely) * 100, summary.deliveryTimely < targetValue('deliveryTimely', 0.95) ? 'bad' : '') +
      kpiCard('未关闭异常', fmt(summary.openAlerts.length), '项', { text: pct(summary.closeRate), tone: closeTone ? 'bad' : 'good' }, normalizeRate(summary.closeRate) * 100, closeTone, false, '异常关闭率') +
    '</section>';
  }

  function targetValue(key, fallback) {
    var target = (DATA.targets || []).find(function (item) { return item.key === key; });
    return target ? Number(target.target) : fallback;
  }

  function topbar() {
    return '<header class="topbar">' +
      '<div class="brand-lockup"><div class="brand-mark" aria-hidden="true"></div><div class="brand-copy">' +
        '<p class="eyebrow">Multi-source Warehouse Data</p><p class="brand-subline">' + escapeHtml((DATA.meta.warehouseCount || 3) + ' 类仓库 · ' + (DATA.meta.declaredZoneCount || 12) + ' 个库区') + '</p>' +
      '</div></div>' +
      '<div class="screen-title"><p>Warehouse Operations Overview</p><h1>仓库运营全景主板</h1></div>' +
      '<div class="shift-clock"><div class="clock-copy"><p class="clock-time" id="clock-time">--:--:--</p><p class="clock-date" id="clock-date">--</p></div><span class="live-chip">数据截至 ' + escapeHtml(state.selectedDate) + '</span></div>' +
    '</header>';
  }

  function boardIntro(title, description) {
    var source = DATA.meta.source || '仓库运营模拟数据集';
    return '<div class="board-intro"><div><h2>' + escapeHtml(title) + '</h2><p>' + escapeHtml(description) + '</p></div>' +
      '<div class="data-stamp"><span class="dot"></span><span>' + escapeHtml(source) + ' · 截至 ' + escapeHtml(state.selectedDate) + '</span></div></div>';
  }

  function operatingHealth(summary) {
    var occupancyTarget = targetValue('occupancy', 0.85);
    var occupancyScore = summary.occupancy <= occupancyTarget ? 1 : clamp(1 - (summary.occupancy - occupancyTarget) / Math.max(0.01, 1 - occupancyTarget), 0, 1);
    var healthScore = (normalizeRate(summary.inventoryAccuracy) + normalizeRate(summary.receivingTimely) + normalizeRate(summary.deliveryTimely) + normalizeRate(summary.closeRate) + occupancyScore) / 5 * 100;
    var attentionCount = [
      summary.inventoryAccuracy < targetValue('inventoryAccuracy', 0.98),
      summary.receivingTimely < targetValue('receivingTimely', 0.95),
      summary.deliveryTimely < targetValue('deliveryTimely', 0.95),
      summary.closeRate < targetValue('exceptionCloseRate', 0.9),
      summary.occupancy > occupancyTarget
    ].filter(Boolean).length;
    var tone = healthScore < 85 ? 'bad' : attentionCount ? 'warn' : '';
    var stateLabel = healthScore >= 90 ? '总体平稳' : healthScore >= 85 ? '重点关注' : '存在风险';
    return { score: healthScore, attentionCount: attentionCount, tone: tone, label: stateLabel };
  }

  function overviewIntro(summary) {
    var health = operatingHealth(summary);
    var source = DATA.meta.source || '仓库运营模拟数据集';
    return '<div class="board-intro overview-intro"><div><h2>仓库整体运营态势</h2><p>' +
      escapeHtml((DATA.meta.warehouseCount || 3) + ' 类仓库整体视图 · 规模 / 服务 / 空间 / 风险 / 资源') +
      '</p></div><div class="overview-health ' + health.tone + '" title="' + escapeHtml(source + ' · 截至 ' + state.selectedDate) + '">' +
      '<span class="overview-health-dot"></span><span class="overview-health-label">整体健康度</span><strong>' + Math.round(health.score) + '</strong><small>分</small>' +
      '<span class="overview-health-divider"></span><span class="overview-health-state">' + escapeHtml(health.label + (health.attentionCount ? ' · ' + health.attentionCount + '项关注' : '')) + '</span></div></div>';
  }

  function trendChart(rows) {
    if (!rows.length) return '<div class="empty-state">暂无趋势数据</div>';
    var width = 660;
    var height = 190;
    var left = 36;
    var right = 12;
    var top = 14;
    var bottom = 28;
    var plotW = width - left - right;
    var plotH = height - top - bottom;
    var keys = ['inbound', 'outbound', 'picking'];
    var max = Math.max.apply(Math, rows.reduce(function (arr, row) {
      keys.forEach(function (key) { arr.push(Number(row[key]) || 0); });
      return arr;
    }, [1])) * 1.1;
    var x = function (i) { return left + (rows.length === 1 ? plotW / 2 : i * plotW / (rows.length - 1)); };
    var y = function (v) { return top + plotH - ((Number(v) || 0) / max) * plotH; };
    var pathFor = function (key) {
      return rows.map(function (row, i) { return (i ? 'L' : 'M') + x(i).toFixed(1) + ',' + y(row[key]).toFixed(1); }).join(' ');
    };
    var inboundPath = pathFor('inbound');
    var areaPath = inboundPath + ' L' + x(rows.length - 1).toFixed(1) + ',' + (top + plotH) + ' L' + x(0).toFixed(1) + ',' + (top + plotH) + ' Z';
    var grid = [0, 0.5, 1].map(function (ratio) {
      var gy = top + plotH * ratio;
      var label = fmt(max * (1 - ratio));
      return '<line class="grid-line" x1="' + left + '" y1="' + gy + '" x2="' + (width - right) + '" y2="' + gy + '"></line><text x="0" y="' + (gy + 3) + '">' + label + '</text>';
    }).join('');
    var labelStep = rows.length > 12 ? 4 : rows.length > 7 ? 2 : 1;
    var labels = rows.map(function (row, i) {
      if (i % labelStep !== 0 && i !== rows.length - 1) return '';
      return '<text x="' + x(i) + '" y="' + (height - 5) + '" text-anchor="middle">' + escapeHtml(parseDateLabel(row.date)) + '</text>';
    }).join('');
    return '<svg class="trend-chart" viewBox="0 0 ' + width + ' ' + height + '" preserveAspectRatio="none" role="img" aria-label="入库、出库与拣货趋势">' +
      '<defs><linearGradient id="areaMint" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#22d3a7" stop-opacity=".34"></stop><stop offset="100%" stop-color="#22d3a7" stop-opacity="0"></stop></linearGradient></defs>' +
      grid + '<path class="area-inbound" d="' + areaPath + '"></path>' +
      '<path class="line-inbound" d="' + inboundPath + '"></path>' +
      '<path class="line-outbound" d="' + pathFor('outbound') + '"></path>' +
      '<path class="line-picking" d="' + pathFor('picking') + '"></path>' + labels +
    '</svg>';
  }

  function zoneTone(zone) {
    var rate = normalizeRate(zone.occupancy);
    return rate >= 0.9 || zone.status === '高负荷' || zone.status === '异常' ? 'bad' : rate >= 0.8 || zone.status === '预警' || zone.status === '偏高' ? 'warn' : '';
  }

  function zoneBars() {
    return (DATA.zones || []).slice().sort(function (a, b) { return normalizeRate(b.occupancy) - normalizeRate(a.occupancy); }).slice(0, 5).map(function (zone) {
      var tone = zoneTone(zone);
      return '<div class="zone-bar"><span>' + escapeHtml(zone.name || zone.code) + '</span>' +
        progress(zone.occupancy, tone === 'bad' ? 'is-bad' : tone === 'warn' ? 'is-warn' : '') +
        '<b>' + pct(zone.occupancy, 0) + '</b></div>';
    }).join('');
  }

  function alertItems(limit) {
    var alerts = selectedAlerts().filter(function (alert) { return alert.status !== '已关闭'; }).slice(0, limit || 7);
    if (!alerts.length) return '<div class="empty-state">当前范围无未关闭异常</div>';
    return '<div class="alert-list">' + alerts.map(function (alert, index) {
      var high = alert.severity === '紧急' || alert.severity === '高' || alert.severity === '严重';
      var low = alert.severity === '一般' || alert.severity === '低';
      var age = alert.durationHours !== undefined && alert.durationHours !== null ? fmt1(alert.durationHours) + 'h' : parseDateLabel(alert.date || '');
      return '<button class="alert-item" type="button" data-alert-index="' + index + '">' +
        '<span class="severity-dot ' + (high ? 'high' : low ? 'low' : '') + '"></span>' +
        '<span class="alert-main"><strong>' + escapeHtml(alert.title || alert.type) + '</strong><span>' + escapeHtml((alert.zone || '全仓') + ' · ' + (alert.owner || '待分派') + ' · ' + (alert.status || '待处理')) + '</span></span>' +
        '<span class="alert-age">' + escapeHtml(age) + '</span>' +
      '</button>';
    }).join('') + '</div>';
  }

  function processStrip(summary) {
    var steps = [
      { code: '01', name: '收货入库', value: summary.inbound, unit: '箱', rate: summary.receivingTimely, note: '及时率 ' + pct(summary.receivingTimely) },
      { code: '02', name: '库内上架', value: summary.forkliftTasks, unit: '任务', rate: Math.max(0.01, 1 - summary.averageDuration / 240), note: '均时 ' + fmt1(summary.averageDuration) + ' 分' },
      { code: '03', name: '订单拣货', value: summary.picking, unit: '任务', rate: Math.min(1, targetValue('pickingMinutes', 45) / Math.max(1, summary.averageDuration)), note: '均时 ' + fmt1(summary.averageDuration) + ' 分' },
      { code: '04', name: '复核出库', value: summary.outbound, unit: '箱', rate: summary.deliveryTimely, note: '及时率 ' + pct(summary.deliveryTimely) }
    ];
    return '<div class="process-strip">' + steps.map(function (step) {
      return '<div class="process-step"><span class="step-node">' + step.code + '</span><strong>' + fmt(step.value) + '</strong><span>' + step.name + ' · ' + step.unit + '</span><em>' + escapeHtml(step.note) + '</em></div>';
    }).join('') + '</div>';
  }

  function fleetOverview(summary) {
    var fleet = DATA.forklifts || [];
    if (fleet.length && fleet.some(function (item) { return item.load !== undefined; })) {
      var totalTasks = fleet.reduce(function (total, item) { return total + (Number(item.tasks) || 0); }, 0);
      var weightedLoad = fleet.reduce(function (total, item) {
        return total + (Number(item.load) || 0) * (Number(item.tasks) || 0);
      }, 0) / Math.max(1, totalTasks);
      return '<div class="fleet-overview"><div class="fleet-score"><strong>' + fmt(summary.forkliftTasks) + '</strong><span>' + rangeLabel() + '叉车任务</span></div>' +
        '<div class="fleet-stack">' + fleet.map(function (item) {
          return '<div class="fleet-row"><span>' + escapeHtml(item.zone) + '</span>' + progress(item.load, item.load >= 0.85 ? 'is-warn' : '') + '<b>' + fmt(item.tasks) + '</b></div>';
        }).join('') +
        '<div class="fleet-row"><span>综合负荷</span>' + progress(weightedLoad, weightedLoad >= 0.85 ? 'is-warn' : '') + '<b>' + pct(weightedLoad, 0) + '</b></div></div></div>';
    }
    var working = fleet.filter(function (item) { return item.status === '执行中' || item.status === '作业中'; }).length;
    var idle = fleet.filter(function (item) { return item.status === '空闲'; }).length;
    var charging = fleet.filter(function (item) { return item.status === '充电' || item.status === '维保'; }).length;
    var total = fleet.length || Math.max(1, summary.forkliftTasks);
    var utilization = fleet.length ? working / total : Math.min(1, summary.forkliftTasks / 100);
    return '<div class="fleet-overview"><div class="fleet-score"><strong>' + pct(utilization, 0) + '</strong><span>车辆任务占用</span></div>' +
      '<div class="fleet-stack">' +
        '<div class="fleet-row"><span>执行中</span>' + progress(total ? working / total : 0) + '<b>' + fmt(working) + '</b></div>' +
        '<div class="fleet-row"><span>空闲</span>' + progress(total ? idle / total : 0) + '<b>' + fmt(idle) + '</b></div>' +
        '<div class="fleet-row"><span>充电/维保</span>' + progress(total ? charging / total : 0, 'is-warn') + '<b>' + fmt(charging) + '</b></div>' +
      '</div></div>';
  }

  function getMonthSummary() {
    var rows = getRowsForRange('month');
    return {
      days: rows.length,
      inbound: sum(rows, 'inbound'),
      outbound: sum(rows, 'outbound'),
      picking: sum(rows, 'picking'),
      forkliftTasks: sum(rows, 'forkliftTasks'),
      exceptions: sum(rows, 'exceptions'),
      deliveryTimely: avg(rows, 'deliveryTimely')
    };
  }

  function warehouseVisual() {
    return '<svg class="warehouse-visual" viewBox="0 0 520 150" aria-hidden="true">' +
      '<defs><linearGradient id="rackFace" x1="0" x2="1"><stop offset="0" stop-color="#174457"></stop><stop offset="1" stop-color="#0a2630"></stop></linearGradient>' +
      '<linearGradient id="floorGlow" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#55c7f3" stop-opacity=".16"></stop><stop offset="1" stop-color="#55c7f3" stop-opacity="0"></stop></linearGradient>' +
      '<pattern id="floorGrid" width="28" height="18" patternUnits="userSpaceOnUse"><path d="M28 0H0V18" fill="none" stroke="#55c7f3" stroke-opacity=".12" stroke-width="1"></path></pattern></defs>' +
      '<path d="M34 128L260 72l226 56-226 20z" fill="url(#floorGlow)"></path><path d="M34 128L260 72l226 56-226 20z" fill="url(#floorGrid)"></path>' +
      '<g class="rack rack-left" transform="translate(78 24)"><path d="M0 92V8M112 92V8M0 24h112M0 48h112M0 72h112" fill="none" stroke="#55c7f3" stroke-width="3"></path>' +
      '<g fill="url(#rackFace)" stroke="#22d3a7" stroke-opacity=".42"><rect x="8" y="13" width="27" height="17" rx="2"></rect><rect x="41" y="13" width="27" height="17" rx="2"></rect><rect x="74" y="13" width="30" height="17" rx="2"></rect><rect x="8" y="37" width="45" height="17" rx="2"></rect><rect x="59" y="37" width="45" height="17" rx="2"></rect><rect x="8" y="61" width="30" height="17" rx="2"></rect><rect x="44" y="61" width="60" height="17" rx="2"></rect></g></g>' +
      '<g class="rack rack-right" transform="translate(330 24)"><path d="M0 92V8M112 92V8M0 24h112M0 48h112M0 72h112" fill="none" stroke="#55c7f3" stroke-width="3"></path>' +
      '<g fill="url(#rackFace)" stroke="#22d3a7" stroke-opacity=".42"><rect x="8" y="13" width="45" height="17" rx="2"></rect><rect x="59" y="13" width="45" height="17" rx="2"></rect><rect x="8" y="37" width="30" height="17" rx="2"></rect><rect x="44" y="37" width="60" height="17" rx="2"></rect><rect x="8" y="61" width="45" height="17" rx="2"></rect><rect x="59" y="61" width="45" height="17" rx="2"></rect></g></g>' +
      '<g transform="translate(218 42)"><path d="M0 73V5h84v68M0 24h84M0 45h84" fill="none" stroke="#22d3a7" stroke-width="3"></path>' +
      '<rect x="8" y="11" width="30" height="18" rx="2" fill="#1c5060"></rect><rect x="45" y="11" width="31" height="18" rx="2" fill="#f4bd52" fill-opacity=".5"></rect><rect x="8" y="32" width="68" height="18" rx="2" fill="#1c5060"></rect><rect x="8" y="53" width="30" height="18" rx="2" fill="#55c7f3" fill-opacity=".42"></rect><rect x="45" y="53" width="31" height="18" rx="2" fill="#1c5060"></rect></g>' +
      '<path d="M204 120h112" stroke="#22d3a7" stroke-width="2" stroke-linecap="round"></path></svg>';
  }

  function heroMetric(label, value, unit, tone) {
    return '<div class="hero-metric ' + (tone || '') + '"><span>' + escapeHtml(label) + '</span><strong>' + escapeHtml(value) + '</strong><small>' + escapeHtml(unit || '') + '</small></div>';
  }

  function monthMetric(label, value, unit, tone) {
    return '<div class="month-metric ' + (tone || '') + '"><span>' + escapeHtml(label) + '</span><div><strong>' + escapeHtml(value) + '</strong><small>' + escapeHtml(unit || '') + '</small></div></div>';
  }

  function serviceMetric(label, value, isWarn) {
    return '<div class="service-metric ' + (isWarn ? 'warn' : '') + '"><span>' + escapeHtml(label) + '</span><strong>' + escapeHtml(value) + '</strong></div>';
  }

  function staticAlertItems(limit) {
    var alerts = selectedAlerts().filter(function (item) { return item.status !== '已关闭'; }).slice(0, limit || 2);
    if (!alerts.length) return '<div class="showcase-empty">当前无未关闭异常</div>';
    return '<div class="showcase-alert-list">' + alerts.map(function (alert) {
      var high = alert.severity === '紧急' || alert.severity === '高' || alert.severity === '严重';
      var age = alert.durationHours !== undefined && alert.durationHours !== null ? fmt1(alert.durationHours) + 'h' : parseDateLabel(alert.date || '');
      return '<div class="showcase-alert-row"><span class="severity-dot ' + (high ? 'high' : '') + '"></span><span class="showcase-alert-copy"><strong>' + escapeHtml(alert.title || alert.type) + '</strong><span>' + escapeHtml((alert.zone || '全仓') + ' · ' + (alert.owner || '待分派')) + '</span></span><span class="alert-age">' + escapeHtml(age) + '</span></div>';
    }).join('') + '</div>';
  }

  function renderVariantA(summary) {
    var trendRows = getRowsForRange('month');
    var month = getMonthSummary();
    var health = operatingHealth(summary);
    var occupancyTone = summary.occupancy >= 0.9 ? 'bad' : summary.occupancy >= 0.8 ? 'warn' : '';
    return '<section class="showcase-grid" aria-label="仓库整体运营展示板">' +
        '<article class="panel showcase-trend">' + panelHeader('近 ' + trendRows.length + ' 日出入库', '固定窗口 · 成品箱数', '', '') +
          '<div class="panel-body showcase-trend-body"><div class="showcase-dual-kpis"><div><span>今日入库</span><strong>' + fmt(summary.inbound) + '</strong><small>箱</small></div><div><span>今日出库</span><strong>' + fmt(summary.outbound) + '</strong><small>箱</small></div></div>' +
          '<div class="legend"><span><i></i>入库</span><span><i class="cyan"></i>出库</span><span><i class="amber"></i>拣货</span></div>' + trendChart(trendRows) + '</div></article>' +
        '<article class="panel showcase-hero"><div class="hero-status-line"><span>仓库整体态势</span><small>数据截至 ' + escapeHtml(state.selectedDate) + '</small></div>' +
          '<div class="hero-visual-stage"><div class="hero-health ' + health.tone + '"><span>健康度</span><strong>' + Math.round(health.score) + '</strong><small>分</small></div>' + warehouseVisual() +
          '<div class="hero-state ' + health.tone + '"><i></i><strong>' + escapeHtml(health.label) + '</strong><span>' + escapeHtml(health.attentionCount + ' 项关注') + '</span></div></div>' +
          '<div class="hero-metric-grid">' +
            heroMetric('今日入库', fmt(summary.inbound), '箱') + heroMetric('今日出库', fmt(summary.outbound), '箱', 'cyan') + heroMetric('拣货任务', fmt(summary.picking), '单') +
            heroMetric('叉车任务', fmt(summary.forkliftTasks), '单') + heroMetric('已同步库位', fmt(summary.totalLocations), '个', 'cyan') + heroMetric('可用库位', fmt(summary.availableLocations), '个') +
          '</div></article>' +
        '<article class="panel showcase-month">' + panelHeader('本月运营累计', month.days + ' 天固定口径', '', '') +
          '<div class="panel-body"><div class="month-metric-grid">' + monthMetric('成品入库', fmt(month.inbound), '箱') + monthMetric('成品出库', fmt(month.outbound), '箱', 'cyan') + monthMetric('拣货任务', fmt(month.picking), '单') + monthMetric('叉车任务', fmt(month.forkliftTasks), '单', 'cyan') + '</div>' +
          '<div class="month-summary-line"><span>月度异常 <strong>' + fmt(month.exceptions) + '</strong> 起</span><span>平均发货及时率 <strong>' + pct(month.deliveryTimely) + '</strong></span></div></div></article>' +
        '<article class="panel showcase-capacity">' + panelHeader('库容与空间', '快照 ' + (DATA.meta.zoneSnapshotDate || '-') + ' · 已同步 ' + (DATA.meta.availableZoneRows || (DATA.zones || []).length) + '/' + (DATA.meta.declaredZoneCount || (DATA.zones || []).length) + ' 区', '', '') +
          '<div class="panel-body"><div class="capacity-summary">' + radialGauge(summary.occupancy, '平均占用', occupancyTone) + '<div class="capacity-copy"><h4>' + fmt(summary.totalLocations) + ' 个已同步库位</h4><p>已用 ' + fmt(summary.occupiedLocations) + ' · 可用 ' + fmt(summary.availableLocations) + ' · 冻结 ' + fmt(summary.frozenLocations) + '</p></div></div><div class="zone-bars">' + zoneBars() + '</div></div></article>' +
        '<article class="panel showcase-operations">' + panelHeader('今日作业链路', '收货 → 上架 → 拣货 → 出库', '', '') +
          '<div class="panel-body operations-layout">' + processStrip(summary) + '<div class="resource-band"><span class="resource-band-title">分库叉车负荷</span>' + fleetOverview(summary) + '</div></div></article>' +
        '<article class="panel showcase-risk">' + panelHeader('服务质量与风险', '目标达成及未关闭事件', '', '') +
          '<div class="panel-body risk-layout"><div class="service-metric-grid">' +
            serviceMetric('库存准确率', pct(summary.inventoryAccuracy), summary.inventoryAccuracy < targetValue('inventoryAccuracy', 0.98)) +
            serviceMetric('收货及时率', pct(summary.receivingTimely), summary.receivingTimely < targetValue('receivingTimely', 0.95)) +
            serviceMetric('发货及时率', pct(summary.deliveryTimely), summary.deliveryTimely < targetValue('deliveryTimely', 0.94)) +
          '</div>' + staticAlertItems(2) + '<div class="risk-footer"><span>未关闭 <strong>' + fmt(summary.openAlerts.length) + '</strong> 项</span><span>异常关闭率 <strong>' + pct(summary.closeRate) + '</strong></span></div></div></article>' +
      '</section>';
  }

  function flowStage(code, name, description, value, unit, rate, note, risk) {
    return '<div class="flow-stage ' + (risk ? 'is-risk' : '') + '">' +
      '<span class="flow-stage-num">' + escapeHtml(code) + '</span><h3>' + escapeHtml(name) + '</h3><p>' + escapeHtml(description) + '</p>' +
      '<div class="flow-stage-value"><strong>' + escapeHtml(value) + '</strong><small>' + escapeHtml(unit) + '</small></div>' +
      '<div class="flow-stage-meta"><span>' + escapeHtml(note) + '</span><b>' + pct(rate, 0) + '</b></div>' + progress(rate) +
    '</div>';
  }

  function renderVariantB(summary) {
    var queue = summary.openAlerts.slice(0, 5);
    var closeTarget = targetValue('exceptionCloseRate', 0.9);
    var deliveryTarget = targetValue('deliveryTimely', 0.95);
    var health = (normalizeRate(summary.receivingTimely) + normalizeRate(summary.deliveryTimely) + normalizeRate(summary.inventoryAccuracy) + normalizeRate(summary.closeRate)) / 4;
    return boardIntro('B · 流程作战板', '沿着货物流转追踪瓶颈，把资源调度放在流程上下文中。') +
      '<section class="flow-board"><div class="flow-main">' +
        '<article class="panel flow-hero">' + panelHeader('今日端到端履约链路', '节点颜色突出低于目标的环节', '', '') +
          '<div class="panel-body"><div class="flow-lane">' +
            flowStage('01 / IN', '到货收货', '预约到仓与收货确认', fmt(summary.inbound), '箱', summary.receivingTimely, '收货及时率', summary.receivingTimely < targetValue('receivingTimely', 0.95)) +
            flowStage('02 / PUT', '上架入位', '叉车搬运与库位确认', fmt(summary.forkliftTasks), '任务', Math.max(0.5, 1 - summary.averageDuration / 240), '作业健康度', false) +
            flowStage('03 / PICK', '订单拣货', '波次释放与拣货复核', fmt(summary.picking), '任务', Math.min(1, targetValue('pickingMinutes', 45) / Math.max(1, summary.averageDuration)), '平均 ' + fmt1(summary.averageDuration) + ' 分', summary.averageDuration > targetValue('pickingMinutes', 45)) +
            flowStage('04 / CHECK', '出库复核', '集货复核与装车等待', fmt(summary.outbound), '箱', summary.deliveryTimely, '发货及时率', summary.deliveryTimely < deliveryTarget) +
            flowStage('05 / CLOSE', '异常闭环', '责任分派与结案确认', fmt(summary.closedAlerts.length), '已关闭', summary.closeRate, '异常关闭率', summary.closeRate < closeTarget) +
          '</div></div></article>' +
        '<div class="flow-lower">' +
          '<article class="panel">' + panelHeader('承诺时钟', '关键 SLA 与目标差', '', '') +
            '<div class="panel-body"><div class="promise-grid">' +
              '<div class="promise-cell ' + (summary.receivingTimely < targetValue('receivingTimely', 0.95) ? 'warn' : '') + '"><span>收货及时率</span><strong>' + pct(summary.receivingTimely) + '</strong></div>' +
              '<div class="promise-cell ' + (summary.deliveryTimely < deliveryTarget ? 'bad' : '') + '"><span>发货及时率</span><strong>' + pct(summary.deliveryTimely) + '</strong></div>' +
              '<div class="promise-cell ' + (summary.closeRate < closeTarget ? 'warn' : '') + '"><span>异常关闭率</span><strong>' + pct(summary.closeRate) + '</strong></div>' +
            '</div></div></article>' +
          '<article class="panel">' + panelHeader('资源负载', '任务与空间同时观察', '', '') +
            '<div class="panel-body"><div class="task-lanes">' +
              '<div class="task-lane"><span>叉车任务</span>' + progress(Math.min(1, summary.forkliftTasks / Math.max(1, summary.inbound) * 5)) + '<b>' + fmt(summary.forkliftTasks) + '</b></div>' +
              '<div class="task-lane"><span>库区占用</span>' + progress(summary.occupancy, summary.occupancy > 0.9 ? 'is-bad' : summary.occupancy > 0.8 ? 'is-warn' : '') + '<b>' + pct(summary.occupancy, 0) + '</b></div>' +
              '<div class="task-lane"><span>异常积压</span>' + progress(Math.min(1, summary.openAlerts.length / Math.max(1, selectedAlerts().length)), 'is-warn') + '<b>' + fmt(summary.openAlerts.length) + '</b></div>' +
              '<div class="task-lane"><span>库存准确</span>' + progress(summary.inventoryAccuracy) + '<b>' + pct(summary.inventoryAccuracy, 0) + '</b></div>' +
            '</div></div></article>' +
        '</div></div>' +
        '<aside class="flow-rail">' +
          '<article class="panel">' + panelHeader('运营健康度', rangeLabel() + '综合状态', '', '') +
            '<div class="panel-body"><div class="sla-orbit">' + radialGauge(health, '综合评分', health < 0.9 ? 'warn' : '', 118) +
            '<div class="orbit-copy"><strong>' + (health * 100).toFixed(1) + '</strong><span>满分 100</span><p>综合库存准确率、收发及时率与异常关闭率。</p></div></div></div></article>' +
          '<article class="panel">' + panelHeader('待处理队列', '影响履约的开放事项', '', '') +
            '<div class="panel-body"><div class="queue-list">' + (queue.length ? queue.map(function (alert, index) {
              return '<button class="alert-item" type="button" data-alert-index="' + index + '"><span class="queue-id">' + String(index + 1).padStart(2, '0') + '</span><span class="queue-copy"><strong>' + escapeHtml(alert.title || alert.type) + '</strong><span>' + escapeHtml((alert.zone || '全仓') + ' · ' + (alert.owner || '待分派')) + '</span></span><span class="queue-time">' + fmt1(alert.durationHours || 0) + 'h</span></button>';
            }).join('') : '<div class="empty-state">暂无待处理事项</div>') + '</div></div></article>' +
          '<article class="panel">' + panelHeader('目标影响', '当前与 KPI 警戒线', '', '') +
            '<div class="panel-body"><div class="impact-list">' +
              '<div class="impact-item"><span>库存准确率</span><strong>' + pct(summary.inventoryAccuracy) + '</strong></div>' +
              '<div class="impact-item"><span>库区平均占用</span><strong>' + pct(summary.occupancy) + '</strong></div>' +
              '<div class="impact-item"><span>开放异常</span><strong>' + fmt(summary.openAlerts.length) + '</strong></div>' +
            '</div></div></article>' +
        '</aside></section>';
  }

  var zoneLayouts = [
    { c: 1, r: 1, w: 3, h: 2 }, { c: 4, r: 1, w: 3, h: 2 },
    { c: 8, r: 1, w: 2, h: 3 }, { c: 10, r: 1, w: 3, h: 3 },
    { c: 1, r: 3, w: 2, h: 2 }, { c: 3, r: 3, w: 2, h: 2 },
    { c: 5, r: 3, w: 2, h: 2 }, { c: 1, r: 5, w: 2, h: 2 },
    { c: 3, r: 5, w: 2, h: 2 }, { c: 5, r: 5, w: 2, h: 2 },
    { c: 7, r: 4, w: 3, h: 3 }, { c: 10, r: 4, w: 3, h: 3 }
  ];

  function warehouseMap() {
    var zones = DATA.zones || [];
    var declared = Number(DATA.meta.declaredZoneCount) || zones.length;
    var missing = Math.max(0, declared - zones.length);
    var known = zones.map(function (zone, index) {
      var layout = zoneLayouts[index % zoneLayouts.length];
      var tone = zoneTone(zone);
      return '<button type="button" class="zone-block is-' + tone + '" data-zone="' + escapeHtml(zone.code) + '" style="grid-column:' + layout.c + ' / span ' + layout.w + ';grid-row:' + layout.r + ' / span ' + layout.h + ';--fill:' + clamp(normalizeRate(zone.occupancy) * 100, 5, 100) + '%">' +
        '<span class="zone-content"><span class="zone-name">' + escapeHtml(zone.name || zone.code) + '</span><span class="zone-util"><span>' + escapeHtml(zone.status || '正常') + '</span><strong>' + pct(zone.occupancy, 0) + '</strong></span></span>' +
      '</button>';
    }).join('');
    var placeholders = Array.from({ length: missing }, function (_, offset) {
      var index = zones.length + offset;
      var layout = zoneLayouts[index % zoneLayouts.length];
      return '<div class="zone-block is-missing" style="grid-column:' + layout.c + ' / span ' + layout.w + ';grid-row:' + layout.r + ' / span ' + layout.h + '">' +
        '<span class="zone-content"><span class="zone-name">库区快照待同步</span><span class="zone-util"><span>缺失明细</span><strong>' + String(offset + 1).padStart(2, '0') + '</strong></span></span></div>';
    }).join('');
    return '<div class="warehouse-map">' + known + placeholders + '</div>' +
    '<div class="map-legend"><span><i></i>正常 &lt; 80%</span><span><i class="warn"></i>关注 80–89%</span><span><i class="bad"></i>高负荷 ≥ 90%</span><span class="missing-key">斜纹：快照待同步</span></div>';
  }

  function resourceRows() {
    var fleet = (DATA.forklifts || []).slice(0, 6);
    if (!fleet.length) return '<div class="empty-state">暂无车辆明细</div>';
    return '<div class="resource-panel">' + fleet.map(function (item) {
      var label = item.id || item.vehicle || 'FLT';
      var status = item.status || '执行中';
      return '<div class="resource-row"><span class="resource-icon">' + escapeHtml(String(label).slice(-3)) + '</span>' +
        '<span class="resource-copy"><strong>' + escapeHtml(label) + '</strong><span>' + escapeHtml((item.task || '待命') + ' · ' + (item.zone || '全仓')) + '</span></span>' +
        '<span class="resource-status">' + escapeHtml(status) + '</span></div>';
    }).join('') + '</div>';
  }

  function timelineItems() {
    var items = selectedAlerts().slice(0, 6);
    if (!items.length) return '<div class="empty-state">暂无事件</div>';
    return '<div class="timeline-list">' + items.map(function (item) {
      return '<div class="timeline-item"><span class="timeline-node"></span><span class="timeline-copy"><strong>' + escapeHtml(item.title || item.type) + '</strong><span>' + escapeHtml((item.zone || '全仓') + ' · ' + (item.status || '待处理')) + '</span></span><span class="timeline-time">' + escapeHtml(parseDateLabel(item.date || '')) + '</span></div>';
    }).join('') + '</div>';
  }

  function spatialTrend() {
    var rows = getTrendRows().slice(-7);
    var max = Math.max.apply(Math, rows.reduce(function (arr, row) { arr.push(Number(row.inbound) || 0, Number(row.outbound) || 0); return arr; }, [1]));
    return '<div class="spatial-trend">' + rows.map(function (row) {
      return '<div class="day-column"><div class="day-bars"><i style="height:' + clamp(row.inbound / max * 100, 5, 100) + '%"></i><i style="height:' + clamp(row.outbound / max * 100, 5, 100) + '%"></i></div><span>' + escapeHtml(String(row.date).slice(5)) + '</span></div>';
    }).join('') + '</div>';
  }

  function renderVariantC(summary) {
    return boardIntro('C · 空间态势图', '把库位容量、异常位置与叉车资源放回真实的空间上下文。') +
      '<section class="spatial-board">' +
        '<aside class="panel spatial-left">' + panelHeader('全仓态势', rangeLabel() + '核心指标', '', '') +
          '<div class="metric-column">' +
            '<div class="metric-tile"><span>库位占用</span><strong>' + pct(summary.occupancy) + '</strong><small>' + fmt(summary.occupiedLocations) + ' / ' + fmt(summary.totalLocations) + ' 个库位</small></div>' +
            '<div class="metric-tile"><span>当期入库</span><strong>' + fmt(summary.inbound) + '</strong><small>收货及时率 ' + pct(summary.receivingTimely) + '</small></div>' +
            '<div class="metric-tile"><span>当期出库</span><strong>' + fmt(summary.outbound) + '</strong><small>发货及时率 ' + pct(summary.deliveryTimely) + '</small></div>' +
            '<div class="metric-tile"><span>拣货任务</span><strong>' + fmt(summary.picking) + '</strong><small>平均 ' + fmt1(summary.averageDuration) + ' 分钟</small></div>' +
            '<div class="metric-tile"><span>叉车任务</span><strong>' + fmt(summary.forkliftTasks) + '</strong><small>均时 ' + fmt1(summary.averageDuration) + ' 分钟</small></div>' +
            '<div class="metric-tile"><span>开放异常</span><strong>' + fmt(summary.openAlerts.length) + '</strong><small>异常关闭率 ' + pct(summary.closeRate) + '</small></div>' +
          '</div></aside>' +
        '<article class="panel warehouse-panel">' + panelHeader('库区容量分布', '点击库区查看负责人、冻结与异常库位', '高负荷优先', '') +
          '<div class="warehouse-map-wrap">' + warehouseMap() + '</div></article>' +
        '<aside class="spatial-right">' +
          '<article class="panel">' + panelHeader('现场资源', '叉车状态与当前任务', '', '') + '<div class="panel-body">' + resourceRows() + '</div></article>' +
          '<article class="panel">' + panelHeader('异常事件流', '最近发生与未关闭事项', '', '') + '<div class="panel-body">' + timelineItems() + '</div></article>' +
        '</aside>' +
        '<article class="panel spatial-bottom">' + panelHeader('近 7 日吞吐节奏', '绿色入库 · 蓝色出库', '', '') + '<div class="panel-body">' + spatialTrend() + '</div></article>' +
      '</section>';
  }

  function render() {
    var summary = getSummary();
    var body = state.variant === 'B' ? renderVariantB(summary) : state.variant === 'C' ? renderVariantC(summary) : renderVariantA(summary);
    document.body.setAttribute('data-variant', state.variant);
    app.innerHTML = '<div class="app-frame">' + topbar() + '<main class="dashboard variant-' + state.variant.toLowerCase() + '">' + body + '</main></div>';
    bindEvents();
    updateClock();
  }

  function setVariant(next) {
    if (!VARIANTS[next]) return;
    state.variant = next;
    var params = new URLSearchParams(window.location.search);
    params.set('variant', next);
    window.history.replaceState({}, '', window.location.pathname + '?' + params.toString());
    closeOverlay();
    render();
  }

  function bindEvents() {
    app.querySelectorAll('[data-zone]').forEach(function (button) {
      button.addEventListener('click', function () { openZone(button.getAttribute('data-zone')); });
    });
    app.querySelectorAll('[data-alert-index]').forEach(function (button) {
      button.addEventListener('click', function () {
        var index = Number(button.getAttribute('data-alert-index'));
        var alert = selectedAlerts().filter(function (item) { return item.status !== '已关闭'; })[index];
        if (alert) openAlert(alert);
      });
    });
    app.querySelectorAll('[data-action="variant-c"]').forEach(function (button) {
      button.addEventListener('click', function () { setVariant('C'); });
    });
    app.querySelectorAll('[data-action="all-alerts"]').forEach(function (button) {
      button.addEventListener('click', function () {
        var first = selectedAlerts().filter(function (item) { return item.status !== '已关闭'; })[0];
        if (first) openAlert(first);
      });
    });
  }

  function openZone(code) {
    var zone = (DATA.zones || []).find(function (item) { return String(item.code) === String(code); });
    if (!zone) return;
    state.selectedZone = code;
    var tone = zoneTone(zone);
    var related = selectedAlerts().filter(function (item) { return item.zone === zone.name || item.zone === zone.code; }).slice(0, 4);
    var recommendation = tone === 'bad'
      ? '该库区已进入高负荷区间。建议暂停非紧急补货，优先释放待出库库位，并检查冻结与异常库位是否可在本班次完成解锁。'
      : tone === 'warn'
        ? '该库区接近预警线。建议将后续入库任务分流至低占用库区，并在班中复核一次可用库位。'
        : '当前容量状态稳定。继续观察入库峰值与叉车任务集中度，保持异常库位及时闭环。';
    overlayRoot.innerHTML = '<div class="drawer-backdrop" data-close-overlay></div><aside class="detail-drawer" role="dialog" aria-modal="true" aria-label="库区详情">' +
      '<div class="drawer-head"><div><p class="eyebrow">ZONE DETAIL / ' + escapeHtml(zone.code) + '</p><h3>' + escapeHtml(zone.name || zone.code) + '</h3></div><button class="drawer-close" type="button" data-close-overlay aria-label="关闭">×</button></div>' +
      '<div class="drawer-hero">' + radialGauge(zone.occupancy, '库位占用', tone, 116) + '<div><span class="status-pill ' + tone + '">' + escapeHtml(zone.status || '正常') + '</span><p class="panel-subtitle" style="margin-top:10px">负责人</p><strong>' + escapeHtml(zone.manager || '待配置') + '</strong></div></div>' +
      '<div class="drawer-grid">' +
        '<div class="drawer-stat"><span>总库位</span><strong>' + fmt(zone.capacity) + '</strong></div>' +
        '<div class="drawer-stat"><span>可用库位</span><strong>' + fmt(zone.available) + '</strong></div>' +
        '<div class="drawer-stat"><span>冻结库位</span><strong>' + fmt(zone.frozen) + '</strong></div>' +
        '<div class="drawer-stat"><span>异常库位</span><strong>' + fmt(zone.abnormal) + '</strong></div>' +
      '</div>' +
      '<section class="drawer-section"><h4>调度建议</h4><div class="recommendation">' + escapeHtml(recommendation) + '</div></section>' +
      '<section class="drawer-section"><h4>关联异常 · ' + related.length + '</h4>' +
        (related.length ? '<div class="alert-list">' + related.map(function (item) {
          return '<div class="alert-item"><span class="severity-dot ' + ((item.severity === '高' || item.severity === '严重') ? 'high' : '') + '"></span><span class="alert-main"><strong>' + escapeHtml(item.title || item.type) + '</strong><span>' + escapeHtml((item.owner || '待分派') + ' · ' + (item.status || '待处理')) + '</span></span><span class="alert-age">' + fmt1(item.durationHours || 0) + 'h</span></div>';
        }).join('') + '</div>' : '<div class="recommendation">当前筛选范围内没有关联异常。</div>') +
      '</section></aside>';
    bindOverlayEvents();
  }

  function openAlert(alert) {
    var severityTone = alert.severity === '紧急' || alert.severity === '高' || alert.severity === '严重' ? 'bad' : alert.severity === '重要' || alert.severity === '中' ? 'warn' : '';
    var suggestion = alert.recommendation || (
      severityTone === 'bad'
        ? '建议立即确认影响范围，指定责任人和预计恢复时间；若影响发货及时率，优先调整波次或库区资源。'
        : '建议在当前班次完成原因确认与责任分派，并在日看板中跟踪关闭时长。'
    );
    overlayRoot.innerHTML = '<div class="drawer-backdrop" data-close-overlay></div><aside class="detail-drawer" role="dialog" aria-modal="true" aria-label="异常详情">' +
      '<div class="drawer-head"><div><p class="eyebrow">EXCEPTION / ' + escapeHtml(alert.id || alert.type) + '</p><h3>' + escapeHtml(alert.title || alert.type) + '</h3></div><button class="drawer-close" type="button" data-close-overlay aria-label="关闭">×</button></div>' +
      '<div class="drawer-hero" style="grid-template-columns:1fr"><div><span class="status-pill ' + severityTone + '">' + escapeHtml((alert.severity || '中') + '级') + '</span><p style="margin:14px 0 0;color:var(--muted);font-size:11px;line-height:1.7">' + escapeHtml(alert.description || '该事件已进入仓库异常闭环队列。') + '</p></div></div>' +
      '<div class="drawer-grid">' +
        '<div class="drawer-stat"><span>发生库区</span><strong>' + escapeHtml(alert.zone || '全仓') + '</strong></div>' +
        '<div class="drawer-stat"><span>处理状态</span><strong>' + escapeHtml(alert.status || '待处理') + '</strong></div>' +
        '<div class="drawer-stat"><span>责任人</span><strong>' + escapeHtml(alert.owner || '待分派') + '</strong></div>' +
        '<div class="drawer-stat"><span>持续时长</span><strong>' + fmt1(alert.durationHours || 0) + ' h</strong></div>' +
      '</div>' +
      '<section class="drawer-section"><h4>处置建议</h4><div class="recommendation">' + escapeHtml(suggestion) + '</div></section>' +
      '<section class="drawer-section"><h4>事件信息</h4><div class="impact-list">' +
        '<div class="impact-item"><span>异常类型</span><strong>' + escapeHtml(alert.type || '-') + '</strong></div>' +
        '<div class="impact-item"><span>发生日期</span><strong>' + escapeHtml(alert.date || '-') + '</strong></div>' +
        '<div class="impact-item"><span>关闭时间</span><strong>' + escapeHtml(alert.closedAt || '未关闭') + '</strong></div>' +
      '</div></section></aside>';
    bindOverlayEvents();
  }

  function bindOverlayEvents() {
    overlayRoot.querySelectorAll('[data-close-overlay]').forEach(function (node) {
      node.addEventListener('click', closeOverlay);
    });
    var close = overlayRoot.querySelector('.drawer-close');
    if (close) close.focus();
  }

  function closeOverlay() {
    overlayRoot.innerHTML = '';
    state.selectedZone = null;
  }

  function updateClock() {
    var now = new Date();
    var timeNode = document.getElementById('clock-time');
    var dateNode = document.getElementById('clock-date');
    if (timeNode) timeNode.textContent = now.toLocaleTimeString('zh-CN', { hour12: false });
    if (dateNode) dateNode.textContent = now.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', weekday: 'short' });
  }

  document.addEventListener('keydown', function (event) {
    var target = event.target;
    var isEditing = target && (target.matches('input, textarea, select, [contenteditable="true"]'));
    if (event.key === 'Escape' && overlayRoot.innerHTML) {
      closeOverlay();
      return;
    }
    if (isEditing || overlayRoot.innerHTML) return;
  });

  render();
  window.setInterval(updateClock, 1000);
})();

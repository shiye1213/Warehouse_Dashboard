(function renderBoxWarehouseDatabaseData() {
  const format = (value, digits = 0) => Number(value || 0).toLocaleString('zh-CN', { maximumFractionDigits: digits });
  const percent = (value, digits = 1) => (Number(value || 0) * 100).toFixed(digits) + '%';
  const sum = (rows, key) => rows.reduce((total, row) => total + Number(row[key] || 0), 0);
  const average = (rows, key) => rows.length ? sum(rows, key) / rows.length : 0;
  const relative = (current, previous) => Number(previous || 0) ? (Number(current || 0) - Number(previous)) / Number(previous) : 0;
  const escapeHtml = (value) => String(value == null ? '' : value).replace(/[&<>"']/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]));
  const setText = (selector, value) => { const node = document.querySelector(selector); if (node) node.textContent = value; };
  const setHtml = (selector, value) => { const node = document.querySelector(selector); if (node) node.innerHTML = value; };
  const setMain = (selector, value) => {
    const node = document.querySelector(selector);
    if (!node) return;
    const text = Array.from(node.childNodes).find((item) => item.nodeType === Node.TEXT_NODE && item.nodeValue.trim());
    if (text) text.nodeValue = value;
    else node.prepend(document.createTextNode(value));
  };
  const setMetric = (selector, value, note, delta) => {
    setMain(selector + ' strong', value);
    setText(selector + ' small', note);
    setText(selector + ' em', delta);
  };
  const formatDelta = (value) => {
    const amount = Number(value || 0) * 100;
    return (amount >= 0 ? '↑ ' : '↓ ') + Math.abs(amount).toFixed(1) + '%';
  };
  const chartPoints = (rows, key, base = 120, scaleMax) => {
    const values = rows.map((row) => Math.max(0, Number(row[key] || 0)));
    const max = scaleMax || Math.max.apply(null, values.concat([1])) * 1.08;
    return values.map((value, index) => {
      const x = 18 + (values.length < 2 ? 0 : 372 * index / (values.length - 1));
      const y = base - value / max * (base - 18);
      return x.toFixed(1) + ',' + y.toFixed(1);
    }).join(' ');
  };
  const updateAxis = (chart, rows, keys, base, valueFormatter) => {
    if (!chart || !rows.length) return;
    const texts = Array.from(chart.querySelectorAll('.axis text'));
    const yTexts = texts.filter((node) => node.getAttribute('text-anchor') === 'end');
    const xTexts = texts.filter((node) => node.getAttribute('text-anchor') !== 'end');
    const values = [];
    rows.forEach((row) => keys.forEach((key) => values.push(Number(row[key] || 0))));
    const max = Math.max.apply(null, values.concat([1]));
    yTexts.forEach((node, index) => {
      const ratio = yTexts.length <= 1 ? 0 : 1 - index / (yTexts.length - 1);
      node.textContent = valueFormatter(max * ratio);
    });
    xTexts.forEach((node, index) => {
      const rowIndex = Math.round(index * (rows.length - 1) / Math.max(xTexts.length - 1, 1));
      node.textContent = String(rows[rowIndex]?.date || '').slice(5);
    });
  };
  const setChart = (chart, rows, firstKey, secondKey, base = 120, valueFormatter = (value) => format(value)) => {
    if (!chart || !rows.length) return;
    const scaleValues = [];
    rows.forEach((row) => {
      scaleValues.push(Number(row[firstKey] || 0));
      if (secondKey) scaleValues.push(Number(row[secondKey] || 0));
    });
    const scaleMax = Math.max.apply(null, scaleValues.concat([1])) * 1.08;
    const first = chartPoints(rows, firstKey, base, scaleMax);
    const second = secondKey ? chartPoints(rows, secondKey, base, scaleMax) : '';
    const firstLine = chart.querySelector('.cyan-line, .mint-line');
    const secondLine = chart.querySelector('.purple-line');
    firstLine?.setAttribute('points', first);
    if (firstLine) firstLine.dataset.values = JSON.stringify(rows.map((row) => Number(row[firstKey] || 0)));
    if (second && secondLine) {
      secondLine.setAttribute('points', second);
      secondLine.dataset.values = JSON.stringify(rows.map((row) => Number(row[secondKey] || 0)));
    }
    const area = chart.querySelector('.area, .monthly-area, path[fill^="url"]');
    if (area) area.setAttribute('d', 'M' + first.replaceAll(' ', 'L') + 'V' + base + 'H18Z');
    updateAxis(chart, rows, secondKey ? [firstKey, secondKey] : [firstKey], base, valueFormatter);
    delete chart.dataset.threeDimensional;
    chart.querySelectorAll('.line-3d-geometry, .line-point').forEach((node) => node.remove());
  };
  const polarPoint = (angle, radius) => {
    const radians = (angle - 90) * Math.PI / 180;
    return { x: 100 + radius * Math.cos(radians), y: 100 + radius * Math.sin(radians) };
  };
  const donutPath = (startRatio, endRatio) => {
    const span = Math.max(0, endRatio - startRatio);
    if (!span) return '';
    const startAngle = startRatio * 360;
    const endAngle = Math.min(endRatio, 0.999999) * 360;
    const outerStart = polarPoint(startAngle, 72);
    const outerEnd = polarPoint(endAngle, 72);
    const innerEnd = polarPoint(endAngle, 42);
    const innerStart = polarPoint(startAngle, 42);
    const largeArc = span > 0.5 ? 1 : 0;
    return 'M' + outerStart.x.toFixed(3) + ' ' + outerStart.y.toFixed(3)
      + ' A72 72 0 ' + largeArc + ' 1 ' + outerEnd.x.toFixed(3) + ' ' + outerEnd.y.toFixed(3)
      + ' L' + innerEnd.x.toFixed(3) + ' ' + innerEnd.y.toFixed(3)
      + ' A42 42 0 ' + largeArc + ' 0 ' + innerStart.x.toFixed(3) + ' ' + innerStart.y.toFixed(3) + ' Z';
  };
  const updateRingGeometry = (ratio) => {
    const bounded = Math.max(0, Math.min(Number(ratio || 0), 1));
    const occupiedPath = donutPath(0, bounded);
    const freePath = donutPath(bounded, 1);
    document.querySelectorAll('.ring-face-occupied, .ring-depth-occupied').forEach((node) => node.setAttribute('d', occupiedPath));
    document.querySelectorAll('.ring-face-free, .ring-depth-free').forEach((node) => node.setAttribute('d', freePath));
  };
  const stockHistory = (daily, current) => {
    let running = current;
    const rows = new Array(daily.length);
    for (let index = daily.length - 1; index >= 0; index -= 1) {
      rows[index] = { date: daily[index].date, stock: Math.max(0, running) };
      running -= Number(daily[index].packagingInbound || 0) - Number(daily[index].packagingOutbound || 0);
    }
    return rows;
  };
  const targetValue = (data, key, fallback) => Number((data.targets || []).find((item) => item.key === key)?.target ?? fallback);
  const renderOperations = (operations, data) => {
    const targetReceipt = targetValue(data, 'receivingTimely', 0);
    const targetDelivery = targetValue(data, 'deliveryTimely', 0);
    const groups = [
      {
        rows: operations.filter((row) => Number(row.inboundQty || 0) > 0).sort((a, b) => Number(b.inboundQty) - Number(a.inboundQty)).slice(0, 4),
        qty: 'inboundQty', orders: 'inboundOrders', timely: 'receiptTimely', target: targetReceipt,
      },
      {
        rows: operations.filter((row) => Number(row.outboundQty || 0) > 0).sort((a, b) => Number(b.outboundQty) - Number(a.outboundQty)).slice(0, 4),
        qty: 'outboundQty', orders: 'outboundOrders', timely: 'deliveryTimely', target: targetDelivery,
      },
    ];
    document.querySelectorAll('.task-pane').forEach((pane, paneIndex) => {
      const group = groups[paneIndex];
      const header = '<tr><th>项目号</th><th>物料</th><th>数量</th><th>单据</th><th>及时率</th></tr>';
      const rows = group.rows.map((row) => {
        const rate = Number(row[group.timely] || 0);
        const tone = rate >= group.target ? 'done' : 'waiting';
        return '<tr><td>' + escapeHtml(row.projectNo) + '</td><td>' + escapeHtml(row.materialName) + '</td><td>' + format(row[group.qty]) + '</td><td>' + format(row[group.orders]) + '</td><td><span class="state ' + tone + '">' + percent(rate) + '</span></td></tr>';
      }).join('');
      pane.querySelectorAll('thead').forEach((node) => { node.innerHTML = header; });
      pane.querySelectorAll('tbody').forEach((node) => { node.innerHTML = rows || '<tr><td colspan="5">最新业务日无作业记录</td></tr>'; });
      setText('.task-pane:nth-child(' + (paneIndex + 1) + ') .task-title em', format(sum(group.rows, group.orders)) + ' 单');
    });
  };
  const renderAlerts = (alerts) => {
    const open = alerts.filter((row) => row.status !== '已关闭');
    setText('.order-card .pill', open.length + ' 条未关闭');
    const track = document.querySelector('.order-roller-track');
    if (!track) return;
    track.innerHTML = alerts.slice(0, 6).map((row, index) => {
      const tone = row.status === '已关闭' ? 'done' : row.status === '处理中' ? 'doing' : 'waiting';
      return '<div class="order-row ' + (index % 2 ? '' : 'order-row--active') + '" role="listitem"><span class="order-row__code">' + escapeHtml(row.id) + '</span><span class="order-row__item">' + escapeHtml(row.type) + '<small>' + escapeHtml(row.area || row.zone || row.project || '未分区') + '</small></span><span class="state ' + tone + '">' + escapeHtml(row.status) + '</span><time>' + format(row.durationMinutes || row.responseMinutes) + 'm</time></div>';
    }).join('') || '<div class="order-row"><span class="order-row__item">当前无异常记录</span></div>';
  };
  const clearExampleValues = () => {
    document.querySelectorAll('.metric-box strong, .hero-kpi strong, .stock-number strong, .mini-stat b, .age-bars b, .ops-summary b').forEach((node) => {
      const small = node.querySelector('small');
      node.textContent = '—';
      if (small) node.appendChild(small);
    });
    document.querySelectorAll('.task-pane tbody').forEach((node) => { node.innerHTML = '<tr><td colspan="5">正在读取数据库…</td></tr>'; });
    setText('.order-roller-track', '正在读取数据库…');
    setText('.top-status .online', '● 正在连接数据库');
  };
  const render = (data) => {
    const daily = data.daily || [];
    const latest = daily.at(-1) || {};
    const previous = daily.at(-2) || {};
    const zones = data.zones || [];
    const stocks = data.stocks || [];
    const alerts = data.alerts || [];
    const operations = data.skuOperations || [];
    const capacity = sum(zones, 'capacity');
    const occupied = sum(zones, 'occupied');
    const available = sum(zones, 'available');
    const onHand = sum(stocks, 'onHand');
    const frozen = sum(stocks, 'frozen');
    const totalInbound = sum(daily, 'packagingInbound');
    const totalOutbound = sum(daily, 'packagingOutbound');
    const inboundOrders = sum(daily, 'inboundOrders');
    const outboundOrders = sum(daily, 'outboundOrders');
    const half = Math.max(1, Math.floor(daily.length / 2));
    const firstHalf = daily.slice(0, half);
    const secondHalf = daily.slice(half);
    const inboundDelta = relative(sum(secondHalf, 'packagingInbound'), sum(firstHalf, 'packagingInbound'));
    const outboundDelta = relative(sum(secondHalf, 'packagingOutbound'), sum(firstHalf, 'packagingOutbound'));
    const netChange = totalInbound - totalOutbound;
    const outerStocks = stocks.filter((row) => String(row.name || '').includes('外箱'));
    const innerStocks = stocks.filter((row) => String(row.name || '').includes('内盒'));
    const outerQty = sum(outerStocks, 'onHand');
    const innerQty = sum(innerStocks, 'onHand');
    const inventoryTotal = Math.max(outerQty + innerQty, 1);
    const coverageDays = average(daily, 'packagingOutbound') ? onHand / average(daily, 'packagingOutbound') : 0;
    setText('.left-column .flow-card .panel-head small', '近 ' + daily.length + ' 天 · 数据库作业量');
    setText('.left-column .flow-card .pill', netChange >= 0 ? '净入库' : '净出库');
    setMetric('.left-column .metric-box:nth-child(1)', format(totalInbound), format(inboundOrders) + ' 单', formatDelta(inboundDelta));
    setMetric('.left-column .metric-box:nth-child(2)', format(totalOutbound), format(outboundOrders) + ' 单', formatDelta(outboundDelta));
    setHtml('.left-column .stock-card .mini-stat', '净变化 <b>' + (netChange >= 0 ? '+' : '') + format(netChange) + '</b>');
    setMain('.left-column .stock-number strong', format(onHand));
    setText('.analysis-card .pill', percent(capacity ? occupied / capacity : 0));
    setText('.ring-label b', percent(capacity ? occupied / capacity : 0));
    setHtml('.ring-wrap p', '<i></i>已用 ' + format(occupied) + ' <i class="free"></i>可用 ' + format(available));
    const ring = document.querySelector('.ring-3d');
    if (ring) {
      ring.dataset.occupied = String(occupied);
      ring.dataset.available = String(available);
      ring.dataset.occupancy = String(capacity ? occupied / capacity : 0);
      ring.setAttribute('aria-label', '库位占用 ' + percent(capacity ? occupied / capacity : 0));
      updateRingGeometry(capacity ? occupied / capacity : 0);
    }
    setHtml('.age-bars > div:nth-child(1) b', format(outerQty) + '<em>' + percent(outerQty / inventoryTotal) + '</em>');
    setHtml('.age-bars > div:nth-child(3) b', format(innerQty) + '<em>' + percent(innerQty / inventoryTotal) + '</em>');
    const outerBar = document.querySelector('.age-bars .hbar i');
    const innerBar = document.querySelector('.age-bars .purplebar i');
    if (outerBar) outerBar.style.width = percent(outerQty / inventoryTotal);
    if (innerBar) innerBar.style.width = percent(innerQty / inventoryTotal);
    setText('.age-note b', format(frozen) + ' 个');
    const heroValues = [latest.packagingInbound, latest.packagingOutbound, onHand, capacity, occupied, latest.exceptions];
    document.querySelectorAll('.hero-kpis > div strong').forEach((node, index) => setMain('.hero-kpis > div:nth-child(' + (index + 1) + ') strong', format(heroValues[index])));
    setText('.hero-kpis > div:nth-child(1) em', '较昨日 ' + formatDelta(relative(latest.packagingInbound, previous.packagingInbound)));
    setText('.hero-kpis > div:nth-child(2) em', '较昨日 ' + formatDelta(relative(latest.packagingOutbound, previous.packagingOutbound)));
    setText('.hero-kpis > div:nth-child(3) em', '可用 ' + format(sum(stocks, 'available')));
    setText('.hero-kpis > div:nth-child(5) em', '占用率 ' + percent(capacity ? occupied / capacity : 0));
    setText('.hero-kpis > div:nth-child(6) em', '未关闭 ' + format(alerts.filter((row) => row.status !== '已关闭').length) + ' 条');
    setText('.operation-card .ops-summary span:nth-child(1) b', format(latest.pickingTasks || latest.picking));
    setText('.operation-card .ops-summary span:nth-child(2) b', format(latest.forkliftTasks));
    setText('.operation-card .ops-summary span:nth-child(3) b', percent(latest.deliveryTimely, 2));
    setText('.hero-badges span:nth-child(1)', zones.length + ' 个库区');
    setText('.hero-badges span:nth-child(2)', stocks.length + ' 种物料');
    setText('.hero-badges span:nth-child(3)', '数据库实时');
    setText('.right-column .flow-card .panel-head small', '近 ' + daily.length + ' 天 · 质量指标');
    setText('.right-column .flow-card h2', '质量与履约趋势');
    setText('.right-column .flow-card .pill', '数据库实测');
    setText('.right-column .flow-card .metric-box:nth-child(1) span', '库存准确率');
    setText('.right-column .flow-card .metric-box:nth-child(2) span', '出库及时率');
    setMetric('.right-column .metric-box:nth-child(1)', percent(average(daily, 'inventoryAccuracy')), '期间均值', '目标 ' + percent(targetValue(data, 'inventoryAccuracy', 0)));
    setMetric('.right-column .metric-box:nth-child(2)', percent(average(daily, 'deliveryTimely')), '期间均值', '目标 ' + percent(targetValue(data, 'deliveryTimely', 0)));
    setText('.right-column .flow-card .chart-title b', '每日质量走势');
    setHtml('.right-column .flow-card .chart-title span', '<i class="cyan"></i>库存准确 <i class="purple"></i>出库及时');
    setText('.right-column .stock-card .panel-head small', '近 ' + daily.length + ' 天 · 日末库存');
    setText('.right-column .stock-card h2', '库存与覆盖天数');
    setHtml('.right-column .stock-card .mini-stat', '覆盖 <b>' + format(coverageDays, 1) + '<small>天</small></b>');
    setText('.right-column .stock-number span', '最新库存');
    setMain('.right-column .stock-number strong', format(onHand));
    const charts = document.querySelectorAll('svg.chart');
    const history = stockHistory(daily, onHand);
    setChart(charts[0], daily, 'packagingInbound', 'packagingOutbound');
    setChart(charts[1], history, 'stock', null, 110);
    setChart(charts[2], daily, 'inventoryAccuracy', 'deliveryTimely', 120, (value) => percent(value, 0));
    setChart(charts[3], history, 'stock', null, 110);
    renderOperations(operations, data);
    renderAlerts(alerts);
    setText('.top-status .online', '● 数据库已连接');
    setText('.top-status small', '负责人 · ' + ((data.meta?.owners || []).join('、') || '未配置'));
    const clock = document.getElementById('clock');
    if (clock && data.meta?.latestDate) {
      clock.dataset.bizDate = data.meta.latestDate;
      clock.textContent = data.meta.latestDate + ' 23:59:59';
    }
    document.body.dataset.dataSource = 'mysql';
    window.dispatchEvent(new CustomEvent('box-warehouse:data-loaded'));
  };
  const clearCharts = () => {
    document.querySelectorAll('svg.chart polyline.line').forEach((node) => node.setAttribute('points', ''));
    document.querySelectorAll('svg.chart .area, svg.chart .monthly-area, svg.chart path[fill^="url"]').forEach((node) => node.setAttribute('d', ''));
  };
  const load = async () => {
    try {
      const response = await fetch('/api/dashboard/warehouses/WH-PK04?range=31');
      if (!response.ok) throw new Error('HTTP ' + response.status);
      render(await response.json());
    } catch (error) {
      clearCharts();
      setText('.top-status .online', '● 数据库连接失败');
      setText('.top-status small', '暂无可展示的业务数据');
      document.querySelectorAll('.task-pane tbody').forEach((node) => { node.innerHTML = '<tr><td colspan="5">数据库数据加载失败</td></tr>'; });
      setText('.order-roller-track', '数据库数据加载失败');
      console.error('加载箱盒库数据失败', error);
    }
  };
  clearExampleValues();
  load();
  window.setInterval(load, 30000);
})();
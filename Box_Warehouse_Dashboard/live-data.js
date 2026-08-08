(function renderBoxWarehouseDatabaseData() {
  const format = (value, digits = 0) => Number(value || 0).toLocaleString('zh-CN', { maximumFractionDigits: digits });
  const sum = (rows, key) => rows.reduce((total, row) => total + Number(row[key] || 0), 0);
  const escapeHtml = (value) => String(value ?? '').replace(/[&<>"']/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]));
  const setText = (selector, value) => { const node = document.querySelector(selector); if (node) node.textContent = value; };
  const setMain = (selector, value) => {
    const node = document.querySelector(selector);
    if (!node) return;
    const text = [...node.childNodes].find((item) => item.nodeType === Node.TEXT_NODE && item.nodeValue.trim());
    if (text) text.nodeValue = value;
    else node.prepend(document.createTextNode(value));
  };
  const chartPoints = (rows, key, base = 120) => {
    const values = rows.map((row) => Math.max(0, Number(row[key] || 0)));
    const max = Math.max(...values, 1) * 1.08;
    return values.map((value, index) => `${(18 + (values.length < 2 ? 0 : 372 * index / (values.length - 1))).toFixed(1)},${(base - value / max * (base - 18)).toFixed(1)}`).join(' ');
  };
  const setChart = (chart, rows, firstKey, secondKey, base = 120) => {
    if (!chart || !rows.length) return;
    const first = chartPoints(rows, firstKey, base);
    const second = secondKey ? chartPoints(rows, secondKey, base) : '';
    const firstLine = chart.querySelector('.cyan-line, .mint-line');
    const secondLine = chart.querySelector('.purple-line');
    firstLine?.setAttribute('points', first);
    if (firstLine) firstLine.dataset.values = JSON.stringify(rows.map((row) => Number(row[firstKey] || 0)));
    if (second && secondLine) {
      secondLine.setAttribute('points', second);
      secondLine.dataset.values = JSON.stringify(rows.map((row) => Number(row[secondKey] || 0)));
    }
    const area = chart.querySelector('.area, .monthly-area, path[fill^="url"]');
    if (area) area.setAttribute('d', `M${first.replaceAll(' ', 'L')}V${base}H18Z`);
    delete chart.dataset.threeDimensional;
    chart.querySelectorAll('.line-3d-geometry, .line-point').forEach((node) => node.remove());
  };
  const stockHistory = (daily, current) => {
    let running = current;
    const rows = new Array(daily.length);
    for (let index = daily.length - 1; index >= 0; index -= 1) {
      rows[index] = { stock: Math.max(0, running) };
      running -= Number(daily[index].packagingInbound || 0) - Number(daily[index].packagingOutbound || 0);
    }
    return rows;
  };
  const renderInventory = (stocks) => {
    const panes = document.querySelectorAll('.task-pane');
    panes.forEach((pane, paneIndex) => {
      const rows = stocks.filter((_, index) => index % 2 === paneIndex).slice(0, 4);
      const header = '<tr><th>\u7269\u6599\u7f16\u7801</th><th>\u7269\u6599</th><th>\u73b0\u5b58\u91cf</th><th>\u53ef\u7528\u91cf</th><th>\u72b6\u6001</th></tr>';
      const rowHtml = rows.map((row) => {
        const available = Number(row.onHand || 0) - Number(row.reserved || 0) - Number(row.frozen || 0);
        const frozen = Number(row.frozen || 0) > 0;
        return `<tr><td>${escapeHtml(row.code)}</td><td>${escapeHtml(row.name)}</td><td>${format(row.onHand)}</td><td>${format(available)}</td><td><span class="state ${frozen ? 'waiting' : 'done'}">${frozen ? '\u51bb\u7ed3' : '\u6b63\u5e38'}</span></td></tr>`;
      }).join('');
      pane.querySelectorAll('thead').forEach((node) => { node.innerHTML = header; });
      pane.querySelectorAll('tbody').forEach((node) => { node.innerHTML = rowHtml; });
      setText(`.task-pane:nth-child(${paneIndex + 1}) .task-title em`, `${rows.length} \u9879`);
    });
  };
  const renderAlerts = (alerts) => {
    const open = alerts.filter((row) => row.status !== '\u5df2\u5173\u95ed');
    setText('.order-card .pill', `${open.length} \u6761\u5f85\u5904\u7406`);
    const track = document.querySelector('.order-roller-track');
    if (!track) return;
    track.innerHTML = alerts.slice(0, 6).map((row, index) => {
      const tone = row.status === '\u5df2\u5173\u95ed' ? 'done' : row.status === '\u5904\u7406\u4e2d' ? 'doing' : 'waiting';
      return `<div class="order-row ${index % 2 ? '' : 'order-row--active'}" role="listitem"><span class="order-row__code">${escapeHtml(row.id)}</span><span class="order-row__item">${escapeHtml(row.type)}<small>${escapeHtml(row.area || row.zone || row.project || '\u672a\u5206\u533a')}</small></span><span class="state ${tone}">${escapeHtml(row.status)}</span><time>${format(row.durationMinutes || row.responseMinutes)}m</time></div>`;
    }).join('') || '<div class="order-row"><span class="order-row__item">\u5f53\u524d\u65e0\u5f85\u5904\u7406\u5f02\u5e38</span></div>';
  };
  const render = (data) => {
    const daily = data.daily || [];
    const latest = daily.at(-1) || {};
    const zones = data.zones || [];
    const stocks = data.stocks || [];
    const alerts = data.alerts || [];
    const capacity = sum(zones, 'capacity');
    const occupied = sum(zones, 'occupied');
    const available = sum(zones, 'available');
    const onHand = sum(stocks, 'onHand');
    const frozen = sum(stocks, 'frozen');
    setMain('.hero-kpis > div:nth-child(1) strong', format(latest.packagingInbound));
    setMain('.hero-kpis > div:nth-child(2) strong', format(latest.packagingOutbound));
    setMain('.hero-kpis > div:nth-child(3) strong', format(onHand));
    setMain('.hero-kpis > div:nth-child(4) strong', format(capacity));
    setMain('.hero-kpis > div:nth-child(5) strong', format(occupied));
    setMain('.hero-kpis > div:nth-child(6) strong', format(latest.exceptions));
    setMain('.left-column .metric-box:nth-child(1) strong', format(sum(daily, 'packagingInbound')));
    setMain('.left-column .metric-box:nth-child(2) strong', format(sum(daily, 'packagingOutbound')));
    setMain('.right-column .metric-box:nth-child(1) strong', format(sum(daily, 'packagingInbound')));
    setMain('.right-column .metric-box:nth-child(2) strong', format(sum(daily, 'packagingOutbound')));
    document.querySelectorAll('.stock-number strong').forEach((node) => { const text = [...node.childNodes].find((item) => item.nodeType === Node.TEXT_NODE); if (text) text.nodeValue = format(onHand); });
    setText('.ring-label b', `${(capacity ? occupied / capacity * 100 : 0).toFixed(1)}%`);
    const ringNote = document.querySelector('.ring-wrap p');
    if (ringNote) ringNote.innerHTML = `<i></i>\u5df2\u7528 ${format(occupied)} <i class="free"></i>\u53ef\u7528 ${format(available)}`;
    const ring = document.querySelector('.ring-3d');
    if (ring) {
      ring.dataset.occupied = String(occupied);
      ring.dataset.available = String(available);
      ring.dataset.occupancy = String(capacity ? occupied / capacity : 0);
    }
    setText('.age-note b', `${format(frozen)} \u4ef6`);
    setText('.operation-card .ops-summary span:nth-child(1) b', format(latest.pickingTasks || latest.picking));
    setText('.operation-card .ops-summary span:nth-child(2) b', format(latest.forkliftTasks));
    setText('.operation-card .ops-summary span:nth-child(3) b', `${(Number(latest.deliveryTimely || 0) * 100).toFixed(2)}%`);
    setText('.hero-badges span:nth-child(1)', `${zones.length} \u4e2a\u5e93\u533a`);
    setText('.hero-badges span:nth-child(2)', `${stocks.length} \u79cd\u7269\u6599`);
    const charts = document.querySelectorAll('svg.chart');
    const history = stockHistory(daily, onHand);
    setChart(charts[0], daily, 'packagingInbound', 'packagingOutbound');
    setChart(charts[1], history, 'stock', null, 110);
    setChart(charts[2], daily, 'packagingInbound', 'packagingOutbound');
    setChart(charts[3], history, 'stock', null, 110);
    renderInventory(stocks);
    renderAlerts(alerts);
    setText('.top-status .online', '\u25cf \u6570\u636e\u5e93\u5df2\u8fde\u63a5');
    setText('.top-status small', `\u8d23\u4efb\u4eba \u00b7 ${data.meta?.owners?.join('\u3001') || '\u672a\u914d\u7f6e'}`);
    const clock = document.getElementById('clock');
    if (clock && data.meta?.latestDate) clock.dataset.bizDate = data.meta.latestDate;
    window.dispatchEvent(new CustomEvent('box-warehouse:data-loaded'));
  };
  const load = async () => {
    try {
      const response = await fetch('/api/dashboard/warehouses/WH-PK04?range=31');
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      render(await response.json());
    } catch (error) {
      setText('.top-status .online', '\u25cf \u6570\u636e\u5e93\u8fde\u63a5\u5931\u8d25');
      console.error('\u52a0\u8f7d\u7bb1\u76d2\u5e93\u6570\u636e\u5931\u8d25', error);
    }
  };
  load();
  window.setInterval(load, 30000);
})();


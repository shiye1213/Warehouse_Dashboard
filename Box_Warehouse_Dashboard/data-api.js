(async function connectBoxWarehouseDashboard() {
  const format = (value, digits = 0) => Number(value || 0).toLocaleString('zh-CN', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  });
  const setMainValue = (selector, value) => {
    const element = document.querySelector(selector);
    if (!element) return;
    const textNode = Array.from(element.childNodes).find((node) => node.nodeType === Node.TEXT_NODE && node.nodeValue.trim());
    if (textNode) textNode.nodeValue = value;
    else element.prepend(document.createTextNode(value));
  };
  try {
    const response = await fetch('/api/dashboard/warehouses/WH-PK04?range=31');
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const data = await response.json();
    const daily = data.daily || [];
    const latest = daily.at(-1) || {};
    const summary = data.summary || {};
    const zones = data.zones || [];
    const stocks = data.stocks || [];
    const capacity = zones.reduce((sum, row) => sum + Number(row.capacity || 0), 0);
    const occupied = zones.reduce((sum, row) => sum + Number(row.occupied || 0), 0);
    const available = zones.reduce((sum, row) => sum + Number(row.available || 0), 0);
    const onHand = stocks.reduce((sum, row) => sum + Number(row.onHand || 0), 0);
    const monthInbound = daily.reduce((sum, row) => sum + Number(row.packagingInbound || 0), 0);
    const monthOutbound = daily.reduce((sum, row) => sum + Number(row.packagingOutbound || 0), 0);

    setMainValue('.hero-kpis > div:nth-child(1) strong', format(latest.packagingInbound));
    setMainValue('.hero-kpis > div:nth-child(2) strong', format(latest.packagingOutbound));
    setMainValue('.hero-kpis > div:nth-child(3) strong', format(onHand));
    setMainValue('.hero-kpis > div:nth-child(4) strong', format(capacity));
    setMainValue('.hero-kpis > div:nth-child(5) strong', format(occupied));
    setMainValue('.hero-kpis > div:nth-child(6) strong', format(latest.exceptions));
    setMainValue('.left-column .metric-box:nth-child(1) strong', format(monthInbound));
    setMainValue('.left-column .metric-box:nth-child(2) strong', format(monthOutbound));
    setMainValue('.left-column .stock-number strong', format(onHand));
    const ringLabel = document.querySelector('.ring-label b');
    if (ringLabel) ringLabel.textContent = `${(Number(summary.occupancy || 0) * 100).toFixed(1)}%`;
    const ringNote = document.querySelector('.ring-wrap p');
    if (ringNote) ringNote.innerHTML = `<i></i>已用 ${format(occupied)} <i class="free"></i>可用 ${format(available)}`;
    const clock = document.getElementById('clock');
    if (clock && data.meta?.latestDate) clock.dataset.bizDate = data.meta.latestDate;
  } catch (error) {
    console.warn('箱盒库后端数据加载失败，继续展示内置快照。', error);
  }
})();

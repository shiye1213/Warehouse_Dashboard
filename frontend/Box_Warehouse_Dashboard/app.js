const daily = [4253,2342,4063,2310,2696,4023,4453,3441,2453,3046,3022,1848,2847,3064,2415,3966,1973,2190,2841,2886,3674,3543,4227,4873,3434,2663,5192,2488,4254,2260,4095];
const outbound = [3683,2093,3553,2031,2261,3806,3816,3132,2298,3011,2528,1673,2655,2693,2060,3438,1781,1866,2355,2576,3179,3209,3842,4470,2830,2308,4200,2486,3632,2156,3904];
const exceptionData = [
  { id: 'EX2607310', type: '库位异常', area: '箱盒主库', level: '一般', status: '处理中', owner: '孙工', response: '48m' },
  { id: 'EX2607306', type: 'PDA扫码失败', area: '箱盒待检区', level: '重要', status: '未处理', owner: '孙工', response: '36m' },
  { id: 'EX2607294', type: '出库延迟', area: '箱盒主库', level: '一般', status: '已关闭', owner: '孙工', response: '71m' },
  { id: 'EX2607288', type: '库位异常', area: '箱盒待检区', level: '一般', status: '已关闭', owner: '孙工', response: '54m' },
  { id: 'EX2607271', type: 'PDA扫码失败', area: '箱盒主库', level: '重要', status: '已关闭', owner: '孙工', response: '42m' },
  { id: 'EX2607258', type: '入库超时', area: '箱盒待检区', level: '一般', status: '处理中', owner: '孙工', response: '88m' },
];

const $ = (selector) => document.querySelector(selector);
const formatNumber = (value) => value.toLocaleString('en-US');

function renderFlowChart() {
  const host = $('#flowChart');
  const width = 760;
  const height = 202;
  const pad = { top: 16, right: 12, bottom: 12, left: 12 };
  const max = Math.max(...daily, ...outbound) * 1.08;
  const x = (index) => pad.left + index * ((width - pad.left - pad.right) / (daily.length - 1));
  const y = (value) => height - pad.bottom - (value / max) * (height - pad.top - pad.bottom);
  const path = (values) => values.map((value, index) => `${index ? 'L' : 'M'} ${x(index).toFixed(1)} ${y(value).toFixed(1)}`).join(' ');
  const area = `${path(daily)} L ${x(daily.length - 1)} ${height - pad.bottom} L ${x(0)} ${height - pad.bottom} Z`;
  const grid = [0.25, 0.5, 0.75, 1].map((ratio) => `<line class="chart-grid-line" x1="${pad.left}" y1="${y(max * ratio)}" x2="${width - pad.right}" y2="${y(max * ratio)}" />`).join('');
  const points = daily.filter((_, index) => index % 5 === 0 || index === daily.length - 1).map((value, index) => { const sourceIndex = index === Math.floor((daily.length - 1) / 5) ? daily.length - 1 : index * 5; return `<circle class="flow-point" cx="${x(sourceIndex)}" cy="${y(daily[sourceIndex])}" r="3.1" />`; }).join('');
  host.innerHTML = `<svg viewBox="0 0 ${width} ${height}" role="img" aria-label="入库和出库折线图"><defs><linearGradient id="flowArea" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#54d8ff" stop-opacity=".26"/><stop offset="1" stop-color="#54d8ff" stop-opacity="0"/></linearGradient></defs>${grid}<path class="flow-area" d="${area}"/><path class="flow-line-in" d="${path(daily)}"/><path class="flow-line-out" d="${path(outbound)}"/>${points}</svg>`;
}

function renderExceptions(filter = 'all') {
  const rows = $('#exceptionRows');
  const visible = filter === 'open' ? exceptionData.filter((item) => item.status !== '已关闭') : exceptionData;
  const statusClass = { 处理中: 'processing', 未处理: 'pending', 已关闭: 'closed' };
  rows.innerHTML = visible.map((item) => `<tr><td class="event-name"><strong>${item.type}</strong><small>${item.id} · ${item.area}</small></td><td><span class="level ${item.level === '重要' ? 'important' : 'normal'}">${item.level}</span></td><td><span class="state ${statusClass[item.status]}">${item.status}</span></td><td>${item.owner}</td><td class="response">${item.response}</td></tr>`).join('');
}

document.querySelectorAll('.segmented button').forEach((button) => button.addEventListener('click', () => {
  document.querySelectorAll('.segmented button').forEach((item) => item.classList.remove('is-active'));
  button.classList.add('is-active');
  const label = button.dataset.mode === 'month' ? '月视图 · 2026-07' : '日视图 · 2026-07-31';
  $('.view-context .muted:last-child').textContent = label;
}));

document.querySelectorAll('.table-filters button').forEach((button) => button.addEventListener('click', () => {
  document.querySelectorAll('.table-filters button').forEach((item) => item.classList.remove('is-active'));
  button.classList.add('is-active');
  renderExceptions(button.dataset.filter);
}));

$('#refreshButton').addEventListener('click', (event) => {
  const button = event.currentTarget;
  button.classList.add('is-loading');
  button.lastChild.textContent = '同步中';
  setTimeout(() => { button.classList.remove('is-loading'); button.lastChild.textContent = '刷新'; }, 650);
});

$('#inventoryToggle').addEventListener('click', () => {
  const note = $('.inventory-note strong');
  note.textContent = note.textContent === 'TOP 3 项目占 79%' ? '外箱 31.7% · 内盒 68.3%' : 'TOP 3 项目占 79%';
});

renderFlowChart();
renderExceptions();

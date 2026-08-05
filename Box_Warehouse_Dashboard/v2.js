const cards = document.querySelectorAll('.glass, .glass-strong');
cards.forEach((card) => {
  if (!card.firstElementChild || !card.firstElementChild.classList.contains('backdrop')) {
    const backdrop = document.createElement('div');
    backdrop.className = 'backdrop';
    card.insertBefore(backdrop, card.firstChild);
  }
});
const board = document.getElementById('wallboard');
function fitBoard(){
  const scale = Math.min(window.innerWidth / 1920, window.innerHeight / 1080);
  board.style.transform = `translate(-50%, -50%) scale(${scale})`;
}
fitBoard();
window.addEventListener('resize', fitBoard, { passive:true });

const clock = document.getElementById('clock');
let second = 59;
setInterval(() => {
  second = (second + 1) % 60;
  clock.textContent = `2026-07-31 23:59:${String(second).padStart(2,'0')}`;
}, 1000);

document.querySelectorAll('.in-icon').forEach((el) => { el.textContent = '\u5165\u5e93'; });
document.querySelectorAll('.out-icon').forEach((el) => { el.textContent = '\u51fa\u5e93'; });
document.querySelectorAll('.in-icon').forEach((el) => { el.textContent = String.fromCodePoint(0x5165, 0x5e93); });
document.querySelectorAll('.out-icon').forEach((el) => { el.textContent = String.fromCodePoint(0x51fa, 0x5e93); });

// Navigate dashboard cards into the original Vue warehouse pages.
const detailLinks = [
  ['.hero-kpis > div:nth-child(1)', 'inbound'],
  ['.hero-kpis > div:nth-child(2)', 'outbound'],
  ['.hero-kpis > div:nth-child(3)', 'inventory'],
  ['.hero-kpis > div:nth-child(4)', 'inventory'],
  ['.hero-kpis > div:nth-child(5)', 'inventory'],
  ['.hero-kpis > div:nth-child(6)', 'exceptions'],
  ['.left-column .metric-box:nth-child(1)', 'inbound'],
  ['.left-column .metric-box:nth-child(2)', 'outbound'],
  ['.left-column .stock-card', 'inventory'],
  ['.right-column .stock-card', 'monthly'],
  ['.operation-grid .task-pane:nth-child(1)', 'inbound'],
  ['.operation-grid .task-pane:nth-child(2)', 'outbound'],
  ['.order-card', 'exceptions'],
];

const vueRoutes = {
  inbound: '/operations',
  outbound: '/operations',
  inventory: '/zones/PK-P01',
  monthly: '/zones/PK-P01',
  exceptions: '/exceptions',
};
const openDetail = (view) => {
  const route = vueRoutes[view] || vueRoutes.inventory;
  const vueBase = window.WAREHOUSE_VUE_BASE || (location.port === '5173' ? '' : `http://${location.hostname || '127.0.0.1'}:5173`);
  window.location.href = `${vueBase}${route}`;
};
detailLinks.forEach(([selector, view]) => {
  document.querySelectorAll(selector).forEach((node) => {
    node.classList.add('detail-link');
    node.tabIndex = 0;
    node.setAttribute('role', 'link');
    node.setAttribute('aria-label', 'Open box warehouse detail');
    node.addEventListener('click', () => openDetail(view));
    node.addEventListener('keydown', (event) => {
      if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); openDetail(view); }
    });
  });
});

function buildThreeDimensionalLines() {
  const svgNamespace = 'http://www.w3.org/2000/svg';
  document.querySelectorAll('svg.chart').forEach((chart) => {
    if (chart.dataset.threeDimensional === 'true') return;
    chart.dataset.threeDimensional = 'true';
    const depthX = 8;
    const depthY = -7;
    const baseY = chart.classList.contains('stock-chart') ? 110 : 120;
    const topY = 18;
    const frame = chart.querySelector('.chart-3d-grid');
    if (frame) {
      frame.innerHTML = `<path class="chart-3d-back" d="M${18 + depthX} ${topY + depthY} V${baseY + depthY} H390"/><path class="chart-3d-depth-axis" d="M18 ${baseY} L${18 + depthX} ${baseY + depthY}"/><path class="chart-3d-depth-axis" d="M18 ${topY} L${18 + depthX} ${topY + depthY}"/><path class="chart-3d-floor" d="M18 ${baseY} L${18 + depthX} ${baseY + depthY} H390"/>`;
    }
    chart.querySelectorAll('polyline.line').forEach((line) => {
      const points = line.getAttribute('points').trim().split(/\s+/).map((point) => point.split(',').map(Number));
      const seriesClass = line.classList.contains('purple-line') ? 'purple' : line.classList.contains('mint-line') ? 'mint' : 'cyan';
      const geometry = document.createElementNS(svgNamespace, 'g');
      geometry.setAttribute('class', `line-3d-geometry line-3d-${seriesClass}`);
      const base = document.createElementNS(svgNamespace, 'polyline');
      base.setAttribute('class', 'line-3d-base');
      base.setAttribute('points', line.getAttribute('points'));
      base.setAttribute('transform', `translate(${depthX} ${depthY})`);
      geometry.appendChild(base);
      for (let index = 0; index < points.length - 1; index += 1) {
        const [x1, y1] = points[index];
        const [x2, y2] = points[index + 1];
        const side = document.createElementNS(svgNamespace, 'polygon');
        side.setAttribute('class', 'line-3d-side');
        side.setAttribute('points', `${x1},${y1} ${x2},${y2} ${x2 + depthX},${y2 + depthY} ${x1 + depthX},${y1 + depthY}`);
        geometry.appendChild(side);
      }
      line.parentNode.insertBefore(geometry, line);
      line.removeAttribute('transform');
      line.classList.add('line-3d-top');
    });
  });
}
buildThreeDimensionalLines();

document.querySelectorAll('.ring-face-occupied, .ring-face-free').forEach((segment) => {
  segment.addEventListener('mouseenter', () => segment.classList.add('is-hovered'));
  segment.addEventListener('mouseleave', () => segment.classList.remove('is-hovered'));
});
document.querySelectorAll('.mini-stat').forEach((stat) => {
  const value = stat.querySelector('b')?.textContent.trim() || '';
  stat.classList.toggle('negative', value.startsWith('-'));
  stat.classList.toggle('positive', !value.startsWith('-'));
});
const dataTooltip = document.createElement('div');
dataTooltip.className = 'warehouse-data-tooltip';
dataTooltip.setAttribute('role', 'status');
dataTooltip.hidden = true;
document.body.appendChild(dataTooltip);
const showDataTooltip = (text, event) => {
  dataTooltip.textContent = text;
  dataTooltip.hidden = false;
  dataTooltip.style.left = `${event.clientX + 14}px`;
  dataTooltip.style.top = `${event.clientY - 12}px`;
};
const hideDataTooltip = () => { dataTooltip.hidden = true; };
const nearestPoint = (points, svg, event) => {
  const rect = svg.getBoundingClientRect();
  const x = (event.clientX - rect.left) / rect.width * 400;
  return points.reduce((best, point, index) => Math.abs(point[0] - x) < Math.abs(points[best][0] - x) ? index : best, 0);
};

document.querySelectorAll('.ring-face-occupied, .ring-face-free').forEach((segment) => {
  segment.addEventListener('mouseenter', (event) => {
    const occupied = segment.classList.contains('ring-face-occupied');
    showDataTooltip(`${occupied ? '已用库位' : '可用库位'} · ${occupied ? '844' : '116'} 个 · ${occupied ? '88%' : '12%'}`, event);
  });
  segment.addEventListener('mousemove', (event) => showDataTooltip(dataTooltip.textContent, event));
  segment.addEventListener('mouseleave', hideDataTooltip);
});
function buildLinePoints() {
  const svgNamespace = 'http://www.w3.org/2000/svg';
  document.querySelectorAll('svg.chart polyline.line').forEach((line) => {
    const svg = line.closest('svg');
    const points = line.getAttribute('points').trim().split(/\s+/).map((point) => point.split(',').map(Number));
    const maxValue = svg.classList.contains('stock-chart') ? 400000 : svg.getAttribute('aria-label').includes('12个月') ? 120000 : 6000;
    const baseY = svg.classList.contains('stock-chart') ? 110 : 120;
    const topY = 18;
    const series = line.classList.contains('purple-line') ? '出库' : line.classList.contains('mint-line') ? '库存' : '入库';
    points.forEach(([x, y], index) => {
      const point = document.createElementNS(svgNamespace, 'circle');
      point.setAttribute('class', `line-point ${series}`);
      point.setAttribute('cx', x);
      point.setAttribute('cy', y);
      point.setAttribute('r', 3.2);
      point.addEventListener('mouseenter', (event) => {
        point.classList.add('is-hovered');
        const value = Math.max(0, Math.round((baseY - y) / (baseY - topY) * maxValue));
        showDataTooltip(`${series} · 第${index + 1}个数据点 · ${value.toLocaleString()}${maxValue >= 100000 ? '' : '单'}`, event);
      });
      point.addEventListener('mousemove', (event) => {
        const value = Math.max(0, Math.round((baseY - y) / (baseY - topY) * maxValue));
        showDataTooltip(`${series} · 第${index + 1}个数据点 · ${value.toLocaleString()}${maxValue >= 100000 ? '' : '单'}`, event);
      });
      point.addEventListener('mouseleave', () => { point.classList.remove('is-hovered'); hideDataTooltip(); });
      svg.appendChild(point);
    });
  });
}
buildLinePoints();
function animateChartEntrance() {
  document.querySelectorAll('svg.chart .line-3d-top').forEach((line, index) => {
    const length = typeof line.getTotalLength === 'function' ? line.getTotalLength() : 900;
    line.style.strokeDasharray = `${length}`;
    line.style.strokeDashoffset = `${length}`;
    line.animate(
      [{ strokeDashoffset: length }, { strokeDashoffset: 0 }],
      { duration: 1250, delay: index * 100, easing: 'cubic-bezier(.2,.75,.25,1)', fill: 'forwards' },
    );
  });
  document.querySelectorAll('svg.chart .line-3d-geometry').forEach((geometry, index) => {
    geometry.animate(
      [{ opacity: 0 }, { opacity: 1 }],
      { duration: 600, delay: index * 80, easing: 'ease-out', fill: 'forwards' },
    );
  });
  document.querySelectorAll('.ring-3d').forEach((ring) => {
    ring.animate(
      [
        { opacity: 0, transform: 'perspective(700px) rotateX(9deg) rotateY(-5deg) scale(.72)' },
        { opacity: 1, transform: 'perspective(700px) rotateX(9deg) rotateY(-5deg) scale(1)' },
      ],
      { duration: 850, easing: 'cubic-bezier(.2,.8,.25,1)', fill: 'forwards' },
    );
  });
}
window.requestAnimationFrame(animateChartEntrance);
function animateTaskTables() {
  const panes = [];
  document.querySelectorAll('.task-pane table').forEach((table) => {
    if (table.classList.contains('task-header-table')) return;
    const head = table.querySelector('thead');
    const body = table.querySelector('tbody');
    if (!head || !body) return;
    const headerTable = document.createElement('table');
    headerTable.className = 'task-header-table';
    headerTable.appendChild(head.cloneNode(true));
    const viewport = document.createElement('div');
    viewport.className = 'task-scroll-window';
    const track = document.createElement('div');
    track.className = 'task-scroll-track';
    const createBodyTable = () => {
      const bodyTable = document.createElement('table');
      bodyTable.className = 'task-body-table';
      bodyTable.appendChild(body.cloneNode(true));
      return bodyTable;
    };
    track.append(createBodyTable(), createBodyTable(), createBodyTable());
    viewport.appendChild(track);
    table.replaceWith(headerTable, viewport);
    panes.push({ track, distance: track.firstElementChild.getBoundingClientRect().height || 244 });
  });
  if (!panes.length) return;
  const duration = 18000;
  let startTime = null;
  const scrollRows = (timestamp) => {
    if (startTime === null) startTime = timestamp;
    const progress = ((timestamp - startTime) % duration) / duration;
    panes.forEach(({ track, distance }) => {
      track.style.transform = `translateY(${-progress * distance}px)`;
    });
    window.requestAnimationFrame(scrollRows);
  };
  window.requestAnimationFrame(scrollRows);
}
window.requestAnimationFrame(animateTaskTables);
const orderRoller = document.querySelector('.order-roller');
const orderRollerTrack = document.querySelector('.order-roller-track');
if (orderRoller && orderRollerTrack) {
  const rollerRows = [...orderRollerTrack.querySelectorAll('.order-row')];
  const rowStep = 59;
  const cycleDistance = rowStep * Math.max(rollerRows.length - 1, 1);
  const cycleDuration = 14000;
  let cycleStart = null;
  const animateRoller = (timestamp) => {
    if (cycleStart === null) cycleStart = timestamp;
    const progress = ((timestamp - cycleStart) % cycleDuration) / cycleDuration;
    const offset = -progress * cycleDistance;
    orderRollerTrack.style.transform = `translateY(${offset}px)`;
    const center = orderRoller.clientHeight / 2;
    let closestIndex = 0;
    let closestDistance = Infinity;
    rollerRows.forEach((row, index) => {
      const rowCenter = row.offsetTop + row.offsetHeight / 2 + offset;
      const distance = Math.abs(rowCenter - center);
      if (distance < closestDistance) { closestDistance = distance; closestIndex = index; }
    });
    rollerRows.forEach((row, index) => row.classList.toggle('order-row--active', index === closestIndex));
    window.requestAnimationFrame(animateRoller);
  };
  window.requestAnimationFrame(animateRoller);
}
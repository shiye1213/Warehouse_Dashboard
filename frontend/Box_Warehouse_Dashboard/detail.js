const views = {
  inventory: {
    eyebrow:'INVENTORY SNAPSHOT', title:'箱盒库存结存', description:'聚合箱盒主库的库位、物料和周转状态，辅助仓内调度。', score:'86.4%', scoreLabel:'库位利用率', scoreNote:'平稳', trend:'近 30 天库存趋势', record:'关联库存记录', metrics:[['当前结存','381,310','个','较昨日 +11,310','▦'],['已占用库位','1,037','个','占库位 86.4%','⌂'],['可用库位','103','个','可释放缓冲位','＋'],['箱盒物料','12','类','外箱 5 · 内盒 7','◇']], rows:[['YL-26070101','外箱入库','3,720','完成','07-31 23:18'],['YL-26070102','内盒盘点','1,160','完成','07-31 22:42'],['YL-26070201','冻结复核','620','待复核','07-31 21:56'],['YL-26070103','库位调整','735','处理中','07-31 20:37']]},
  monthly: {
    eyebrow:'MONTHLY INVENTORY', title:'近 12 个月库存', description:'查看月末结存、周转天数和箱盒库容变化，定位长期占用。', score:'18.6', scoreLabel:'平均周转天数', scoreNote:'较上月 -1.2 天', trend:'近 12 个月库存趋势', record:'月末结存记录', metrics:[['月末结存','381,310','个','同比 +6.8%','▦'],['平均周转','18.6','天','目标 ≤ 21 天','↻'],['峰值占用','1,116','个','发生于 07-18','⌂'],['冻结库位','60','个','占库位 5.0%','!']], rows:[['2026-07','月末结存','381,310','已归档','07-31'],['2026-06','月末结存','369,820','已归档','06-30'],['2026-05','月末结存','357,420','已归档','05-31'],['2026-04','月末结存','344,680','已归档','04-30']]},
  inbound: {
    eyebrow:'INBOUND CONTROL', title:'今日入库详情', description:'跟踪箱盒到货、收货、上架和待检任务的即时进度。', score:'4,095', scoreLabel:'今日入库件数', scoreNote:'完成率 93.2%', trend:'近 30 天入库趋势', record:'入库任务记录', metrics:[['今日入库','4,095','个','较昨日 +11.3%','↓'],['待收货任务','23','单','需优先处理 5 单','!'],['已上架','3,812','个','及时率 93.08%','✓'],['平均收货','26','分钟','较目标 -4 分钟','◷']], rows:[['YL-26070101','丁腈外箱','720','完成','23:18'],['YL-26070102','PVC内盒','1,160','完成','22:42'],['YL-26070201','丁腈内盒','1,480','上架中','21:56'],['YL-26070103','PE外箱','735','待检','20:37']]},
  outbound: {
    eyebrow:'OUTBOUND CONTROL', title:'今日出库详情', description:'追踪箱盒拣货、复核和出库交付节点，识别延迟风险。', score:'3,904', scoreLabel:'今日出库件数', scoreNote:'准时率 91.8%', trend:'近 30 天出库趋势', record:'出库任务记录', metrics:[['今日出库','3,904','个','较昨日 +8.7%','↑'],['待拣货任务','21','单','高峰时段 14:00','!'],['已复核','3,460','个','复核率 88.6%','✓'],['平均拣货','31','分钟','较目标 +2 分钟','◷']], rows:[['YL-26070102','PVC外箱','650','完成','23:05'],['YL-26070201','丁腈内盒','1,420','完成','22:21'],['YL-26070101','丁腈外箱','710','拣货中','21:48'],['YL-26070103','PE内盒','1,124','待复核','20:12']]},
  exceptions: {
    eyebrow:'RISK CONTROL', title:'箱盒库异常详情', description:'聚合库位、扫码、拣货和交付异常，方便责任人快速闭环。', score:'5', scoreLabel:'未关闭事项', scoreNote:'2 条高优先级', trend:'近 30 天异常趋势', record:'异常事件记录', metrics:[['未关闭事项','5','条','较昨日 -2 条','!'],['高优先级','2','条','需今日闭环','▲'],['平均响应','42','分钟','目标 ≤ 60 分钟','◷'],['已关闭','28','条','本月累计','✓']], rows:[['EX2607310','库位异常','箱盒主库','处理中','48m'],['EX2607306','PDA扫码失败','箱盒待检区','未处理','36m'],['EX2607294','出库延迟','YL-26070103','已关闭','71m'],['EX2607288','库位异常','箱盒待检区','已关闭','54m']]}
};

const params = new URLSearchParams(location.search);
const data = views[params.get('view')] || views.inventory;
const fmt = (value) => String(value).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
const $ = (selector) => document.querySelector(selector);

document.title = `${data.title} · 箱盒库`;
$('#heroEyebrow').textContent = data.eyebrow;
$('#heroTitle').textContent = data.title;
$('#heroDescription').textContent = data.description;
$('#heroScore').textContent = data.score;
$('#heroScoreLabel').textContent = data.scoreLabel;
$('#heroScoreNote').textContent = data.scoreNote;
$('#trendTitle').textContent = data.trend;
$('#recordTitle').textContent = data.record;

$('#metricGrid').innerHTML = data.metrics.map(([label,value,unit,note,mark]) => `
  <article class="glass-card metric-card"><span class="metric-mark">${mark}</span><div class="metric-label">${label}</div><div class="metric-value"><strong>${fmt(value)}</strong><small>${unit}</small></div><em>${note}</em></article>
`).join('');

$('#recordRows').innerHTML = data.rows.map(([project,type,quantity,status,time]) => {
  const waiting = /待|处理中|拣货中/.test(status);
  return `<tr><td>${project}</td><td>${type}</td><td>${quantity}</td><td><span class="state${waiting ? ' waiting' : ''}">${status}</span></td><td>${time}</td></tr>`;
}).join('');

document.querySelectorAll('.metric-card').forEach((card) => card.classList.add('detail-link'));
document.querySelector('.back-link')?.focus({ preventScroll:true });

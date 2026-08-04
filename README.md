# INTCO Warehouse · 仓储运营指挥中心

面向客户与高管的仓储经营总览，同时为运营团队提供可继续下钻的作业、履约、空间、异常和资源详情。项目已从原生静态原型升级为 Vue 3 + Spring Boot 前后端架构。

## 已实现

- 高管总览：综合健康度、业务规模、履约质量、空间占用、风险与资源负荷
- 总览下钻：主板各区域可跳转到对应详情页
- 可收起侧边栏：桌面端记忆展开状态，移动端自动切换为抽屉导航
- 8 类页面：经营总览、作业运营、履约质量、空间库存、库区详情、风险异常、资源调度、数据中心
- 真实数据接口：Spring Boot 汇总指标并返回趋势和明细
- Excel / CSV 导入：按日期合并日指标，包含格式与字段校验
- Excel / CSV 导出：完整工作簿含日指标、库区、异常、目标和叉车资源 5 个工作表
- 标准 Excel 模板下载
- 桌面、平板和移动端响应式布局

## 技术架构

```text
Vue 3 + Vue Router + ECharts
            │ /api
            ▼
Spring Boot 2.7 + Apache POI + Commons CSV
            │
            ▼
内存数据服务（由 2026 年 7 月模拟数据初始化）
```

当前机器使用 Java 8，因此后端采用仍兼容 Java 8 的 Spring Boot 2.7.18。将运行环境升级到 Java 17 后，可以再迁移到 Spring Boot 3.x。

## 本地运行

最方便的方式是在项目根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\run.ps1
```

打开 `http://127.0.0.1:5173/`。脚本会在后台启动 Spring Boot，并在当前窗口运行 Vue 开发服务器；结束前端进程时会一并结束后端。

也可以使用两个终端分别启动：

```powershell
cd backend
mvn spring-boot:run
```

```powershell
cd frontend
npm install --cache ..\.codex_tmp\npm-cache
npm run dev
```

## 构建与测试

```powershell
cd frontend
npm run build

cd ..\backend
mvn test
mvn package
```

## 数据导入字段

日指标导入支持 `.xlsx` 和 UTF-8 `.csv`，推荐直接在“数据中心”下载标准模板。字段包括：

`日期`、`入库箱数`、`出库箱数`、`拣货任务`、`叉车任务`、`库存准确率`、`入库及时率`、`出库及时率`、`异常数`、`收货时长(分钟)`、`拣货时长(分钟)`、`平均作业时长(分钟)`、`月台利用率`、`加班工时`。

- 日期为必填项，格式建议使用 `yyyy-MM-dd`
- 百分比可填写 `0.986` 或 `98.6%`
- 同日期记录更新，新增日期追加
- 单次最多导入 10,000 行，文件上限 20MB

## API

- `GET /api/dashboard/overview?range=31`：经营摘要、趋势和各类明细
- `GET /api/zones/{code}`：库区及关联异常
- `POST /api/data/import`：上传 Excel / CSV
- `GET /api/data/export?format=xlsx|csv`：导出数据
- `GET /api/data/template`：下载标准模板
- `GET /api/health`：服务状态

根目录原有的 `index.html`、`app.js`、`styles.css` 和 `data/` 保留为历史原型与种子数据来源；新项目代码位于 `frontend/` 和 `backend/`。

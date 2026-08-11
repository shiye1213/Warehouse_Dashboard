# INTCO Warehouse · 仓储运营指挥中心

面向客户与高管的仓储经营总览，同时为运营团队提供可继续下钻的作业、履约、空间、异常和资源详情。项目已从原生静态原型升级为 Vue 3 + Spring Boot 前后端架构。

## 已实现

- 高管总览：综合健康度、业务规模、履约质量、空间占用、风险与资源负荷
- 总览下钻：主板各区域可跳转到对应详情页
- 可收起侧边栏：桌面端记忆展开状态，移动端自动切换为抽屉导航
- 8 类页面：经营总览、作业运营、履约质量、空间库存、库区详情、风险异常、资源调度、数据中心
- MySQL 持久化：保存仓库、库存、SKU 日指标、仓库日指标、库区、异常、BOM 与 KPI
- Excel / CSV 导入：完整工作簿事务替换，仓库日指标 CSV 按日期与仓库合并
- Excel / CSV 导出：完整工作簿含 8 个可回导数据工作表，并支持仓库日指标 CSV
- 标准 Excel 模板下载与数据表状态检查
- 桌面、平板和移动端响应式布局

## 技术架构

```text
Vue 3 + Vue Router + ECharts
            │ /api
            ▼
Spring Boot 2.7 + MyBatis-Plus + Apache POI + Commons CSV
            │
            ▼
MySQL 8（空库由 2026 年 7 月模拟数据自动初始化）
```

当前机器使用 Java 8，因此后端采用仍兼容 Java 8 的 Spring Boot 2.7.18。将运行环境升级到 Java 17 后，可以再迁移到 Spring Boot 3.x。

## 本地运行

本项目直接使用 Windows 本机 MySQL 8，不依赖 Docker。管理员 PowerShell 中启动数据库服务：

```powershell
Start-Service MySQL80
Get-Service MySQL80
```

首次使用时，以 MySQL 管理员账号执行初始化脚本：

```powershell
& 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' -u root -p `
  -e "source scripts/init-local-mysql.sql"
```

然后启动前后端：

```powershell
powershell -ExecutionPolicy Bypass -File .\run.ps1
```

打开 `http://127.0.0.1:5173/`。脚本会在后台启动 Spring Boot，并在当前窗口运行 Vue 开发服务器；结束前端进程时会一并结束后端。

默认连接本机 `warehouse_dashboard`。如账号或端口不同，可通过环境变量覆盖：

```powershell
$env:WAREHOUSE_DB_URL = 'jdbc:mysql://127.0.0.1:3306/warehouse_dashboard?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false'
$env:WAREHOUSE_DB_USERNAME = 'warehouse'
$env:WAREHOUSE_DB_PASSWORD = 'warehouse123'
```

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

## 数据导入

完整 `.xlsx` 导入支持以下 11 个数据工作表：

`仓库主数据`、`现存量快照`、`运营_SKU日指标`、`运营_仓库每日指标`、`运营_库区状态`、`运营_异常事件`、`项目_BOM关系`、`运营_KPI目标`、`库龄规则`、`库龄批次明细`、`库龄SKU汇总`。

完整导入会先校验全部工作表，再在单个数据库事务中替换数据。UTF-8 `.csv` 用于轻量更新 `运营_仓库每日指标`，以 `biz_date + warehouse_id` 为合并主键。建议从“数据中心”下载最新标准模板。

## 定时数据演示

后端默认每 5 分钟交替新增和删除一条编号为 `DEMO-AUTO-EXCEPTION` 的演示异常。任务只操作这一条固定记录，前端每 30 秒自动刷新，因此无需手动刷新页面。首次执行发生在后端启动 5 分钟后。

可通过环境变量关闭任务或修改周期：

```powershell
$env:WAREHOUSE_DEMO_MUTATION_ENABLED = 'false'
$env:WAREHOUSE_DEMO_MUTATION_INTERVAL_MS = '300000'
$env:WAREHOUSE_DEMO_MUTATION_INITIAL_DELAY_MS = '300000'
```

## API

- `GET /api/dashboard/overview?range=31`：经营摘要、趋势和各类明细
- `GET /api/dashboard/warehouses/{warehouseId}?range=31`：原料库、成品库或箱盒库单仓看板数据
- `GET /api/warehouses`：仓库主数据
- `GET /api/zones/{code}`：库区及关联异常
- `POST /api/data/import`：上传 Excel / CSV
- `GET /api/data/export?format=xlsx|csv`：导出数据
- `GET /api/data/template`：下载标准模板
- `GET /api/data/status`：数据表行数和数据期间
- `GET /api/health`：服务状态

数据库关系、字段映射和事务规则见 [数据库设计](docs/database-design.md)。

根目录原有的 `index.html`、`app.js`、`styles.css` 和 `data/` 保留为历史原型与种子数据来源；新项目代码位于 `frontend/` 和 `backend/`。

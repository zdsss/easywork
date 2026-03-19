# XiaoBai Easy WorkOrder System — 项目总览

> 最后更新：2026-03-19

---

## 一、项目简介

**XiaoBai Easy WorkOrder** 是一套面向制造业的工单管理系统（MES），采用前后端完全分离架构，由三个独立 Git 仓库组成。

| 仓库 | 技术栈 | 说明 |
|------|--------|------|
| `easywork` | Spring Boot 3.2 / Java 21 / MyBatis-Plus / Flyway | 后端 REST API |
| `easywork-admin` | Vue 3 + Vite 5.4 + Element Plus | 管理端前端（PC） |
| `easywork-worker` | Vue 3 + Vite 5.4 + Vant | 工人端前端（工业 PDA / 移动端） |

---

## 二、系统架构

```
┌─────────────────────────────────────────────────────┐
│                     浏览器                          │
│  ┌───────────────────┐  ┌──────────────────────┐   │
│  │  easywork-admin   │  │  easywork-worker      │   │
│  │  localhost:5173   │  │  localhost:5174        │   │
│  │  (PC 管理端)      │  │  (移动端 工人)         │   │
│  └────────┬──────────┘  └──────────┬────────────┘   │
│           │ /api/* proxy            │ /api/* proxy   │
└───────────┼─────────────────────────┼───────────────┘
            ▼                         ▼
┌───────────────────────────────────────────────────┐
│          easywork  localhost:8080                  │
│          Spring Boot 3.2 / Java 21                │
│  ┌──────────┐ ┌──────────┐ ┌──────────────────┐  │
│  │  Spring  │ │ MyBatis  │ │  Spring Security  │  │
│  │ Security │ │  Plus    │ │   JWT (24h)       │  │
│  └──────────┘ └──────────┘ └──────────────────┘  │
│  ┌──────────┐ ┌──────────┐ ┌──────────────────┐  │
│  │ Bucket4j │ │ Caffeine │ │  WorkOrderState   │  │
│  │(限流429) │ │(统计缓存)│ │    Machine        │  │
│  └──────────┘ └──────────┘ └──────────────────┘  │
└────────────┬──────────────────────────────────────┘
             │
    ┌────────┴────────┐
    ▼                 ▼
┌──────────┐    ┌──────────────────────────┐
│PostgreSQL│    │  Redis Sentinel 集群      │
│  :5432   │    │  master:6379             │
└──────────┘    │  sentinel×3: 26379-26381 │
                └──────────────────────────┘
```

---

## 三、工单状态流转

**生产工单 (PRODUCTION)**
```
NOT_STARTED
    │  工人「开工」POST /device/start
    ▼
STARTED
    │  工人「报工」POST /device/report
    ▼
REPORTED ──── 工人「撤销」POST /device/report/undo ──► STARTED
    │
    │  检验员「提交质检」POST /device/inspect
    ├──── PASSED ──► INSPECT_PASSED ──── 管理员「完成」PUT /admin/work-orders/:id/complete ──► COMPLETED
    ├──── FAILED/REWORK ──► INSPECT_FAILED ──── 管理员「返工」PUT /admin/work-orders/:id/reopen ──► REPORTED
    └──── SCRAP_MATERIAL/SCRAP_PROCESS ──► SCRAPPED（终态）
```

**检验 / 转运 / 安灯工单 (INSPECTION / TRANSPORT / ANDON)**
```
NOT_STARTED → STARTED → COMPLETED（报工即完成，不走质检流程）
```

状态转换规则由 `WorkOrderStateMachine`（`modules/workorder/statemachine`）集中管理，提供 `canTransition` / `allowedTransitions` 方法供各 Service 调用。

---

## 四、API 权限规则

| 路径前缀 | 角色要求 | 说明 |
|----------|----------|------|
| `/api/auth/**` | 公开 | 统一登录入口 |
| `/api/device/**` | WORKER 或 ADMIN | 工人端接口 |
| `/api/admin/**` | ADMIN | 管理端接口 |
| `/api/mes/**` | ADMIN | MES 外部推送接口 |

**限流规则（`RateLimitFilter` / Bucket4j）：**

| 接口 | 限制 | 超限响应 |
|------|------|---------|
| `POST /api/auth/login` | 10 次/分钟/IP | HTTP 429 |
| `POST /api/device/**` | 60 次/分钟/IP | HTTP 429 |

配置项：`app.rate-limit.login-max-requests` / `app.rate-limit.device-max-requests`（等）

---

## 五、完整 API 接口清单

### 5.1 认证

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/login` | 统一登录。Body: `{employeeNumber, password, deviceCode?}` |

**登录响应：**
```json
{
  "code": 200,
  "data": {
    "token": "eyJ...",
    "userId": 1,
    "employeeNumber": "ADMIN001",
    "realName": "System Admin",
    "role": "ADMIN"
  }
}
```

---

### 5.2 管理端 — 用户管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/users?page=1&size=20` | 用户列表（返回 Array） |
| POST | `/api/admin/users` | 创建用户 |

**创建用户 Body：**
```json
{
  "employeeNumber": "W001",
  "username": "worker01",
  "password": "worker123",
  "realName": "张三",
  "role": "WORKER",
  "phone": "可选"
}
```

> `username` 为必填字段（独立于 employeeNumber）

---

### 5.3 管理端 — 班组管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/teams` | 班组列表（Array，成员嵌入 members 字段） |
| POST | `/api/admin/teams` | 创建班组 |
| POST | `/api/admin/teams/:id/members` | 添加成员 |

**添加成员 Body：**
```json
{ "userIds": [3, 4] }
```

> 后端无删除成员接口

---

### 5.4 管理端 — 工单管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/work-orders?page=1&size=20&status=可选` | 工单列表（Array） |
| GET | `/api/admin/work-orders/:id` | 工单详情 |
| POST | `/api/admin/work-orders` | 创建工单 |
| POST | `/api/admin/work-orders/assign` | 派工 |

**创建工单 Body：**
```json
{
  "orderNumber": "WO-2026-001",
  "orderType": "PRODUCTION",
  "productName": "产品名",
  "productCode": "P001",
  "plannedQuantity": 100,
  "priority": 7,
  "plannedStartTime": "2026-03-12T08:00:00",
  "plannedEndTime": "2026-03-15T18:00:00",
  "notes": "备注",
  "operations": [
    { "operationName": "切割", "sequenceNumber": 1, "plannedQuantity": 100 }
  ]
}
```

> `orderNumber` 和 `orderType` 为必填字段；`orderType` 可选值：`PRODUCTION` / `INSPECTION` / `TRANSPORT` / `ANDON`

**派工 Body：**
```json
{
  "operationId": 1,
  "assignmentType": "USER",
  "userIds": [3],
  "teamIds": []
}
```

**工单 DTO 关键字段：**
```
orderNumber / orderType / productName / productCode
plannedQuantity / completedQuantity / remainingQuantity
status / priority / notes
operations[]: { id, operationName, operationNumber, sequenceNumber, status, plannedQuantity, completedQuantity }
```

---

### 5.5 管理端 — 工单生命周期操作

| 方法 | 路径 | 说明 |
|------|------|------|
| PUT | `/api/admin/work-orders/:id/complete` | INSPECT_PASSED → COMPLETED |
| PUT | `/api/admin/work-orders/:id/reopen` | INSPECT_FAILED → REPORTED（返工） |
| POST | `/api/admin/work-orders/:id/rework` | 触发返工流程 |
| POST | `/api/admin/inspections` | 提交质检（管理员也可用） |

> 待质检工单：`GET /admin/work-orders?status=REPORTED`（无专用列表接口）

### 5.5b 管理端 — 工序依赖 & 审计日志

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/operations/:id/dependencies` | 查询工序依赖 |
| POST | `/api/admin/operations/:id/dependencies` | 添加工序依赖（仅 SERIAL / PARALLEL 类型） |
| GET | `/api/admin/audit-logs` | 操作审计日志（ISO 9001） |

---

### 5.6 管理端 — 统计

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/statistics/dashboard` | 统计看板（结果 Caffeine 缓存 TTL 30s） |

**响应 data 字段：**
```
totalWorkOrders / notStartedCount / startedCount / reportedCount / completedCount
overallCompletionRate / typeStats[] / workerStats[]
```

> `completedCount` = INSPECT_PASSED + COMPLETED（语义：已通过质检的工单数）

---

### 5.7 管理端 — MES 集成

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/mes-integration/stats` | MES 同步统计 |
| GET | `/api/admin/mes-integration/logs?page=1&size=20&direction=&status=&syncType=` | 同步日志（**分页**，返回 `{records,total,current,size,pages}`） |

> MES 默认关闭（`app.mes.integration.enabled: false`）；失败重试使用指数退避（5s→10s→…→300s，最多 5 次）

---

### 5.8 工人端（Device）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/device/login` | 工人端专用登录（同 auth/login，可带 deviceCode） |
| GET | `/api/device/work-orders` | 获取当前工人被派工的工单（Array） |
| POST | `/api/device/start` | 开工（委托 `WorkStartService`） |
| POST | `/api/device/report` | 报工（委托 `ReportService`） |
| POST | `/api/device/report/undo` | 撤销报工 |
| POST | `/api/device/inspect` | 提交质检（检验员在工人端使用） |
| POST | `/api/device/call/andon` | Andon 呼叫 |
| POST | `/api/device/call/inspection` | 质检呼叫 |
| POST | `/api/device/call/transport` | 搬运呼叫 |
| POST | `/api/device/scan/start` | 条码扫描开工（工单号/工序号双模式，`ScanService` 解析） |
| POST | `/api/device/scan/report` | 条码扫描报工（`ScanService` 解析） |
| POST | `/api/device/batch/start` | 批量开工（支持幂等 Key） |
| POST | `/api/device/batch/report` | 批量报工（支持幂等 Key） |

**质检 Body：**
```json
{
  "workOrderId": 1,
  "inspectionResult": "PASSED",
  "inspectedQuantity": 100,
  "qualifiedQuantity": 98,
  "defectQuantity": 2,
  "defectReason": "可选",
  "notes": "可选"
}
```

> `inspectionResult` 可选值：`PASSED` / `FAILED` / `REWORK` / `SCRAP_MATERIAL` / `SCRAP_PROCESS`

**开工 Body：** `{ "operationId": 1 }`

**报工 Body：**
```json
{
  "operationId": 1,
  "reportedQuantity": 80,
  "qualifiedQuantity": 78,
  "defectQuantity": 2,
  "notes": "可选"
}
```

**撤销 Body：** `{ "operationId": 1, "undoReason": "数量填错" }`

**呼叫 Body：** `{ "workOrderId": 1, "operationId": 1, "description": "描述" }`

**批量操作幂等：** 请求头携带 `Idempotency-Key: <UUID>`，服务端 Redis 缓存 30 分钟结果，重放请求直接返回缓存响应。

> `GET /api/device/work-orders/:id` **不存在**。工人端详情通过列表数据传递。

---

### 5.9 MES Webhook

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/mes/work-orders/import` | MES 系统推送工单到本系统 |

---

## 六、通用响应格式

```json
{
  "code": 200,
  "message": "Success",
  "data": { ... },
  "timestamp": 1773238000000
}
```

- `code === 200`：成功，使用 `data`
- 其他 code：失败，显示 `message`

**分页响应格式（仅 MES logs 使用）：**
```json
{ "code": 200, "data": { "records": [...], "total": 100, "current": 1, "size": 20, "pages": 5 } }
```

**其余列表接口** `data` 直接为 Array。

---

## 七、认证机制

- **Token 存储**：`localStorage`，key = `token`
- **请求头**：`Authorization: Bearer <token>`
- **有效期**：24 小时
- **Secret**：生产环境必须通过环境变量 `JWT_SECRET` 注入
- **401/403 处理**：axios 拦截器自动跳转 `/login`

---

## 八、前端项目结构

### easywork-admin（管理端）

```
easywork-admin/
├── vite.config.js          # 端口 5173，proxy /api → 8080
├── src/
│   ├── api/
│   │   ├── http.js         # Axios 拦截器（token注入、错误弹窗、401跳转）
│   │   ├── workorder.js    # 工单 CRUD + assign + complete + reopen
│   │   ├── dependency.js   # 工序依赖 GET/POST
│   │   ├── statistics.js   # 统计看板
│   │   └── mes.js          # MES stats + logs
│   ├── utils/
│   │   └── statusLabel.js  # orderType × status → 中文标签 + Element Plus tag 类型
│   └── views/
│       ├── DashboardView.vue           # 统计看板（ECharts，含 resize 监听）
│       ├── workorder/
│       │   ├── WorkOrderListView.vue   # 列表 + 状态筛选（含 SCRAPPED）
│       │   ├── WorkOrderCreateView.vue # 创建（4 种 orderType）+ 动态工序
│       │   └── WorkOrderDetailView.vue # 详情 + 派工 + 依赖有向图（Vue Flow + Kahn 拓扑）+ 完成/返工
│       ├── AuditLogView.vue    # 操作审计日志（ISO 9001）
│       └── MesView.vue         # MES 统计 + 日志表格
```

### easywork-worker（工人端）

```
easywork-worker/
├── vite.config.js          # 端口 5174，proxy /api → 8080
├── public/sw.js            # Service Worker（离线队列支持）
├── src/
│   ├── composables/
│   │   ├── useHardwareInput.js  # 硬件输入层：扫码枪识别（50ms）/ 方向键 / 快捷键 / ESC
│   │   └── usePhysicalKeys.js   # 原始键盘层（兼容保留）
│   ├── utils/
│   │   ├── statusLabel.js  # orderType × status → 中文标签 + Vant tag 类型
│   │   └── offlineQueue.js # IndexedDB 离线队列（断网排队/联网重放）
│   ├── components/
│   │   ├── KeyHints.vue    # 固定在 tabbar 上方的快捷键提示条
│   │   ├── T9Input.vue     # T9 九键键盘（登录页密码输入）
│   │   └── StatusBar.vue   # 右上角状态栏（网络/电池/扫码）
│   └── views/
│       ├── WorkOrderListView.vue    # 下拉刷新 + 方向键导航 + 焦点工单模型（currentIndex + n+1/total）
│       ├── WorkOrderDetailView.vue  # 开工/报工/质检/撤销 + 快捷键 1-5 + 工序列表方向键导航
│       ├── ScanView.vue             # 扫码页（开工/报工模式，Tab 键切换，摄像头 + 扫码枪）
│       ├── BatchView.vue            # 批量操作（多选工序 + 批量开工/报工）
│       └── CallView.vue             # 三种呼叫类型（Andon/质检/搬运）
```

---

## 九、启动方式

```powershell
# Step 1：启动数据库（PostgreSQL + Redis 单节点，在 easywork\ 目录下执行）
cd easywork
& "C:\Program Files\Docker\Docker\resources\bin\docker.exe" compose up -d postgres redis-master

# Step 2：启动后端（新终端，在 easywork\ 目录下执行）
$env:JAVA_HOME="D:\Software\Java21"; & "D:\Software\apache-maven-3.9.13\bin\mvn" spring-boot:run

# Step 3：启动管理端（新终端）
cd easywork-admin
npm run dev   # → http://localhost:5173

# Step 4：启动工人端（新终端）
cd easywork-worker
npm run dev   # → http://localhost:5174
```

> 工具路径详见 `.claude.md` — 开发环境约束章节

---

## 十、默认账号

| 员工号 | 密码 | 角色 | 说明 |
|--------|------|------|------|
| `ADMIN001` | `admin123` | ADMIN | 系统默认管理员 |
| `W001` | `worker123` | WORKER | 测试工人账号（手动创建） |

---

## 十一、已知限制与注意事项

| 项目 | 说明 |
|------|------|
| 班组成员删除 | 后端无该接口，前端仅展示提示 |
| 工人端工单详情 | 无 `GET /device/work-orders/:id`，通过列表数据本地查找 |
| MES 集成 | 默认关闭，配置 `app.mes.integration.enabled=true` 启用 |
| deviceCode | `/device/login` 支持但需数据库中存在对应设备记录 |
| 条码扫描 | `ScanService` 处理双模式（工单号/工序号）解析；前端支持摄像头 + 扫码枪 + 手动输入 |
| Node.js 版本 | 前端 Vite 5.x 要求 Node.js 18+（LTS）；不要升级 Vite 到 7.x |
| 分页 | 仅 MES logs 返回分页对象；其余列表接口返回 Array |
| 强制开工配置 | `app.workorder.force-start-before-report` 支持按 orderType 配置（map 结构） |
| 工序依赖执行 | 仅 SERIAL 类型前置工序阻塞开工；PARALLEL 不阻塞 |
| 幂等性 | 批量操作支持 `Idempotency-Key` 请求头（UUID）；Redis 缓存 30min |
| Flyway | 当前最高版本 V1.9（性能索引）；`baseline-on-migrate: true`，`baseline-version: 1.6` |
| Redis 生产部署 | docker-compose 含 Redis Sentinel 集群（3 哨兵 + 主从）；本地开发使用 `redis-master` 单节点即可 |
| 集成测试 | 使用 Testcontainers 自动启动 PostgreSQL，无需手动 `docker compose up`；`mvn test -Dgroups=integration` |

---

## 十二、测试状态

**单元测试：** 165 个，全部通过 ✅（2026-03-19）

**集成测试：** 2 个（Testcontainers 自动启动 PostgreSQL，`@Tag("integration")`）

**测试框架：** JUnit 5 + Mockito + Spring Boot Test + Testcontainers

| 测试类 | 覆盖重点 |
|--------|---------|
| WorkStartServiceTest | 开工逻辑（NOT_STARTED 检查 + 工序依赖 SERIAL/PARALLEL + 工单首次开工） |
| ReportServiceTest | 报工状态机（报工量校验 + 完工判断 + 撤销 + 事件发布） |
| ScanServiceTest | 条码解析（工单号/工序号双模式 + 最早未开工工序匹配） |
| WorkOrderStateMachineTest | 状态转换规则（PRODUCTION/INSPECTION/TRANSPORT/ANDON 全矩阵） |
| WorkOrderServiceTest | 工单生命周期（创建/派工/完成/返工/去重排序） |
| InspectionServiceTest | 质检结果分支（PASSED/FAILED/REWORK/SCRAP + 事件发布） |
| DeviceControllerTest | BFF 层 HTTP + 权限控制 |
| WorkOrderMapperIntegrationTest | Mapper 层集成（需 Testcontainers PostgreSQL） |
| ReportServiceConcurrentIntegrationTest | 并发报工（需 Testcontainers PostgreSQL） |

**端到端流程验证：**
- `NOT_STARTED → STARTED → REPORTED → INSPECT_PASSED → COMPLETED` ✅
- `PRODUCTION 报工 → INSPECT_FAILED → reopen → 重新报工` ✅
- `INSPECTION 工单报工 → 直接 COMPLETED（不走质检）` ✅
- 工序依赖：前置未完成 → 阻塞开工 ✅
- 扫码开工：工单条码 → 自动开工最前道未开工工序 ✅
- 离线队列：断网操作 → 联网后自动重放并显示结果 ✅

---

## 十三、更新历史

### 2026-03-19 — 工具目录 & .gitignore

| 内容 |
|------|
| 新增 `utils/` 目录：Playwright 截图/PDF 报告工具（`take_screenshots.js`、`generate_pdf.js`、`current_state_report.md/pdf`） |
| 更新 `.gitignore`：排除 `utils/node_modules/`、`utils/browsers/`、`utils/pw-browsers/`、`utils/.playwright/`、`utils/screenshots/` |
| 移除 `.github/workflows/ci.yml`（CI 工作流） |

### 2026-03-18 — CTO 审查 P0/P1 加固

| 编号 | 内容 |
|------|------|
| P0 | 删除 `common/constant` 死代码包（3 个重复 enum） |
| P1-A | 拆分 `ReportService` → `WorkStartService`（开工/依赖检查）+ `ReportService`（报工/撤销） |
| P1-B | 拆分 `DeviceController` → `ScanService`（条码解析）+ 薄层控制器 |
| P1-C | 新增 `WorkOrderStateMachine`（集中状态转换规则，替代各 Service 中分散的 if/switch） |
| P1-D | 新增 `OperationStatus` Java Enum（替代 `Operation.status` String 字段） |
| P1-E | Flyway V1.8：`work_orders` / `operations` 状态列添加 CHECK 约束 |
| P1-F | Flyway V1.9：`work_orders`、`operations`、`report_records` 添加性能索引 |
| P2-A | Redis Sentinel 集群配置（docker-compose：3 哨兵 + 主从） |
| P2-B | Prometheus 指标端点（`/actuator/prometheus`） |
| P2-C | Testcontainers 替代手动 Docker 用于集成测试；集成测试通过 `@Tag("integration")` 隔离 |
| P2-D | `StatisticsService` Caffeine 缓存（TTL 30s） |
| P2-E | MES 重试改为指数退避（5s→10s→…→300s，最多 5 次） |
| P2-F | 登录端点 Bucket4j 限速（10次/分钟/IP，返回 HTTP 429） |
| 修复 | 解决所有测试编译错误（`OperationStatus` 导入、stub 参数不匹配）；单元测试总数 140 → 165 |

### 2026-03-17 — CTO 审查 v2 优化（Q 系列 + N 系列 + C 系列）

| 编号 | 内容 |
|------|------|
| Q-1 | 引入 Flyway 自动迁移（`flyway-core` 9.x），消除手动建表风险 |
| Q-2 | `WorkOrderStatus` / `WorkOrderType` / `DependencyType` 提取为 Java Enum + `EnumTypeHandler`，消除字符串字面量 |
| Q-3 | 批量操作幂等：工人端生成 UUID 作为 `Idempotency-Key`，`IdempotencyService` Redis 缓存 30 分钟结果 |
| Q-4 | JWT secret 强制环境变量注入（移除默认值，启动失败优于弱密钥） |
| N-1 | 实现\"让步接收\"质检结果（`ACCEPT_WITH_CONCESSION`）：后端枚举分支 + 工人端按钮 + 状态标签 |
| N-3 | 工人端批量操作快捷键：列表页数字键 `1`=全部开工 / `2`=全部报工 |
| C-1 | CONDITIONAL 依赖类型彻底移除（后端枚举 + Mapper XML + 管理端 UI 全部清理） |
| C-2 | 强制开工配置改为按 `orderType` 的 map 结构（替代全局 bool） |

### 2026-03-16 — CTO 审查 v1 P0-P3 修复

| 编号 | 内容 |
|------|------|
| P0-A | `OperationDependencyService.getPredecessors()` 查询方向修复 |
| P0-B | `useHardwareInput.js` 扫码枪 + 输入框焦点兼容（普通字符透传，扫码 Enter 强制拦截） |
| P0-C | `WorkOrderListView` 焦点工单模型（`currentIndex` + `n+1/total` 显示） |
| P0-D | `WorkOrderService.completeWorkOrder()` 按 `orderType` 分支（非生产工单 REPORTED → COMPLETED） |
| P1-A | 报工数量快捷键：整数追加（`current×10+digit`）+ Backspace 删末位 |
| P1-B | `WorkOrderDetailView` 工序列表方向键导航（`activeOpIndex` + `scrollIntoView`） |
| P1-C | 扫码班组匹配：`findEarliestNotStartedByUserAndWorkOrder` 定位最前道未开工工序 |
| P1-D | 管理端工单创建依赖失败通知（`ElMessage.warning` 列出失败项） |
| P1-E | 离线队列失败通知（返回 `{processed, failed, skipped}`） |
| P2-A | `WorkOrder` 实体 `@Version` 乐观锁 + V1.6 迁移 |
| P2-B | `operation_dependencies` UNIQUE 约束 + 索引（V1.6 迁移） |
| P2-C | `AuditLogAspect` `@Transactional` + `SecurityUtils` 注入（修复 audit log user_id=null → 500） |
| P2-D | `StatisticsService` `completedCount` 语义统一（INSPECT_PASSED + COMPLETED） |
| P2-E | 管理端依赖图 Kahn 拓扑排序布局（消除节点重叠） |
| P2-F | `DashboardView` ECharts `window.resize` 监听 |
| P3-C | 管理端移除 CONDITIONAL 依赖类型 UI 选项 |
| 兼容 | `easywork-admin` Vite 7.x → 5.4.0（Node 18 兼容） |

### 2026-03-15 — 硬件输入层与工人端核心交互

- `useHardwareInput.js` 硬件输入语义化层（扫码枪识别 50ms 阈值、方向键、数字快捷键 1-5、ESC）
- `KeyHints.vue` 固定快捷键提示条
- `WorkOrderListView` 替换为 `useHardwareInput`，扫码开工后刷新并跳转详情
- `WorkOrderDetailView` 数字快捷键 1=开工/2=报工/3=质检/4=撤销/5=返工
- `ScanView` 开工/报工模式切换（Tab 键）

### 2026-03-14 — 核心功能实现

- SCRAPPED 终态（V1.5 迁移）
- `ReportService` 按 `orderType` 分支处理状态流转 + 串行前置工序依赖检查
- `POST /api/device/inspect` 新增（检验员工人端提交质检）
- `InspectionService` 扩展 REWORK/SCRAP 分支
- 工人端检验工单「提交质检」按钮；`statusLabel.js` 按 `orderType` 映射
- T9 九键键盘（工人端登录页）
- `BatchView.vue` 批量开工/报工；`ScanView.vue` 扫码页（摄像头 + 扫码枪）
- 管理端工序依赖有向图（Vue Flow）；按 `orderType` 分组展示完成率

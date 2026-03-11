# XiaoBai Easy WorkOrder System — 项目总览

> 更新时间：2026-03-11

---

## 一、项目简介

**XiaoBai Easy WorkOrder** 是一套面向制造业的工单管理系统（MES），采用前后端完全分离架构，由三个独立 Git 仓库组成。

| 仓库 | 技术栈 | 说明 |
|------|--------|------|
| `easywork` | Spring Boot 3.2 / Java 21 | 后端 REST API |
| `easywork-admin` | Vue 3 + Element Plus | 管理端前端（PC） |
| `easywork-worker` | Vue 3 + Vant | 工人端前端（移动端） |

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
└────────────┬──────────────────────────────────────┘
             │
    ┌────────┴────────┐
    ▼                 ▼
┌──────────┐    ┌──────────┐
│PostgreSQL│    │  Redis   │
│  :5432   │    │  :6379   │
└──────────┘    └──────────┘
```

---

## 三、工单状态流转

```
NOT_STARTED
    │
    │ 工人「开工」POST /device/start
    ▼
STARTED
    │
    │ 工人「报工」POST /device/report
    ▼
REPORTED ──────────────────────────────── 工人「撤销」POST /device/report/undo ──► STARTED
    │
    │ 管理员「质检」POST /admin/inspections
    ├──── inspectionResult=PASSED ──► INSPECT_PASSED
    │                                         │
    └──── inspectionResult=FAILED ──► INSPECT_FAILED
                                              │
                                      （可重新质检）
                                              │
                                        COMPLETED
```

---

## 四、API 权限规则

| 路径前缀 | 角色要求 | 说明 |
|----------|----------|------|
| `/api/auth/**` | 公开 | 统一登录入口 |
| `/api/device/**` | WORKER 或 ADMIN | 工人端接口 |
| `/api/admin/**` | ADMIN | 管理端接口 |
| `/api/mes/**` | ADMIN | MES 外部推送接口 |

---

## 五、完整 API 接口清单（24 个）

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

> ⚠️ `username` 为必填字段（独立于 employeeNumber）

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

> ⚠️ 目前后端无删除成员接口

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

> ⚠️ `orderNumber` 和 `orderType` 为必填字段

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

### 5.5 管理端 — 质检

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/admin/inspections` | 提交质检结果 |

> ⚠️ 无 GET 质检列表接口。待质检工单通过 `GET /admin/work-orders?status=REPORTED` 获取。

**质检 Body：**
```json
{
  "workOrderId": 1,
  "operationId": 1,
  "inspectionResult": "PASSED",
  "inspectedQuantity": 100,
  "qualifiedQuantity": 98,
  "defectQuantity": 2,
  "defectReason": "可选",
  "notes": "可选"
}
```

> ⚠️ 字段名为 `inspectionResult`（值 `"PASSED"` / `"FAILED"`），非布尔值

---

### 5.6 管理端 — 统计

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/statistics/dashboard` | 统计看板 |

**响应 data 字段：**
```
totalWorkOrders / notStartedCount / startedCount / reportedCount / completedCount
overallCompletionRate / typeStats[] / workerStats[]
```

**workerStats 字段：** `userId / realName / employeeNumber / reportCount / totalReported`

---

### 5.7 管理端 — MES 集成

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/mes-integration/stats` | MES 同步统计 |
| GET | `/api/admin/mes-integration/logs?page=1&size=20&direction=&status=&syncType=` | 同步日志（**分页**，返回 `{records,total,current,size,pages}`） |

**direction 值：** `INBOUND`（从MES）/ `OUTBOUND`（到MES）
**status 值：** `PENDING` / `SUCCESS` / `FAILED` / `RETRYING`

> ⚠️ MES 默认关闭（`app.mes.integration.enabled: false`）

---

### 5.8 工人端（Device）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/device/login` | 工人端专用登录（同 auth/login，可带 deviceCode） |
| GET | `/api/device/work-orders` | 获取当前工人被派工的工单（Array） |
| POST | `/api/device/start` | 开工 |
| POST | `/api/device/report` | 报工 |
| POST | `/api/device/report/undo` | 撤销报工 |
| POST | `/api/device/call/andon` | Andon 呼叫 |
| POST | `/api/device/call/inspection` | 质检呼叫 |
| POST | `/api/device/call/transport` | 搬运呼叫 |
| POST | `/api/device/scan/start` | 条码扫描开工 |
| POST | `/api/device/scan/report` | 条码扫描报工 |

**开工 Body：**
```json
{ "operationId": 1 }
```

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

**撤销 Body：**
```json
{
  "operationId": 1,
  "undoReason": "数量填错"
}
```

**呼叫 Body（workOrderId 必填）：**
```json
{
  "workOrderId": 1,
  "operationId": 1,
  "description": "描述"
}
```

> ⚠️ `GET /api/device/work-orders/:id` **不存在**。工人端详情通过列表数据传递，无需单独请求。

---

### 5.9 MES Webhook（外部推送）

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
{
  "code": 200,
  "data": {
    "records": [...],
    "total": 100,
    "current": 1,
    "size": 20,
    "pages": 5
  }
}
```

**其余列表接口** `data` 直接为 Array：
```json
{ "code": 200, "data": [...] }
```

---

## 七、认证机制

- **Token 存储**：`localStorage`，key = `token`
- **请求头**：`Authorization: Bearer <token>`
- **有效期**：24 小时
- **401/403 处理**：axios 拦截器自动跳转 `/login`

---

## 八、前端项目结构

### easywork-admin（管理端）

```
easywork-admin/
├── vite.config.js          # 端口 5173，proxy /api → 8080
├── src/
│   ├── main.js             # Element Plus + Pinia + Router
│   ├── App.vue
│   ├── api/
│   │   ├── http.js         # Axios 拦截器（token注入、错误弹窗、401跳转）
│   │   ├── auth.js         # POST /api/auth/login
│   │   ├── workorder.js    # 工单 CRUD + assign
│   │   ├── user.js         # 用户 CRUD
│   │   ├── team.js         # 班组管理 + 成员
│   │   ├── inspection.js   # 质检提交 + 待质检工单查询
│   │   ├── statistics.js   # 统计看板
│   │   └── mes.js          # MES stats + logs
│   ├── stores/
│   │   └── auth.js         # Pinia: token/userId/employeeNumber/realName/role
│   ├── router/
│   │   └── index.js        # 路由守卫（未登录→/login）
│   ├── layouts/
│   │   └── MainLayout.vue  # 侧边栏 + 顶栏（含退出）
│   └── views/
│       ├── LoginView.vue
│       ├── DashboardView.vue
│       ├── workorder/
│       │   ├── WorkOrderListView.vue    # 列表 + 状态筛选
│       │   ├── WorkOrderCreateView.vue  # 创建 + 动态工序
│       │   └── WorkOrderDetailView.vue  # 详情 + 派工弹窗
│       ├── UserView.vue        # 用户列表 + Drawer创建
│       ├── TeamView.vue        # 班组列表 + 成员管理
│       ├── InspectionView.vue  # 待质检工单 + 质检弹窗
│       └── MesView.vue         # MES 统计 + 日志表格
```

### easywork-worker（工人端）

```
easywork-worker/
├── vite.config.js          # 端口 5174，proxy /api → 8080
├── index.html              # 移动端 viewport meta
├── src/
│   ├── main.js             # Vant + Pinia + Router
│   ├── App.vue
│   ├── api/
│   │   ├── http.js         # Axios 拦截器（同 admin）
│   │   ├── auth.js         # POST /api/auth/login
│   │   ├── workorder.js    # GET /api/device/work-orders
│   │   ├── report.js       # start / report / report/undo
│   │   └── call.js         # andon / inspection / transport
│   ├── stores/
│   │   └── auth.js         # 同 admin
│   ├── router/
│   │   └── index.js        # 路由守卫
│   └── views/
│       ├── LoginView.vue            # 工号/密码/设备号
│       ├── WorkOrderListView.vue    # 下拉刷新 + 工单卡片
│       ├── WorkOrderDetailView.vue  # 工序操作（开工/报工/撤销）
│       └── CallView.vue             # 三种呼叫类型
```

---

## 九、启动方式

```bash
# Step 1：启动数据库（PostgreSQL + Redis）
cd /Users/monet/Program/Repo/easyworks/easywork
docker-compose up -d postgres redis

# Step 2：启动后端（必须指定 Java 21）
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
mvn spring-boot:run

# Step 3：启动管理端（新终端）
cd /Users/monet/Program/Repo/easyworks/easywork-admin
npm run dev   # → http://localhost:5173

# Step 4：启动工人端（新终端）
cd /Users/monet/Program/Repo/easyworks/easywork-worker
npm run dev   # → http://localhost:5174
```

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
| 条码扫描 | 后端接口已实现，前端未实现扫码页面 |
| Java 版本 | Maven 需使用 Java 21（非系统默认 Java 25） |
| 分页 | 仅 MES logs 返回分页对象；其余列表接口返回 Array |

---

## 十二、验收测试结果

**测试时间：** 2026-03-11
**测试范围：** 后端全部 24 个 endpoint
**结果：** ✅ 24/24 全部通过

| 模块 | 覆盖 |
|------|------|
| 认证 | 1/1 ✅ |
| 用户管理 | 2/2 ✅ |
| 班组管理 | 3/3 ✅ |
| 工单管理 | 4/4 ✅ |
| 质检 | 1/1 ✅ |
| 统计 | 1/1 ✅ |
| MES 集成 | 2/2 ✅ |
| 工人端 | 10/10 ✅ |
| MES Webhook | 1/1 ✅ |

**端到端流程验证：**
`NOT_STARTED → STARTED → REPORTED → INSPECT_PASSED` ✅

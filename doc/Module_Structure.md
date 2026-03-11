# XiaoBai Easy WorkOrder System — 模块/包结构文档

> **版本**：1.0.0 · **更新日期**：2026-03-10 · **架构**：DDD 启发的模块化单体（Modular Monolith）

---

## 目录

1. [顶层结构总览](#1-顶层结构总览)
2. [全局基础层](#2-全局基础层)
3. [模块列表及功能说明](#3-模块列表及功能说明)
   - [auth — 认证模块](#31-auth--认证模块)
   - [user — 用户模块](#32-user--用户模块)
   - [team — 班组模块](#33-team--班组模块)
   - [device — 设备模块](#34-device--设备模块)
   - [workorder — 工单模块](#35-workorder--工单模块)
   - [operation — 工序模块](#36-operation--工序模块)
   - [report — 报工模块](#37-report--报工模块)
   - [inspection — 质检模块](#38-inspection--质检模块)
   - [call — 呼叫模块](#39-call--呼叫模块)
   - [statistics — 统计模块](#310-statistics--统计模块)
   - [mesintegration — MES集成模块](#311-mesintegration--mes-集成模块)
4. [模块间依赖关系](#4-模块间依赖关系)
5. [数据库表归属总览](#5-数据库表归属总览)
6. [事件流转总览](#6-事件流转总览)
7. [文件索引](#7-文件索引)

---

## 1. 顶层结构总览

```
src/main/java/com/xiaobai/workorder/
│
├── WorkOrderApplication.java              # Spring Boot 启动入口
│
├── config/                                # 全局配置层（8个文件）
│
├── common/                                # 通用基础组件（7个文件）
│   ├── response/
│   ├── exception/
│   ├── constant/
│   └── util/
│
└── modules/                               # 业务模块层（11个模块）
    ├── auth/
    ├── user/
    ├── team/
    ├── device/
    ├── workorder/
    ├── operation/
    ├── report/
    ├── inspection/
    ├── call/
    ├── statistics/
    └── mesintegration/
```

每个业务模块遵循统一的内部分层结构：

```
modules/{module}/
├── controller/     REST 控制器，处理 HTTP 请求/响应
├── service/        业务逻辑，事务边界
├── repository/     MyBatis Plus Mapper，数据访问
├── entity/         数据库映射实体（@TableName）
└── dto/            请求体 DTO / 响应体 DTO
```

`mesintegration` 模块额外包含：

```
modules/mesintegration/
├── event/          Spring 应用事件类 + 监听器
├── client/         MES 系统 HTTP 客户端
├── scheduler/      定时重试任务
├── config/         模块级 RestTemplate 配置
└── constant/       同步类型 / 状态 / 方向常量
```

---

## 2. 全局基础层

### 2.1 config/ — 全局配置

| 文件 | 类型 | 职责 |
|---|---|---|
| `AsyncConfig.java` | `@Configuration` `@EnableAsync` | 注册 `mesAsyncExecutor` 线程池（核心 2 / 最大 8 / 队列 200），专用于 MES 异步推送 |
| `JwtTokenProvider.java` | `@Component` | JWT 令牌生成（HMAC-SHA256）、解析、验证；提取 `username`、`userId`、`role` |
| `JwtAuthenticationFilter.java` | `@Component` `OncePerRequestFilter` | 从 `Authorization: Bearer` 头提取并验证 JWT，填充 `SecurityContext` |
| `SecurityConfig.java` | `@Configuration` `@EnableWebSecurity` | Security 过滤链、CORS 配置、路由权限规则、`BCryptPasswordEncoder` 注册 |
| `MybatisPlusConfig.java` | `@Configuration` `@MapperScan` | 注册 PostgreSQL 分页插件；扫描路径：`com.xiaobai.workorder.modules.*.repository` |
| `MybatisPlusMetaHandler.java` | `@Component` `MetaObjectHandler` | 自动填充：Insert 时注入 `createdAt`、`updatedAt`；Update 时更新 `updatedAt` |
| `RedisConfig.java` | `@Configuration` | 注册 `RedisTemplate<String, Object>`，使用 `StringRedisSerializer` + `GenericJackson2JsonRedisSerializer` |
| `OpenApiConfig.java` | `@Configuration` | Swagger UI 配置，注册 `bearer-jwt` 安全方案 |

### 2.2 common/ — 通用基础组件

#### response/

| 文件 | 说明 |
|---|---|
| `ApiResponse<T>` | 统一响应包装。字段：`code`、`message`、`data`、`timestamp`。静态工厂：`success(data)`、`success(msg, data)`、`error(code, msg)` |
| `PageResponse<T>` | 分页响应包装。字段：`records`、`total`、`current`、`size`、`pages`。静态工厂：`of(records, total, current, size)` |

#### exception/

| 文件 | 说明 |
|---|---|
| `BusinessException` | 运行时业务异常，携带 HTTP `code`（默认 400）。构造：`(message)` 或 `(code, message)` |
| `GlobalExceptionHandler` | `@RestControllerAdvice`。统一处理：`BusinessException`（400）、`MethodArgumentNotValidException`（400）、`BadCredentialsException`（401）、`AccessDeniedException`（403）、`Exception`（500） |

#### constant/

| 文件 | 常量值 |
|---|---|
| `WorkOrderStatus` | `NOT_STARTED`、`STARTED`、`REPORTED`、`INSPECT_PASSED`、`INSPECT_FAILED`、`COMPLETED` |
| `OperationType` | `PRODUCTION`、`INSPECTION`、`TRANSPORT`、`ANDON` |

#### util/

| 文件 | 说明 |
|---|---|
| `SecurityUtils` | `@Component`。`getCurrentUserId()`：从当前请求的 JWT 中提取 `userId`；`getCurrentUsername()`：从 `SecurityContext` 中获取用户名 |

---

## 3. 模块列表及功能说明

---

### 3.1 auth — 认证模块

**职责**：处理员工登录认证，签发 JWT 令牌。同时适用于手持设备端和 Web 管理端。

**数据库表**：无（依赖 `users` 表，由 `user` 模块管理）

```
modules/auth/
├── controller/
│   └── AuthController.java
├── service/
│   └── AuthService.java
└── dto/
    ├── LoginRequest.java
    └── LoginResponse.java
```

#### 主要文件说明

| 文件 | 类型 | 关键内容 |
|---|---|---|
| `LoginRequest` | DTO | `@NotBlank employeeNumber`、`@NotBlank password`、`deviceCode`（可选） |
| `LoginResponse` | DTO | `token`、`employeeNumber`、`realName`、`role`、`userId` |
| `AuthService` | `@Service` | `login(LoginRequest): LoginResponse`。流程：① 调用 `AuthenticationManager` 验证 → ② 查用户验证 `ACTIVE` 状态 → ③ 若传 `deviceCode` 则记录设备登录 → ④ 生成 JWT → ⑤ 返回 |
| `AuthController` | `@RestController` `/api/auth` | `POST /login` |

#### API 端点

| 方法 | 路径 | 认证 | 说明 |
|---|---|---|---|
| POST | `/api/auth/login` | 公开 | 管理端 / Web 端登录 |

#### 外部依赖

| 依赖 | 方向 | 说明 |
|---|---|---|
| `user.UserMapper` | 调用 | 查询用户信息 |
| `user.UserDetailsServiceImpl` | 注入到 Spring Security | 加载 `UserDetails` |
| `device.DeviceService` | 调用 | 记录设备登录时间 |
| `config.JwtTokenProvider` | 调用 | 生成 JWT |

---

### 3.2 user — 用户模块

**职责**：管理工人与管理员账号，提供 `UserDetailsService` 供 Spring Security 认证使用。

**数据库表**：`users`

```
modules/user/
├── controller/
│   └── AdminUserController.java
├── service/
│   ├── UserService.java
│   └── UserDetailsServiceImpl.java
├── repository/
│   └── UserMapper.java
├── entity/
│   └── User.java
└── dto/
    ├── CreateUserRequest.java
    └── UserDTO.java
```

#### 实体

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `BIGSERIAL` PK | 主键 |
| `employeeNumber` | `VARCHAR(50)` UNIQUE | 工号，作为登录名 |
| `username` | `VARCHAR(100)` | 显示名 |
| `password` | `VARCHAR(255)` | BCrypt 加密 |
| `realName` | `VARCHAR(100)` | 真实姓名 |
| `phone` / `email` | `VARCHAR` | 联系信息 |
| `role` | `VARCHAR(20)` | `ADMIN` / `WORKER` |
| `status` | `VARCHAR(20)` | `ACTIVE` / `DISABLED` |
| `deleted` | `SMALLINT` | 软删除标记（`@TableLogic`） |
| `createdAt` / `updatedAt` | `TIMESTAMP` | 自动填充 |

#### 主要文件说明

| 文件 | 类型 | 关键内容 |
|---|---|---|
| `UserMapper` | `@Mapper` | `findByEmployeeNumber(String): Optional<User>` |
| `UserDetailsServiceImpl` | `@Service` `UserDetailsService` | `loadUserByUsername`：按工号查用户，返回带 `ROLE_` 前缀的 `UserDetails` |
| `UserService` | `@Service` | `createUser`（BCrypt 加密密码）、`findByEmployeeNumber`、`findById`、`listUsers`（分页） |
| `AdminUserController` | `@RestController` `/api/admin/users` | `POST /`、`GET /` |

#### API 端点

| 方法 | 路径 | 角色 | 说明 |
|---|---|---|---|
| POST | `/api/admin/users` | ADMIN | 创建用户（工号唯一校验）|
| GET | `/api/admin/users` | ADMIN | 用户列表（支持 `page`、`size` 分页）|

#### 外部依赖

- **被调用**：`auth`、`team`、`statistics`、`mesintegration` 模块直接使用 `UserMapper`

---

### 3.3 team — 班组模块

**职责**：管理生产班组，维护班组成员关系，支持将工序指派给整个班组。

**数据库表**：`teams`、`team_members`

```
modules/team/
├── controller/
│   └── AdminTeamController.java
├── service/
│   └── TeamService.java
├── repository/
│   ├── TeamMapper.java
│   └── TeamMemberMapper.java
├── entity/
│   ├── Team.java
│   └── TeamMember.java
└── dto/
    ├── CreateTeamRequest.java
    └── TeamDTO.java
```

#### 实体

**`teams`**

| 字段 | 说明 |
|---|---|
| `teamCode` | UNIQUE，班组编号 |
| `teamName` | 班组名称 |
| `leaderId` | 班长 ID（FK → `users`）|
| `status` | `ACTIVE` |

**`team_members`**

| 字段 | 说明 |
|---|---|
| `teamId` + `userId` | 联合唯一约束，防止重复加入 |
| `joinedAt` | 加入时间 |

#### 主要文件说明

| 文件 | 类型 | 关键内容 |
|---|---|---|
| `TeamMapper` | `@Mapper` | `findByTeamCode(String): Optional<Team>` |
| `TeamMemberMapper` | `@Mapper` | `findByTeamId(Long)`、`findByUserId(Long)` |
| `TeamService` | `@Service` | `createTeam`（校验编号唯一，批量插入成员）、`addMembers`（去重后插入）、`listTeams`（含成员详情、班长姓名） |
| `AdminTeamController` | `@RestController` `/api/admin/teams` | `POST /`、`GET /`、`POST /{teamId}/members` |

#### API 端点

| 方法 | 路径 | 角色 | 说明 |
|---|---|---|---|
| POST | `/api/admin/teams` | ADMIN | 创建班组（可含初始成员）|
| GET | `/api/admin/teams` | ADMIN | 班组列表（含成员信息）|
| POST | `/api/admin/teams/{teamId}/members` | ADMIN | 追加班组成员 |

#### 外部依赖

- **调用**：`user.UserMapper`（查询成员信息用于响应）
- **被调用**：`mesintegration.MesInboundService`（按团队编号创建指派）

---

### 3.4 device — 设备模块

**职责**：管理工业手持设备注册信息，并作为**手持设备端 API 的聚合入口（BFF 层）**，将所有手持设备请求路由到对应业务服务。

**数据库表**：`devices`

```
modules/device/
├── controller/
│   └── DeviceController.java         ← 手持设备 BFF 控制器
├── service/
│   └── DeviceService.java
├── repository/
│   └── DeviceMapper.java
└── entity/
    └── Device.java
```

#### 实体

| 字段 | 说明 |
|---|---|
| `deviceCode` | UNIQUE，设备编号 |
| `deviceType` | 默认 `HANDHELD` |
| `macAddress` | MAC 地址 |
| `status` | `ACTIVE` / `DISABLED` |
| `lastLoginAt` | 最后登录时间 |
| `lastLoginUserId` | 最后登录用户（FK → `users`）|

#### 主要文件说明

| 文件 | 类型 | 关键内容 |
|---|---|---|
| `DeviceMapper` | `@Mapper` | `findByDeviceCode(String): Optional<Device>` |
| `DeviceService` | `@Service` | `recordLogin`（验证设备 ACTIVE，更新登录记录）、`findByCode` |
| `DeviceController` | `@RestController` `/api/device` | 10 个手持端 API（见下表） |

#### API 端点（手持设备 BFF）

| 方法 | 路径 | 角色 | 委托服务 | 说明 |
|---|---|---|---|---|
| POST | `/api/device/login` | 公开 | `AuthService` | 设备工人登录 |
| GET | `/api/device/work-orders` | WORKER/ADMIN | `WorkOrderService` | 获取当前用户已指派工单 |
| POST | `/api/device/start` | WORKER/ADMIN | `ReportService` | 开始工序（`operationId`） |
| POST | `/api/device/report` | WORKER/ADMIN | `ReportService` | 提交报工数量 |
| POST | `/api/device/scan/start` | WORKER/ADMIN | `WorkOrderService` + `ReportService` | 扫码开工：按条码定位工单，自动取最早未完成工序 |
| POST | `/api/device/scan/report` | WORKER/ADMIN | `WorkOrderService` + `ReportService` | 扫码报工：同上，自动报工 |
| POST | `/api/device/report/undo` | WORKER/ADMIN | `ReportService` | 撤销最近一次报工 |
| POST | `/api/device/call/andon` | WORKER/ADMIN | `CallService` | 触发 Andon 呼叫 |
| POST | `/api/device/call/inspection` | WORKER/ADMIN | `CallService` | 触发质检呼叫 |
| POST | `/api/device/call/transport` | WORKER/ADMIN | `CallService` | 触发运输呼叫 |

#### 外部依赖

| 依赖模块 | 调用服务/方法 |
|---|---|
| `auth` | `AuthService.login()` |
| `workorder` | `WorkOrderService.getAssignedWorkOrders()`、`getWorkOrderByBarcode()`、`getWorkOrderById()` |
| `report` | `ReportService.startWork()`、`reportWork()`、`undoReport()` |
| `call` | `CallService.createCall()` |
| `common.util` | `SecurityUtils.getCurrentUserId()` |

---

### 3.5 workorder — 工单模块

**职责**：工单的全生命周期管理，包括创建（含子工序）、指派、状态查询及工单详情聚合。

**数据库表**：`work_orders`

```
modules/workorder/
├── controller/
│   └── AdminWorkOrderController.java
├── service/
│   └── WorkOrderService.java
├── repository/
│   └── WorkOrderMapper.java
├── entity/
│   └── WorkOrder.java
└── dto/
    ├── CreateWorkOrderRequest.java
    ├── AssignWorkOrderRequest.java
    └── WorkOrderDTO.java
```

#### 实体

| 字段 | 说明 |
|---|---|
| `orderNumber` | UNIQUE，工单号 |
| `orderType` | `PRODUCTION` / `INSPECTION` / `TRANSPORT` / `ANDON` |
| `plannedQuantity` / `completedQuantity` | 计划量 / 完工量（`DECIMAL 10,2`）|
| `status` | 状态机：`NOT_STARTED` → `STARTED` → `REPORTED` → `INSPECT_PASSED` / `INSPECT_FAILED` |
| `priority` | 优先级（数值越大越优先）|
| `plannedStartTime` / `plannedEndTime` | 计划时间 |
| `actualStartTime` | 实际开工时间（首个工序开工时自动填充）|
| `workshop` / `productionLine` | 车间 / 线体 |

#### 主要文件说明

| 文件 | 类型 | 关键内容 |
|---|---|---|
| `WorkOrderMapper` | `@Mapper` | `findByOrderNumber`；`findByDirectUserId`（SQL JOIN：`operation_assignments.assignment_type='USER'`）；`findByTeamMemberId`（SQL JOIN：经 `team_members` 关联）|
| `WorkOrderService` | `@Service` | `createWorkOrder`（工序自动编号，格式 `WO-001-OP001`）；`assignWorkOrder`；`getAssignedWorkOrders`（合并个人 + 班组工单，去重，按优先级排序）；`listAllWorkOrders`（分页 + 状态筛选）；`getWorkOrderByBarcode`（条码 = 工单号）|
| `AdminWorkOrderController` | `@RestController` `/api/admin/work-orders` | `POST /`、`GET /`、`GET /{id}`、`POST /assign` |

#### API 端点

| 方法 | 路径 | 角色 | 说明 |
|---|---|---|---|
| POST | `/api/admin/work-orders` | ADMIN | 创建工单（含子工序批量创建）|
| GET | `/api/admin/work-orders` | ADMIN | 工单列表（`page`、`size`、`status` 筛选）|
| GET | `/api/admin/work-orders/{id}` | ADMIN | 工单详情（含工序列表）|
| POST | `/api/admin/work-orders/assign` | ADMIN | 工序指派到用户 / 班组 |

#### 外部依赖

| 依赖 | 说明 |
|---|---|
| `operation.OperationMapper` | 创建工序、查询工序列表（用于 DTO 聚合） |
| `operation.OperationAssignmentMapper` | 创建指派记录 |
| **被调用** | `device`、`report`、`inspection`、`statistics`、`mesintegration` 均依赖 `WorkOrderMapper` |

---

### 3.6 operation — 工序模块

**职责**：管理工序实体（`Operation`）和工序指派实体（`OperationAssignment`）。本模块**无独立 Controller 和 Service**，所有操作由 `workorder` 和 `report` 模块驱动。

**数据库表**：`operations`、`operation_assignments`

```
modules/operation/
├── repository/
│   ├── OperationMapper.java
│   └── OperationAssignmentMapper.java
└── entity/
    ├── Operation.java
    └── OperationAssignment.java
```

#### 实体

**`operations`**

| 字段 | 说明 |
|---|---|
| `workOrderId` | FK → `work_orders` |
| `operationNumber` | 格式：`{workOrderNumber}-OP{seq:3}` |
| `operationType` | `PRODUCTION` / `INSPECTION` / `TRANSPORT` / `ANDON` |
| `sequenceNumber` | 工序顺序（决定扫码执行顺序）|
| `plannedQuantity` / `completedQuantity` | 计划量 / 完工量 |
| `status` | `NOT_STARTED` → `STARTED` → `REPORTED` |
| `standardTime` / `actualTime` | 标准工时 / 实际工时（秒）|
| `stationCode` / `stationName` | 工位信息 |

**`operation_assignments`**

| 字段 | 说明 |
|---|---|
| `assignmentType` | `USER`（直接指派）或 `TEAM`（班组指派）|
| `userId` | 指派个人时填写 |
| `teamId` | 指派班组时填写 |

#### 主要文件说明

| 文件 | 类型 | 关键方法 |
|---|---|---|
| `OperationMapper` | `@Mapper` | `findByWorkOrderId(Long)`；`findEarliestUnfinishedByUserAndWorkOrder(userId, workOrderId)`（扫码开工用，取 `sequence_number` 最小的未完成工序，USER 类型）；`findEarliestUnfinishedByTeamUserAndWorkOrder(userId, workOrderId)`（TEAM 类型路径）|
| `OperationAssignmentMapper` | `@Mapper` | `findByOperationId(Long)` |

#### 外部依赖

- **被调用**：`workorder.WorkOrderService`、`report.ReportService`、`mesintegration.MesInboundService`

---

### 3.7 report — 报工模块

**职责**：记录工人完工数量，维护工序 / 工单完工量，并在事务提交后向 MES 发布事件。

**数据库表**：`report_records`

```
modules/report/
├── service/
│   └── ReportService.java
├── repository/
│   └── ReportRecordMapper.java
├── entity/
│   └── ReportRecord.java
└── dto/
    ├── ReportRequest.java
    └── UndoReportRequest.java
```

> **无独立 Controller**：API 入口在 `device.DeviceController`

#### 实体

| 字段 | 说明 |
|---|---|
| `operationId` / `workOrderId` | FK |
| `userId` / `deviceId` | 操作人 / 操作设备 |
| `reportedQuantity` / `qualifiedQuantity` / `defectQuantity` | 报工量 / 合格量 / 不合格量 |
| `reportTime` | 报工时间 |
| `isUndone` | 是否已撤销（布尔值）|
| `undoTime` / `undoReason` | 撤销时间 / 撤销原因 |

#### 主要文件说明

| 文件 | 类型 | 关键内容 |
|---|---|---|
| `ReportRecordMapper` | `@Mapper` | `findActiveByOperationId`（`is_undone=false`）；`findLatestByOperationIdAndUser`（撤销用，取最新记录）；`sumReportedQuantityByOperationId`（`@Select` SQL 聚合，排除撤销记录）|
| `ReportService` | `@Service` `@Transactional` | 见下方详细说明 |

#### ReportService 核心方法

| 方法 | 业务逻辑 | 发布事件 |
|---|---|---|
| `startWork(operationId, userId)` | 校验状态 `NOT_STARTED` → 更新为 `STARTED`；若工单首次开工则更新工单状态 | `WorkOrderStatusChangedEvent`（`NOT_STARTED → STARTED`）|
| `reportWork(request, userId, deviceId)` | 计算剩余量（`planned - alreadyReported`）；`reportedQty` 默认等于剩余量；校验不超量；INSERT `report_records`；更新工序完工量；若工序全部完成则更新工单为 `REPORTED` | `ReportRecordSavedEvent`（报工）；`WorkOrderStatusChangedEvent`（工单变为 REPORTED 时）|
| `undoReport(request, userId)` | 取最新非撤销记录标记 `isUndone=true`；重新计算完工量；回滚工序 / 工单状态 | 无 |
| `getReportHistory(operationId)` | 返回该工序所有未撤销报工记录 | — |

#### 外部依赖

| 依赖 | 说明 |
|---|---|
| `operation.OperationMapper` | 读写工序状态 |
| `workorder.WorkOrderMapper` | 读写工单状态 |
| `ApplicationEventPublisher` | 发布 `ReportRecordSavedEvent`、`WorkOrderStatusChangedEvent` |
| **被调用** | `device.DeviceController` |

---

### 3.8 inspection — 质检模块

**职责**：提交质检结果，驱动工单进入最终状态（`INSPECT_PASSED` / `INSPECT_FAILED`）。

**数据库表**：`inspection_records`

```
modules/inspection/
├── controller/
│   └── InspectionController.java
├── service/
│   └── InspectionService.java
├── repository/
│   └── InspectionRecordMapper.java
├── entity/
│   └── InspectionRecord.java
└── dto/
    └── InspectionRequest.java
```

#### 实体

| 字段 | 说明 |
|---|---|
| `workOrderId` / `operationId` | FK |
| `inspectorId` | 质检员（FK → `users`）|
| `inspectionType` | 固定为 `QUALITY` |
| `inspectionResult` | `PASSED` / `FAILED` |
| `inspectedQuantity` / `qualifiedQuantity` / `defectQuantity` | 质检量 / 合格量 / 不合格量 |
| `defectReason` | 不合格原因 |
| `status` | `NOT_INSPECTED` → `INSPECTED` |
| `inspectionTime` | 质检时间 |

#### 主要文件说明

| 文件 | 类型 | 关键内容 |
|---|---|---|
| `InspectionRecordMapper` | `@Mapper` | 继承 `BaseMapper`（无自定义方法）|
| `InspectionService` | `@Service` `@Transactional` | `submitInspection`：① 校验工单状态必须为 `REPORTED` → ② INSERT `inspection_records` → ③ 发布 `InspectionRecordSavedEvent` → ④ 根据结果更新工单状态 → ⑤ 发布 `WorkOrderStatusChangedEvent` |
| `InspectionController` | `@RestController` `/api/admin/inspections` | `POST /` |

#### API 端点

| 方法 | 路径 | 角色 | 说明 |
|---|---|---|---|
| POST | `/api/admin/inspections` | ADMIN | 提交质检结果（工单需处于 `REPORTED` 状态）|

#### 外部依赖

| 依赖 | 说明 |
|---|---|
| `workorder.WorkOrderMapper` | 读写工单状态 |
| `ApplicationEventPublisher` | 发布 `InspectionRecordSavedEvent`、`WorkOrderStatusChangedEvent` |

---

### 3.9 call — 呼叫模块

**职责**：记录车间现场的三类呼叫事件：Andon 异常、质检请求、物料运输请求。

**数据库表**：`call_records`

```
modules/call/
├── service/
│   └── CallService.java
├── repository/
│   └── CallRecordMapper.java
├── entity/
│   └── CallRecord.java
└── dto/
    └── CallRequest.java
```

> **无独立 Controller**：API 入口在 `device.DeviceController`（`/call/andon`、`/call/inspection`、`/call/transport`）

#### 实体

| 字段 | 说明 |
|---|---|
| `callType` | `ANDON` / `INSPECTION` / `TRANSPORT` |
| `callerId` / `handlerId` | 发起人 / 处理人（FK → `users`）|
| `status` | `NOT_HANDLED` → `HANDLING` → `HANDLED` |
| `callTime` / `handleTime` / `completeTime` | 各阶段时间戳 |
| `description` | 呼叫描述 |
| `handleResult` | 处理结果 |

#### 主要文件说明

| 文件 | 类型 | 关键方法 |
|---|---|---|
| `CallRecordMapper` | `@Mapper` | 继承 `BaseMapper`（无自定义方法）|
| `CallService` | `@Service` | `createCall`（校验工单存在，INSERT，状态 `NOT_HANDLED`）；`handleCall`（更新为 `HANDLING`）；`completeCall`（更新为 `HANDLED`，记录处理结果）|

#### 外部依赖

- **调用**：`workorder.WorkOrderMapper`（校验工单存在）
- **被调用**：`device.DeviceController`

---

### 3.10 statistics — 统计模块

**职责**：为管理控制台提供生产看板数据，跨模块聚合工单、报工、用户信息。

**数据库表**：无（只读，跨 `work_orders`、`report_records`、`users` 查询）

```
modules/statistics/
├── controller/
│   └── StatisticsController.java
├── service/
│   └── StatisticsService.java
└── dto/
    └── StatisticsDTO.java
```

#### 主要文件说明

| 文件 | 类型 | 关键内容 |
|---|---|---|
| `StatisticsDTO` | DTO | `totalWorkOrders`、`notStartedCount`、`startedCount`、`reportedCount`、`completedCount`、`overallCompletionRate`（`BigDecimal`）、`typeStats: List<WorkOrderTypeStat>`、`workerStats: List<WorkerStat>` |
| `StatisticsService` | `@Service` | `getDashboardStats()`：全量拉取工单列表 → 内存计算各状态数量及完工率 → 按 `orderType` 分组统计 → 拉取报工记录按 `userId` 分组统计工人产出 |
| `StatisticsController` | `@RestController` `/api/admin/statistics` | `GET /dashboard` |

#### API 端点

| 方法 | 路径 | 角色 | 说明 |
|---|---|---|---|
| GET | `/api/admin/statistics/dashboard` | ADMIN | 生产看板统计数据 |

#### 外部依赖

| 依赖 | 说明 |
|---|---|
| `workorder.WorkOrderMapper` | 全量查询工单 |
| `report.ReportRecordMapper` | 全量查询报工记录 |
| `user.UserMapper` | 查询工人姓名、工号 |

---

### 3.11 mesintegration — MES 集成模块

**职责**：实现本系统与上游 MES 系统的双向数据集成。通过 Webhook 接收工单导入（入站），并在事务提交后异步推送报工、质检、状态变更（出站）。提供同步日志的查询和统计接口。

**数据库表**：`mes_sync_logs`、`mes_order_mappings`

**功能开关**：通过 `app.mes.integration.enabled=true/false` 控制，禁用时 `MesApiClient`、`MesClientConfig`、`MesRetryScheduler` 不加载，对主业务零影响。

```
modules/mesintegration/
├── controller/
│   ├── MesWebhookController.java          # 接收 MES 推入
│   └── AdminMesIntegrationController.java # 查询同步日志
├── service/
│   ├── MesInboundService.java             # 入站：导入工单
│   ├── MesOutboundService.java            # 出站：推送到 MES
│   ├── MesSyncLogService.java             # 同步日志持久化
│   └── MesSyncQueryService.java           # 日志查询统计
├── event/
│   ├── ReportRecordSavedEvent.java        # 报工完成事件
│   ├── WorkOrderStatusChangedEvent.java   # 工单状态变更事件
│   ├── InspectionRecordSavedEvent.java    # 质检完成事件
│   └── MesEventListener.java             # 事件监听器（异步）
├── client/
│   └── MesApiClient.java                 # MES HTTP 客户端
├── scheduler/
│   └── MesRetryScheduler.java            # 失败重试定时任务
├── config/
│   └── MesClientConfig.java              # RestTemplate 超时配置
├── repository/
│   ├── MesSyncLogMapper.java
│   └── MesOrderMappingMapper.java
├── entity/
│   ├── MesSyncLog.java
│   └── MesOrderMapping.java
├── dto/
│   ├── MesWorkOrderImportRequest.java     # 入站 DTO
│   ├── MesImportResponse.java
│   ├── MesReportPushPayload.java          # 出站 DTO
│   ├── MesStatusPushPayload.java
│   ├── MesInspectionPushPayload.java
│   ├── MesSyncLogDTO.java                 # 查询响应
│   └── MesSyncStatsDTO.java
└── constant/
    ├── MesSyncType.java                   # WORK_ORDER_IMPORT, REPORT_PUSH...
    ├── MesSyncStatus.java                 # PENDING, SUCCESS, FAILED, RETRYING
    └── MesSyncDirection.java              # INBOUND, OUTBOUND
```

#### 实体

**`mes_sync_logs`**

| 字段 | 说明 |
|---|---|
| `syncType` | `WORK_ORDER_IMPORT` / `REPORT_PUSH` / `STATUS_PUSH` / `INSPECTION_PUSH` |
| `direction` | `INBOUND` / `OUTBOUND` |
| `businessKey` | 业务标识（如报工记录 ID、工单号）|
| `payload` | 请求体序列化为 JSON（`TEXT`）|
| `responseBody` | MES 系统原始响应（`TEXT`）|
| `status` | `PENDING` → `SUCCESS` / `FAILED` / `RETRYING` |
| `retryCount` / `maxRetries` | 当前重试次数 / 最大重试次数（默认 3）|
| `errorMessage` | 失败原因 |

**`mes_order_mappings`**

| 字段 | 说明 |
|---|---|
| `localOrderId` / `localOrderNumber` | 本系统工单 |
| `mesOrderId` / `mesOrderNumber` | MES 系统工单 |
| `syncStatus` | `PENDING` / `SYNCED` / `FAILED` |

#### 主要文件说明

| 文件 | 类型 | 关键内容 |
|---|---|---|
| `MesSyncLogService` | `@Service` `@Transactional(REQUIRES_NEW)` | 使用独立事务保存日志，确保即使主事务回滚日志也不丢失 |
| `MesInboundService` | `@Service` `@Transactional` | `importWorkOrder`：幂等校验（`mesOrderId` 已存在则直接返回）→ 创建 `WorkOrder` + `Operations` + `OperationAssignments`（支持按工号 / 班组编号指派）→ 创建映射记录 |
| `MesOutboundService` | `@Service` | `pushReport` / `pushStatusChange` / `pushInspection`：从 `ObjectProvider<MesApiClient>` 获取（集成未启用则为 `null`，安全跳过）→ 构建 Payload → `createPending` 日志 → HTTP 推送 → `markSuccess` / `markFailed` |
| `MesEventListener` | `@Component` | `@TransactionalEventListener(AFTER_COMMIT)` + `@Async`：事务提交后在独立线程（`mesAsyncExecutor`）中触发推送 |
| `MesApiClient` | `@Component` `@ConditionalOnProperty(enabled=true)` | `pushReport`、`pushStatus`、`pushInspection`：调用 MES HTTP 接口，携带 `X-Api-Key` 请求头 |
| `MesRetryScheduler` | `@Component` `@ConditionalOnProperty(enabled=true)` | `@Scheduled(fixedDelay)` 每 5 分钟执行：查询 `FAILED/RETRYING` 且未超过最大重试次数的日志 → 按 `syncType` 反序列化 Payload 重新推送 |
| `MesSyncQueryService` | `@Service` | 分页查询日志、统计各状态数量 |

#### API 端点

| 方法 | 路径 | 角色 | 说明 |
|---|---|---|---|
| POST | `/api/mes/work-orders/import` | ADMIN | MES 推入工单（幂等）|
| GET | `/api/admin/mes-integration/stats` | ADMIN | 同步统计（各状态数量）|
| GET | `/api/admin/mes-integration/logs` | ADMIN | 同步日志列表（`syncType`、`status`、`direction` 筛选）|

#### 外部依赖

| 依赖 | 说明 |
|---|---|
| `workorder.WorkOrderMapper` | 创建工单（入站）、查询工单信息（出站）|
| `operation.OperationMapper` | 创建工序（入站）、查询工序信息（出站）|
| `operation.OperationAssignmentMapper` | 创建指派（入站）|
| `user.UserMapper` | 按工号查询用户 ID（入站），构建推送 Payload（出站）|
| `team.TeamMapper` | 按班组编号查询团队 ID（入站）|
| `report.ReportRecordMapper` | 构建报工推送 Payload |
| **被触发** | `report.ReportService`（发布 `ReportRecordSavedEvent`、`WorkOrderStatusChangedEvent`）；`inspection.InspectionService`（发布 `InspectionRecordSavedEvent`、`WorkOrderStatusChangedEvent`）|

---

## 4. 模块间依赖关系

### 4.1 调用关系矩阵

表头为**被调用方**，行首为**调用方**。✓ = 直接调用（注入其 Mapper 或 Service）。

|  | auth | user | team | device | workorder | operation | report | inspection | call | statistics | mesintegration |
|---|---|---|---|---|---|---|---|---|---|---|---|
| **auth** | — | ✓ | — | ✓ | — | — | — | — | — | — | — |
| **device** | ✓ | — | — | — | ✓ | — | ✓ | — | ✓ | — | — |
| **workorder** | — | — | — | — | — | ✓ | — | — | — | — | — |
| **report** | — | — | — | — | ✓ | ✓ | — | — | — | — | ✓（事件）|
| **inspection** | — | — | — | — | ✓ | — | — | — | — | — | ✓（事件）|
| **call** | — | — | — | — | ✓ | — | — | — | — | — | — |
| **statistics** | — | ✓ | — | — | ✓ | — | ✓ | — | — | — | — |
| **mesintegration** | — | ✓ | ✓ | — | ✓ | ✓ | ✓ | — | — | — | — |

### 4.2 依赖层次图

```
                    ┌─────────────────────────────────────────┐
                    │          External Clients                │
                    │  Handheld Device │ Mobile App │ Web UI  │
                    └───────┬─────────────────────────────────┘
                            │
               ┌────────────┴────────────┐
               ▼                         ▼
      ┌────────────────┐       ┌──────────────────────┐
      │ DeviceController│       │ Admin Controllers     │
      │  /api/device   │       │ /api/admin/**         │
      └────────┬───────┘       └──────────┬────────────┘
               │                          │
    ┌──────────┼──────────────────────────┼──────────┐
    ▼          ▼          ▼               ▼          ▼
 AuthService WorkOrder ReportService InspectionService CallService
    │        Service       │               │
    │           │          │               │
    ▼           ▼          ▼               ▼
 UserMapper OperationMapper + OperationAssignmentMapper
                      │               │
                      └───────────────┘
                              │ ApplicationEvent (AFTER_COMMIT)
                              ▼
                    ┌──────────────────────┐
                    │   MesEventListener   │  ← @Async, @TransactionalEventListener
                    └──────────┬───────────┘
                               │
                               ▼
                    ┌──────────────────────┐
                    │  MesOutboundService  │
                    │  + MesSyncLogService │
                    └──────────┬───────────┘
                               │
                               ▼
                       MesApiClient (HTTP)
                               │
                               ▼
                    ┌──────────────────────┐
                    │   External MES System │
                    └──────────────────────┘

StatisticsService ──reads──► WorkOrderMapper
                  ──reads──► ReportRecordMapper
                  ──reads──► UserMapper
```

### 4.3 事件依赖（异步解耦）

| 事件类 | 发布方 | 消费方 | 触发时机 | 最终动作 |
|---|---|---|---|---|
| `ReportRecordSavedEvent` | `ReportService.reportWork()` | `MesEventListener.onReportSaved()` | 报工事务 COMMIT 后 | `MesOutboundService.pushReport()` |
| `WorkOrderStatusChangedEvent` | `ReportService.updateWorkOrderStatusOnStart()`、`updateWorkOrderOnReport()` / `InspectionService.submitInspection()` | `MesEventListener.onWorkOrderStatusChanged()` | 状态变更事务 COMMIT 后 | `MesOutboundService.pushStatusChange()` |
| `InspectionRecordSavedEvent` | `InspectionService.submitInspection()` | `MesEventListener.onInspectionSaved()` | 质检事务 COMMIT 后 | `MesOutboundService.pushInspection()` |

---

## 5. 数据库表归属总览

| 数据库表 | 归属模块 | 主要实体类 | 关键约束 |
|---|---|---|---|
| `users` | user | `User` | `UNIQUE(employee_number)` |
| `teams` | team | `Team` | `UNIQUE(team_code)` |
| `team_members` | team | `TeamMember` | `UNIQUE(team_id, user_id)` |
| `devices` | device | `Device` | `UNIQUE(device_code)` |
| `work_orders` | workorder | `WorkOrder` | `UNIQUE(order_number)` |
| `operations` | operation | `Operation` | `UNIQUE(work_order_id, operation_number)` |
| `operation_assignments` | operation | `OperationAssignment` | — |
| `report_records` | report | `ReportRecord` | `is_undone` 软撤销 |
| `inspection_records` | inspection | `InspectionRecord` | FK → `work_orders`, `users` |
| `call_records` | call | `CallRecord` | FK → `work_orders`, `users`（×2）|
| `mes_sync_logs` | mesintegration | `MesSyncLog` | 独立事务（`REQUIRES_NEW`）|
| `mes_order_mappings` | mesintegration | `MesOrderMapping` | FK → `work_orders` |

**所有表**均包含：
- `deleted SMALLINT DEFAULT 0`（软删除，`@TableLogic` 自动过滤）
- `created_at TIMESTAMP`（自动填充，`FieldFill.INSERT`）
- `updated_at TIMESTAMP`（自动填充，`FieldFill.INSERT_UPDATE`）

---

## 6. 事件流转总览

```
工人扫码 / 点击报工
        │
        ▼
DeviceController.POST /report
        │
        ▼
ReportService.reportWork()          ← @Transactional
 ├─ INSERT report_records
 ├─ UPDATE operations (completedQty, status)
 ├─ UPDATE work_orders (completedQty, status)
 ├─ eventPublisher.publish(ReportRecordSavedEvent)
 └─ eventPublisher.publish(WorkOrderStatusChangedEvent)  ← 仅工单状态变更时
        │
        ▼ Transaction COMMIT
        │
        ├──────────────────────────────────────────┐
        ▼                                          ▼
MesEventListener.onReportSaved()    MesEventListener.onWorkOrderStatusChanged()
  @Async (mes-async-*)                @Async (mes-async-*)
        │                                          │
        ▼                                          ▼
MesOutboundService.pushReport()     MesOutboundService.pushStatusChange()
 ├─ MesSyncLogService.createPending()  (独立事务)
 ├─ MesApiClient.pushReport()
 └─ markSuccess() / markFailed()
        │
        ▼ (若失败)
MesRetryScheduler
 @Scheduled(every 5 min)
 └─ 扫描 FAILED/RETRYING 记录 → 重新推送
```

```
质检员提交质检
        │
        ▼
InspectionController.POST /
        │
        ▼
InspectionService.submitInspection()    ← @Transactional
 ├─ INSERT inspection_records
 ├─ UPDATE work_orders (status → INSPECT_PASSED / INSPECT_FAILED)
 ├─ eventPublisher.publish(InspectionRecordSavedEvent)
 └─ eventPublisher.publish(WorkOrderStatusChangedEvent)
        │
        ▼ Transaction COMMIT
        ├── MesEventListener.onInspectionSaved()  → pushInspection()
        └── MesEventListener.onWorkOrderStatusChanged() → pushStatusChange()
```

---

## 7. 文件索引

### 全量文件清单（按模块）

#### config/ — 8 个文件

| 文件 | 注解 |
|---|---|
| `AsyncConfig.java` | `@Configuration` `@EnableAsync` |
| `JwtTokenProvider.java` | `@Component` |
| `JwtAuthenticationFilter.java` | `@Component` |
| `SecurityConfig.java` | `@Configuration` `@EnableWebSecurity` `@EnableMethodSecurity` |
| `MybatisPlusConfig.java` | `@Configuration` `@MapperScan` |
| `MybatisPlusMetaHandler.java` | `@Component` |
| `RedisConfig.java` | `@Configuration` |
| `OpenApiConfig.java` | `@Configuration` |

#### common/ — 7 个文件

| 文件 | 类型 |
|---|---|
| `response/ApiResponse.java` | 泛型响应包装 |
| `response/PageResponse.java` | 分页响应包装 |
| `exception/BusinessException.java` | 业务异常 |
| `exception/GlobalExceptionHandler.java` | `@RestControllerAdvice` |
| `constant/WorkOrderStatus.java` | 工单状态常量 |
| `constant/OperationType.java` | 工序类型常量 |
| `util/SecurityUtils.java` | `@Component` JWT 工具 |

#### modules/ — 64 个文件（按模块）

| 模块 | 文件数 | 含控制器 | 含独立 Service |
|---|---|---|---|
| auth | 3 | ✓ | ✓ |
| user | 5 | ✓ | ✓ |
| team | 6 | ✓ | ✓ |
| device | 4 | ✓（BFF）| ✓ |
| workorder | 5 | ✓ | ✓ |
| operation | 4 | ✗ | ✗ |
| report | 4 | ✗ | ✓ |
| inspection | 5 | ✓ | ✓ |
| call | 4 | ✗ | ✓ |
| statistics | 3 | ✓ | ✓ |
| mesintegration | 23 | ✓（2个）| ✓（4个）|

**mesintegration 模块详细文件列表：**

| 层 | 文件 |
|---|---|
| entity | `MesSyncLog.java`、`MesOrderMapping.java` |
| repository | `MesSyncLogMapper.java`、`MesOrderMappingMapper.java` |
| dto | `MesWorkOrderImportRequest.java`、`MesImportResponse.java`、`MesReportPushPayload.java`、`MesStatusPushPayload.java`、`MesInspectionPushPayload.java`、`MesSyncLogDTO.java`、`MesSyncStatsDTO.java` |
| service | `MesInboundService.java`、`MesOutboundService.java`、`MesSyncLogService.java`、`MesSyncQueryService.java` |
| event | `ReportRecordSavedEvent.java`、`WorkOrderStatusChangedEvent.java`、`InspectionRecordSavedEvent.java`、`MesEventListener.java` |
| client | `MesApiClient.java` |
| scheduler | `MesRetryScheduler.java` |
| config | `MesClientConfig.java` |
| constant | `MesSyncType.java`、`MesSyncStatus.java`、`MesSyncDirection.java` |

---

> **统计**：全项目共 **80 个** Java 文件（含启动类），**11 个**业务模块，**12 张**数据库表，**27 个** REST 端点。
>
> **API 文档**：启动后访问 [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

# mesintegration 模块 — 类与函数文档

## 1. 模块概述

`mesintegration`（MES 集成模块）负责本工单系统与上游 MES（制造执行系统）之间的双向数据同步。

**核心职责：**

| 方向 | 描述 |
|------|------|
| **入站（Inbound）** | 接收 MES 通过 Webhook 推送的工单数据，幂等导入到本地数据库，建立 MES 订单与本地工单的 ID 映射关系 |
| **出站（Outbound）** | 在本地业务事件（报工记录保存、工单状态变更、质检记录保存）发生后，将相关数据异步推送至 MES 系统，并对失败推送进行有界重试 |
| **审计日志** | 对每次同步操作（成功或失败）均记录详细的同步日志，支持运营监控与问题排查 |
| **条件化激活** | 通过 `@ConditionalOnProperty(name = "app.mes.integration.enabled", havingValue = "true")` 控制，集成功能可整体开关，不影响核心业务模块启动 |

**关键技术设计：**

| 技术 | 用途 |
|------|------|
| `@TransactionalEventListener(AFTER_COMMIT)` | 确保业务事务提交后才触发 MES 推送 |
| `@Async` | 推送操作异步执行，不阻塞/回滚业务事务 |
| `Propagation.REQUIRES_NEW` | 同步日志使用独立事务，保证日志始终落库 |
| `ObjectProvider<MesApiClient>` | 优雅处理集成禁用时 Bean 缺失问题 |
| `@ConditionalOnProperty` | 条件化 Bean，支持按配置开关整个集成 |

模块包结构：

```
modules/mesintegration/
├── client/       # MesApiClient（HTTP 外发客户端）
├── config/       # MesClientConfig（RestTemplate 配置）
├── constant/     # MesSyncType、MesSyncStatus、MesSyncDirection
├── controller/   # MesWebhookController、AdminMesIntegrationController
├── dto/          # 7 个 DTO 类
├── entity/       # MesSyncLog、MesOrderMapping
├── event/        # 3 个事件类 + MesEventListener
├── repository/   # MesSyncLogMapper、MesOrderMappingMapper
├── scheduler/    # MesRetryScheduler
└── service/      # 4 个 Service 类
```

---

## 2. 核心类列表

### 常量类

| 类名 | 所在文件 | 职责描述 |
|------|---------|---------|
| `MesSyncType` | `constant/MesSyncType.java` | 同步类型常量：`REPORT_PUSH`、`STATUS_PUSH`、`INSPECTION_PUSH`、`ORDER_IMPORT` |
| `MesSyncStatus` | `constant/MesSyncStatus.java` | 同步状态常量：`PENDING`、`SUCCESS`、`FAILED`、`RETRYING` |
| `MesSyncDirection` | `constant/MesSyncDirection.java` | 同步方向常量：`INBOUND`（入站）、`OUTBOUND`（出站） |

### 实体类

| 类名 | 所在文件 | 职责描述 |
|------|---------|---------|
| `MesSyncLog` | `entity/MesSyncLog.java` | 同步审计日志实体，映射 `mes_sync_logs` 表，记录每次同步的类型、状态、载体、重试次数等 |
| `MesOrderMapping` | `entity/MesOrderMapping.java` | MES 与本地工单 ID 映射实体，映射 `mes_order_mappings` 表 |

### DTO 类

| 类名 | 所在文件 | 职责描述 |
|------|---------|---------|
| `MesWorkOrderImportRequest` | `dto/MesWorkOrderImportRequest.java` | MES 推送工单导入的 Webhook 请求体 DTO |
| `MesImportResponse` | `dto/MesImportResponse.java` | 工单导入结果响应 DTO |
| `MesReportPushPayload` | `dto/MesReportPushPayload.java` | 向 MES 推送报工记录的数据载体 |
| `MesStatusPushPayload` | `dto/MesStatusPushPayload.java` | 向 MES 推送工单状态变更的数据载体 |
| `MesInspectionPushPayload` | `dto/MesInspectionPushPayload.java` | 向 MES 推送质检记录的数据载体 |
| `MesSyncLogDTO` | `dto/MesSyncLogDTO.java` | 同步日志展示 DTO（管理端 API 返回） |
| `MesSyncStatsDTO` | `dto/MesSyncStatsDTO.java` | 同步统计汇总 DTO（管理端概览面板） |

### 配置/客户端类

| 类名 | 所在文件 | 职责描述 |
|------|---------|---------|
| `MesClientConfig` | `config/MesClientConfig.java` | 条件化配置类，注册专用 `RestTemplate` Bean，配置连接/读取超时 |
| `MesApiClient` | `client/MesApiClient.java` | HTTP 客户端，封装三类外发推送请求 |

### 服务类

| 类名 | 所在文件 | 职责描述 |
|------|---------|---------|
| `MesSyncLogService` | `service/MesSyncLogService.java` | 同步日志持久化服务，全部方法使用 `REQUIRES_NEW` 独立事务 |
| `MesInboundService` | `service/MesInboundService.java` | 入站服务，处理 MES 推送的工单导入（含幂等保护） |
| `MesOutboundService` | `service/MesOutboundService.java` | 出站服务，编排三类向 MES 的数据推送逻辑 |
| `MesSyncQueryService` | `service/MesSyncQueryService.java` | 查询侧服务，为管理端提供分页日志列表和统计数据 |

### 事件类

| 类名 | 所在文件 | 职责描述 |
|------|---------|---------|
| `ReportRecordSavedEvent` | `event/ReportRecordSavedEvent.java` | 领域事件：报工记录已持久化（由 ReportService 发布） |
| `WorkOrderStatusChangedEvent` | `event/WorkOrderStatusChangedEvent.java` | 领域事件：工单状态已变更（由 ReportService/InspectionService 发布） |
| `InspectionRecordSavedEvent` | `event/InspectionRecordSavedEvent.java` | 领域事件：质检记录已持久化（由 InspectionService 发布） |
| `MesEventListener` | `event/MesEventListener.java` | 领域事件监听器，将三类事件异步路由到 MesOutboundService |

### 调度/控制器类

| 类名 | 所在文件 | 职责描述 |
|------|---------|---------|
| `MesRetryScheduler` | `scheduler/MesRetryScheduler.java` | 定时重试调度器，每 5 分钟扫描并重新推送失败记录 |
| `MesWebhookController` | `controller/MesWebhookController.java` | Webhook 入站端点，供 MES 系统调用推送工单数据 |
| `AdminMesIntegrationController` | `controller/AdminMesIntegrationController.java` | 管理端监控接口，提供同步统计和日志查询 |

---

## 3. 类详细说明

### 3.1 MesEventListener

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/mesintegration/event/MesEventListener.java`
**类型：** Event Listener（Spring 事件监听器）
**职责：** 监听三类领域事件，在各自业务事务提交后异步触发 MES 推送，实现业务模块与 MES 集成的完全解耦。

#### 方法列表

| 方法名 | 返回类型 | 触发事件 | 功能描述 |
|--------|---------|---------|---------|
| `onReportSaved` | `void` | `ReportRecordSavedEvent` | 报工记录保存后，异步推送报工数据到 MES |
| `onWorkOrderStatusChanged` | `void` | `WorkOrderStatusChangedEvent` | 工单状态变更后，异步推送状态变更到 MES |
| `onInspectionSaved` | `void` | `InspectionRecordSavedEvent` | 质检记录保存后，异步推送质检数据到 MES |

#### 方法详情

##### onReportSaved(ReportRecordSavedEvent event)
- **功能：** 接收报工记录保存事件，调用 `mesOutboundService.pushReport(event.getReportRecordId())`。
- **注解：** `@Async("mesAsyncExecutor")` + `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`
- **参数：**
  - `event` (ReportRecordSavedEvent)：含 `reportRecordId` 和 `workOrderId`
- **备注：** `AFTER_COMMIT` 保证仅在业务事务成功提交后执行，避免 MES 查询到未提交的脏数据。

##### onWorkOrderStatusChanged(WorkOrderStatusChangedEvent event)
- **功能：** 接收工单状态变更事件，调用 `mesOutboundService.pushStatusChange(workOrderId, previousStatus, currentStatus, changedBy)`。
- **注解：** `@Async("mesAsyncExecutor")` + `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`

##### onInspectionSaved(InspectionRecordSavedEvent event)
- **功能：** 接收质检记录保存事件，调用 `mesOutboundService.pushInspection(event.getInspectionRecordId())`。
- **注解：** `@Async("mesAsyncExecutor")` + `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`

---

### 3.2 MesSyncLogService

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/mesintegration/service/MesSyncLogService.java`
**类型：** Service
**职责：** 同步日志持久化服务。**所有方法均使用 `@Transactional(propagation = Propagation.REQUIRES_NEW)`**，在独立事务中执行，确保日志记录无论外层事务是否回滚都能成功落库。

#### 方法列表

| 方法名 | 返回类型 | 功能描述 |
|--------|---------|---------|
| `createPending` | `MesSyncLog` | 创建状态为 `PENDING` 的同步日志记录 |
| `markSuccess` | `void` | 将日志状态更新为 `SUCCESS`，记录同步完成时间 |
| `markFailed` | `void` | 将日志状态更新为 `FAILED` 或 `RETRYING`，递增重试计数 |

#### 方法详情

##### createPending(String syncType, String direction, Long referenceId, String payload)
- **功能：** 在推送操作开始前创建一条 `PENDING` 状态的日志记录，作为操作的审计桩。
- **参数：**
  - `syncType` (String)：同步类型（`MesSyncType` 常量值）
  - `direction` (String)：同步方向（`MesSyncDirection` 常量值）
  - `referenceId` (Long)：关联业务记录 ID（如报工记录 ID）
  - `payload` (String)：序列化后的推送载体 JSON
- **返回值：** `MesSyncLog` — 已持久化的日志记录（含自增 ID）
- **备注：** `REQUIRES_NEW` 事务，不受外层事务影响。

##### markSuccess(Long logId)
- **功能：** 推送成功后将日志状态更新为 `SUCCESS`，填写 `syncedAt`。
- **参数：**
  - `logId` (Long)：日志记录主键 ID
- **备注：** `REQUIRES_NEW` 事务。

##### markFailed(Long logId, String errorMessage, int maxRetries)
- **功能：** 推送失败时递增 `retryCount`，根据是否达到最大重试次数决定将状态置为 `FAILED` 或 `RETRYING`。
- **参数：**
  - `logId` (Long)：日志记录主键 ID
  - `errorMessage` (String)：失败原因信息
  - `maxRetries` (int)：最大重试次数（来自配置文件）
- **备注：** `REQUIRES_NEW` 事务；`retryCount >= maxRetries` 时状态为 `FAILED`，否则为 `RETRYING`。

---

### 3.3 MesInboundService

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/mesintegration/service/MesInboundService.java`
**类型：** Service
**职责：** MES 入站业务逻辑，处理 MES 推送工单数据的幂等导入，完成工单、工序、派工记录及映射关系的完整创建。

#### 方法列表

| 方法名 | 返回类型 | 功能描述 |
|--------|---------|---------|
| `importWorkOrder` | `MesImportResponse` | 幂等导入 MES 工单，已存在则返回现有映射，否则创建新工单 |

#### 方法详情

##### importWorkOrder(MesWorkOrderImportRequest request)
- **功能：** 幂等导入 MES 工单。先通过 `mesOrderId` 检查映射关系是否已存在（幂等保护），若已存在直接返回现有映射；否则创建本地工单及工序、建立派工关系、写入 `MesOrderMapping` 记录。
- **参数：**
  - `request` (MesWorkOrderImportRequest)：含 `mesOrderId`、工单基本信息、工序列表、人员/团队分配信息
- **返回值：** `MesImportResponse` — 包含本地工单 ID、是否为重复导入的标志
- **备注：**
  - 幂等键：`mesOrderId`（MES 系统侧的工单唯一标识）
  - 工序编号生成规则：`{orderNumber}-OP{三位序号}`
  - 派工支持 USER 和 TEAM 两种类型，通过 `assignedEmployeeNumbers` 和 `assignedTeamCodes` 参数传入
  - `MesOrderMapping.syncStatus` 初始为 `SUCCESS`，`lastSyncedAt` 为当前时间
  - 标注 `@Transactional`

---

### 3.4 MesOutboundService

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/mesintegration/service/MesOutboundService.java`
**类型：** Service
**职责：** MES 出站业务逻辑，编排三类向 MES 的数据推送操作：报工推送、状态推送、质检推送。使用 `ObjectProvider<MesApiClient>` 优雅处理集成禁用场景。

#### 方法列表

| 方法名 | 返回类型 | 功能描述 |
|--------|---------|---------|
| `pushReport` | `void` | 推送报工记录数据至 MES |
| `pushStatusChange` | `void` | 推送工单状态变更数据至 MES |
| `pushInspection` | `void` | 推送质检记录数据至 MES |

#### 方法详情

##### pushReport(Long reportRecordId)
- **功能：** 查询报工记录、关联工序、工单、用户和 MES 映射信息，构建 `MesReportPushPayload`，通过 `MesApiClient` 调用 MES 接口推送数据，使用 `MesSyncLogService` 记录推送结果。
- **参数：**
  - `reportRecordId` (Long)：报工记录主键 ID
- **备注：**
  - `MesApiClient` 通过 `ObjectProvider.getIfAvailable()` 获取，若 Bean 未注册（集成禁用）则方法直接返回（仅记录 debug 日志）
  - 推送前调用 `mesSyncLogService.createPending(...)` 创建日志桩
  - 推送成功调用 `markSuccess`，推送失败调用 `markFailed`
  - 若无 `MesOrderMapping`（工单未从 MES 导入），静默跳过

##### pushStatusChange(Long workOrderId, String previousStatus, String currentStatus, String changedBy)
- **功能：** 构建 `MesStatusPushPayload` 并推送工单状态变更到 MES。
- **参数：**
  - `workOrderId` (Long)：工单主键 ID
  - `previousStatus` (String)：变更前状态
  - `currentStatus` (String)：变更后状态
  - `changedBy` (String)：变更人标识
- **备注：** 与 `pushReport` 相同的日志记录和错误处理模式。

##### pushInspection(Long inspectionRecordId)
- **功能：** 查询质检记录，构建 `MesInspectionPushPayload` 并推送质检数据到 MES。
- **参数：**
  - `inspectionRecordId` (Long)：质检记录主键 ID

---

### 3.5 MesRetryScheduler

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/mesintegration/scheduler/MesRetryScheduler.java`
**类型：** Scheduler（定时调度器）
**职责：** 定时扫描状态为 `FAILED` 或 `RETRYING` 且重试次数未达上限的同步日志，按同步类型分发重试推送。

#### 注解

- `@ConditionalOnProperty(name = "app.mes.integration.enabled", havingValue = "true")` — 仅集成启用时激活
- `@EnableScheduling` — 启用定时任务

#### 方法列表

| 方法名 | 返回类型 | 功能描述 |
|--------|---------|---------|
| `retryFailedSyncs` | `void` | 扫描失败日志并重新推送 |

#### 方法详情

##### retryFailedSyncs()
- **功能：** 查询所有待重试的同步日志记录，按 `syncType` 使用 Java 21 switch 表达式分发到对应的推送方法。
- **触发机制：** `@Scheduled(fixedDelayString = "${app.mes.integration.retry-delay-ms:300000}")`，每 5 分钟执行一次（配置可覆盖）
- **备注：**
  ```java
  case MesSyncType.REPORT_PUSH ->
      client.pushReport(objectMapper.readValue(payload, MesReportPushPayload.class));
  case MesSyncType.STATUS_PUSH ->
      client.pushStatus(objectMapper.readValue(payload, MesStatusPushPayload.class));
  case MesSyncType.INSPECTION_PUSH ->
      client.pushInspection(objectMapper.readValue(payload, MesInspectionPushPayload.class));
  ```

---

### 3.6 MesApiClient

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/mesintegration/client/MesApiClient.java`
**类型：** Component（HTTP 客户端）
**职责：** 封装三类向 MES 系统的外发 HTTP 请求，使用专用 `RestTemplate`（含超时配置）。

**注解：** `@ConditionalOnProperty(name = "app.mes.integration.enabled", havingValue = "true")`

#### 方法列表

| 方法名 | 返回类型 | 功能描述 |
|--------|---------|---------|
| `pushReport(MesReportPushPayload)` | `void` | POST 报工数据到 MES 接口 |
| `pushStatus(MesStatusPushPayload)` | `void` | POST 工单状态变更数据到 MES 接口 |
| `pushInspection(MesInspectionPushPayload)` | `void` | POST 质检数据到 MES 接口 |

**备注：** 请求头包含 `X-Api-Key: {app.mes.integration.api-key}` 用于 MES 侧认证。

---

### 3.7 MesWebhookController

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/mesintegration/controller/MesWebhookController.java`
**类型：** Controller
**职责：** MES 系统 Webhook 入站端点，接收 MES 推送的工单数据并调用 `MesInboundService` 处理导入。

#### 方法列表

| 方法名 | HTTP 路径 | 功能描述 |
|--------|----------|---------|
| `importWorkOrder` | `POST /api/mes/work-orders/import` | 接收 MES 推送的工单数据，幂等导入本地系统 |

---

### 3.8 AdminMesIntegrationController

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/mesintegration/controller/AdminMesIntegrationController.java`
**类型：** Controller
**职责：** 管理端 MES 集成监控接口，提供同步日志查询、统计概览等监控功能。

#### 方法列表

| 方法名 | HTTP 路径 | 功能描述 |
|--------|----------|---------|
| `getSyncStats` | `GET /api/admin/mes-integration/stats` | 获取同步统计汇总（成功/失败/待重试数量） |
| `getSyncLogs` | `GET /api/admin/mes-integration/logs` | 分页查询同步日志列表，支持按状态过滤 |

---

### 3.9 事件类（ReportRecordSavedEvent / WorkOrderStatusChangedEvent / InspectionRecordSavedEvent）

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/mesintegration/event/`
**类型：** Spring ApplicationEvent 子类（领域事件）
**职责：** 承载跨模块事件通知的数据载体，由业务模块（report、inspection）发布，由 `MesEventListener` 消费。

#### ReportRecordSavedEvent 字段

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `reportRecordId` | `Long` | 已持久化的报工记录 ID |
| `workOrderId` | `Long` | 关联工单 ID |

#### WorkOrderStatusChangedEvent 字段

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `workOrderId` | `Long` | 工单 ID |
| `previousStatus` | `String` | 变更前状态 |
| `currentStatus` | `String` | 变更后状态 |
| `changedBy` | `String` | 变更人标识 |

#### InspectionRecordSavedEvent 字段

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `inspectionRecordId` | `Long` | 已持久化的质检记录 ID |
| `inspectionRecord` | `InspectionRecord` | 完整的质检记录实体对象 |

---

## 4. 模块内调用关系

### 4.1 出站事件驱动链路

```
业务模块（report / inspection）
    │  publishEvent(ReportRecordSavedEvent / WorkOrderStatusChangedEvent / InspectionRecordSavedEvent)
    ▼
Spring 事件总线
    │  事务提交后（AFTER_COMMIT）
    ▼
MesEventListener（@Async "mesAsyncExecutor" 线程池）
    │  onReportSaved / onWorkOrderStatusChanged / onInspectionSaved
    ▼
MesOutboundService
    │  pushReport / pushStatusChange / pushInspection
    ├─ MesSyncLogService.createPending()          [REQUIRES_NEW 独立事务]
    ├─ MesApiClient.pushReport/Status/Inspection   [HTTP POST to MES]
    └─ MesSyncLogService.markSuccess/markFailed() [REQUIRES_NEW 独立事务]
```

### 4.2 入站 Webhook 链路

```
MES 系统（HTTP 调用方）
    │  POST /api/mes/work-orders/import
    ▼
MesWebhookController.importWorkOrder()
    ▼
MesInboundService.importWorkOrder(request)
    ├─ MesOrderMappingMapper.findByMesOrderId()   [幂等检查]
    ├─ WorkOrderMapper.insert()                   [创建工单]
    ├─ OperationMapper.insert() × N               [创建工序]
    ├─ OperationAssignmentMapper.insert() × N     [创建派工]
    └─ MesOrderMappingMapper.insert()             [建立映射关系]
```

### 4.3 重试调度链路

```
@Scheduled（每5分钟）
    ▼
MesRetryScheduler.retryFailedSyncs()
    ├─ MesSyncLogMapper.findRetryable()           [查询待重试日志]
    └─ switch(syncType)
        ├─ REPORT_PUSH     → MesApiClient.pushReport()
        ├─ STATUS_PUSH     → MesApiClient.pushStatus()
        └─ INSPECTION_PUSH → MesApiClient.pushInspection()
```

---

## 5. 重要业务逻辑说明

### 5.1 事件驱动 + AFTER_COMMIT 保证数据一致性

业务事务中发布事件，`MesEventListener` 使用 `@TransactionalEventListener(phase = AFTER_COMMIT)` 监听，确保：
1. 业务数据已提交到数据库，MES 推送查询到的是最终一致的数据
2. MES 推送失败不会触发业务事务回滚（推送在独立异步线程中执行）
3. `mesAsyncExecutor` 线程池隔离 MES I/O，不占用业务 Worker 线程

### 5.2 REQUIRES_NEW 日志事务隔离

`MesSyncLogService` 所有方法均标注 `@Transactional(propagation = REQUIRES_NEW)`，确保同步日志在独立事务中提交：
- 即使 `MesOutboundService` 或外层业务方法抛出异常，日志记录仍然成功落库
- 适合"审计日志必须记录，不能因业务异常而丢失"的场景

### 5.3 幂等导入保护

`MesInboundService.importWorkOrder()` 在正式导入前先以 `mesOrderId` 查询 `mes_order_mappings` 表：
- 已存在映射 → 直接返回现有工单 ID，不重复创建（幂等）
- 不存在映射 → 执行完整的创建流程

这保证了 MES 因网络抖动重复推送同一工单时，系统不会产生重复工单。

### 5.4 ObjectProvider 实现条件化依赖

`MesOutboundService` 使用 `ObjectProvider<MesApiClient>` 而非直接注入 `MesApiClient`：

```java
MesApiClient client = mesApiClientProvider.getIfAvailable();
if (client == null) {
    log.debug("MES integration disabled, skipping push");
    return;
}
```

当 `app.mes.integration.enabled=false` 时，`MesApiClient` Bean 不注册，`getIfAvailable()` 返回 null，方法静默返回，不影响正常业务流程。

### 5.5 有界重试机制

重试调度器 `MesRetryScheduler` 每 5 分钟（可配置）扫描一次失败日志：
- `retryCount < maxRetries` 时状态为 `RETRYING`，继续参与重试
- `retryCount >= maxRetries` 时状态置为 `FAILED`，不再重试，等待人工介入
- `maxRetries` 通过 `@Value("${app.mes.integration.max-retries:3}")` 从配置文件读取

### 5.6 安全配置

`SecurityConfig` 中为 MES Webhook 端点配置了 ADMIN 角色保护：

```java
.requestMatchers("/api/mes/**").hasRole("ADMIN")
```

MES 系统需使用具有 ADMIN 权限的账号的 JWT Token 调用 Webhook，防止非授权数据注入。

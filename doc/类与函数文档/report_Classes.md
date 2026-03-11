# report 模块 — 类与函数文档

## 1. 模块概述

report（报工）模块负责工单生产过程中的报工全生命周期管理，涵盖开工登记、数量报工、撤销报工及报工历史查询等核心业务。模块通过工序（Operation）与工单（WorkOrder）的状态联动，实现生产进度的实时追踪，并通过 Spring 应用事件机制将报工结果异步推送至 MES 系统。

模块包结构如下：

```
modules/report/
├── dto/          # 请求体 DTO（ReportRequest、UndoReportRequest）
├── entity/       # 数据库实体（ReportRecord）
├── repository/   # 数据访问层（ReportRecordMapper）
└── service/      # 业务逻辑层（ReportService）
```

---

## 2. 核心类列表

| 类名 | 所在文件 | 职责描述 |
|------|---------|---------|
| `ReportRequest` | `dto/ReportRequest.java` | 封装报工请求参数（操作 ID、报工数量、合格数量、不良数量、设备编码、备注） |
| `UndoReportRequest` | `dto/UndoReportRequest.java` | 封装撤销报工请求参数（操作 ID、撤销原因） |
| `ReportRecord` | `entity/ReportRecord.java` | 报工记录持久化实体，映射数据库表 `report_records` |
| `ReportRecordMapper` | `repository/ReportRecordMapper.java` | 继承 MyBatis-Plus BaseMapper，提供报工记录 CRUD 及业务查询方法 |
| `ReportService` | `service/ReportService.java` | 报工核心业务服务，处理开工、报工、撤销、历史查询，并驱动操作与工单状态流转 |

---

## 3. 类详细说明

### 3.1 ReportRequest

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/report/dto/ReportRequest.java`
**类型：** DTO
**职责：** 报工接口入参载体，通过 Jakarta Validation 注解完成基础校验。

#### 字段说明

| 字段名 | 类型 | 是否必填 | 说明 |
|--------|------|---------|------|
| `operationId` | `Long` | 是（`@NotNull`） | 目标操作 ID |
| `reportedQuantity` | `BigDecimal` | 否 | 本次报工数量；为 null 时自动填充剩余数量 |
| `qualifiedQuantity` | `BigDecimal` | 否 | 合格品数量；为 null 时默认等于 reportedQuantity |
| `defectQuantity` | `BigDecimal` | 否 | 不良品数量；为 null 时默认为 0 |
| `deviceCode` | `String` | 否 | 使用设备编码 |
| `notes` | `String` | 否 | 报工备注 |

---

### 3.2 UndoReportRequest

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/report/dto/UndoReportRequest.java`
**类型：** DTO
**职责：** 撤销报工接口入参载体，携带操作 ID 与撤销原因。

#### 字段说明

| 字段名 | 类型 | 是否必填 | 说明 |
|--------|------|---------|------|
| `operationId` | `Long` | 是（`@NotNull`） | 需撤销报工的操作 ID |
| `undoReason` | `String` | 否 | 撤销原因，写入报工记录 `undo_reason` 字段 |

---

### 3.3 ReportRecord

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/report/entity/ReportRecord.java`
**类型：** Entity
**职责：** 对应数据库表 `report_records`，持久化每次报工的完整快照，支持 MyBatis-Plus 逻辑删除与自动填充。

#### 字段说明

| 字段名 | 类型 | 注解/说明 |
|--------|------|---------|
| `id` | `Long` | `@TableId(AUTO)`，自增主键 |
| `operationId` | `Long` | 关联 `operations.id` |
| `workOrderId` | `Long` | 关联 `work_orders.id`，冗余存储，方便工单维度查询 |
| `userId` | `Long` | 报工操作员用户 ID |
| `deviceId` | `Long` | 使用设备 ID |
| `reportedQuantity` | `BigDecimal` | 本次报工数量 |
| `qualifiedQuantity` | `BigDecimal` | 合格品数量 |
| `defectQuantity` | `BigDecimal` | 不良品数量 |
| `reportTime` | `LocalDateTime` | 报工时间 |
| `isUndone` | `Boolean` | 是否已撤销 |
| `undoTime` | `LocalDateTime` | 撤销时间 |
| `undoReason` | `String` | 撤销原因 |
| `notes` | `String` | 备注 |
| `deleted` | `Integer` | `@TableLogic`，逻辑删除标志 |
| `createdAt` | `LocalDateTime` | `@TableField(INSERT)`，自动填充 |
| `updatedAt` | `LocalDateTime` | `@TableField(INSERT_UPDATE)`，自动填充 |

---

### 3.4 ReportRecordMapper

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/report/repository/ReportRecordMapper.java`
**类型：** Repository / Mapper
**职责：** 继承 MyBatis-Plus `BaseMapper<ReportRecord>`，扩展了三个业务专用查询方法。

#### 方法列表

| 方法名 | 访问修饰符 | 返回类型 | 功能描述 |
|--------|-----------|---------|---------|
| `findActiveByOperationId` | `public default` | `List<ReportRecord>` | 查询指定操作下所有未撤销、未删除的报工记录，按报工时间降序 |
| `findLatestByOperationIdAndUser` | `public default` | `Optional<ReportRecord>` | 查询指定操作与用户下最近一条未撤销报工记录 |
| `sumReportedQuantityByOperationId` | `public` | `BigDecimal` | 统计指定操作的有效总报工数量，空值返回 0 |

#### 方法详情

##### findActiveByOperationId(Long operationId)
- **功能：** 查询某操作下所有有效报工记录，用于历史记录展示。
- **参数：**
  - `operationId` (Long)：操作 ID
- **返回值：** `List<ReportRecord>` — 按 `reportTime` 降序排列的有效报工记录列表
- **备注：** 条件：`operationId = ?` AND `isUndone = false` AND `deleted = 0`

##### findLatestByOperationIdAndUser(Long operationId, Long userId)
- **功能：** 查询指定操作与用户下最近一条未撤销报工记录，作为撤销操作的目标。
- **参数：**
  - `operationId` (Long)：操作 ID
  - `userId` (Long)：操作员用户 ID
- **返回值：** `Optional<ReportRecord>` — 存在则包含最近记录，否则为空
- **备注：** 通过 `orderByDesc(reportTime).last("LIMIT 1")` 保证仅取最新一条

##### sumReportedQuantityByOperationId(@Param("operationId") Long operationId)
- **功能：** 聚合查询指定操作的有效总报工数量，用于计算剩余可报数量及更新完成数量。
- **参数：**
  - `operationId` (Long)：操作 ID
- **返回值：** `BigDecimal` — 有效报工数量之和，无记录时 `COALESCE` 保证返回 0
- **备注：** `@Select` 注解内联 SQL：`SELECT COALESCE(SUM(reported_quantity), 0) FROM report_records WHERE operation_id = #{operationId} AND is_undone = false AND deleted = 0`

---

### 3.5 ReportService

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/report/service/ReportService.java`
**类型：** Service
**职责：** 报工模块核心业务服务，统一编排开工登记、报工记录创建、撤销报工及历史查询，驱动操作与工单状态联动流转，并通过 Spring 事件发布实现 MES 集成解耦。

#### 依赖注入

| 依赖 | 类型 | 用途 |
|------|------|------|
| `reportRecordMapper` | `ReportRecordMapper` | 报工记录 CRUD |
| `operationMapper` | `OperationMapper` | 查询与更新工序状态 |
| `workOrderMapper` | `WorkOrderMapper` | 查询与更新工单状态 |
| `eventPublisher` | `ApplicationEventPublisher` | 发布 MES 集成领域事件 |

#### 方法列表

| 方法名 | 访问修饰符 | 返回类型 | 功能描述 |
|--------|-----------|---------|---------|
| `startWork` | `public` | `ReportRecord` | 登记开工，工序状态 NOT_STARTED → STARTED，首次开工时同步更新工单状态 |
| `reportWork` | `public` | `ReportRecord` | 提交报工，校验数量、创建记录、更新工序与工单状态、发布 MES 事件 |
| `undoReport` | `public` | `void` | 撤销最近一条有效报工记录，重算完成数量并回滚工序与工单状态 |
| `getReportHistory` | `public` | `List<ReportRecord>` | 查询指定操作的有效报工历史记录 |
| `getOperationOrThrow` | `private` | `Operation` | 查询工序实体，不存在或已删除则抛出 BusinessException |
| `updateWorkOrderStatusOnStart` | `private` | `void` | 首次开工时将工单状态更新为 STARTED 并发布状态变更事件 |
| `updateWorkOrderOnReport` | `private` | `void` | 报工或撤销后重算工单完成数量，全部完成时将工单推进至 REPORTED |

#### 方法详情

##### startWork(Long operationId, Long userId)
- **功能：** 登记工序开工，驱动工序状态从 `NOT_STARTED` 流转为 `STARTED`。
- **参数：**
  - `operationId` (Long)：需要开工的工序 ID
  - `userId` (Long)：执行开工的用户 ID（用于日志）
- **返回值：** `ReportRecord` — 当前版本固定返回 null（预留扩展）
- **异常：**
  - `BusinessException` — 工序不存在或已删除
  - `BusinessException` — 工序状态不为 `NOT_STARTED`
- **备注：** `@Transactional`；调用 `updateWorkOrderStatusOnStart` 判断是否为工单首次开工。

##### reportWork(ReportRequest request, Long userId, Long deviceId)
- **功能：** 核心报工方法，完成数量校验、记录持久化、工序与工单状态更新及 MES 事件发布。
- **参数：**
  - `request` (ReportRequest)：报工请求，含工序 ID、数量、合格量、不良量、备注
  - `userId` (Long)：报工操作员用户 ID
  - `deviceId` (Long)：使用设备 ID
- **返回值：** `ReportRecord` — 已持久化的报工记录实体（含自增 ID）
- **异常：**
  - `BusinessException` — 工序不存在或已删除
  - `BusinessException` — 工序状态不为 `STARTED` 且不为 `NOT_STARTED`
  - `BusinessException` — 报工数量 <= 0
  - `BusinessException` — 报工数量超过剩余可报数量
- **备注（核心逻辑）：**
  1. **数量自动填充：** `reportedQuantity` 为 null 时自动使用剩余数量（`plannedQty - alreadyReported`），支持一键全量报工
  2. **数量上限校验：** 严格校验不超过剩余量，防止过报
  3. **默认值处理：** `qualifiedQuantity` 默认等于报工数量，`defectQuantity` 默认为 0
  4. **工序状态流转：** 累计完成量 >= 计划量时工序状态变为 `REPORTED`，否则维持 `STARTED`
  5. **事件发布：** `ReportRecordSavedEvent` 在 insert 之后发布；监听器配合 `@TransactionalEventListener(AFTER_COMMIT)` 在事务提交后异步推送 MES
  6. **工单联动：** 调用 `updateWorkOrderOnReport` 汇总工单完成数量，全部完成时推进工单至 `REPORTED`

##### undoReport(UndoReportRequest request, Long userId)
- **功能：** 撤销指定工序下当前用户最近一条有效报工记录，重算完成数量并回滚状态。
- **参数：**
  - `request` (UndoReportRequest)：撤销请求，含工序 ID 和撤销原因
  - `userId` (Long)：发起撤销的用户 ID
- **返回值：** `void`
- **异常：**
  - `BusinessException` — 工序不存在或已删除
  - `BusinessException` — 当前用户在该工序下无有效报工记录
- **备注：**
  1. **软撤销：** 将 `isUndone` 置为 true，保留历史数据，不物理删除
  2. **完成数量重算：** 撤销后重新聚合查询，避免内存减法的精度与并发问题
  3. **工序状态回滚：** 有效完成量归零时回退至 `NOT_STARTED`，否则维持 `STARTED`
  4. **约束：** 仅撤销当前用户最近一条记录，不支持跨用户撤销

##### getReportHistory(Long operationId)
- **功能：** 查询指定工序的有效报工历史记录。
- **参数：**
  - `operationId` (Long)：工序 ID
- **返回值：** `List<ReportRecord>` — 按报工时间降序排列的有效报工记录

##### getOperationOrThrow(Long operationId)（private）
- **功能：** 查询工序实体的防御性封装，不存在或已删除则抛出业务异常。
- **参数：**
  - `operationId` (Long)：工序 ID
- **返回值：** `Operation` — 有效的工序实体
- **异常：** `BusinessException` — `"Operation not found: {operationId}"`
- **备注：** 被 `startWork`、`reportWork`、`undoReport` 三个方法复用。

##### updateWorkOrderStatusOnStart(Long workOrderId)（private）
- **功能：** 首次开工时将工单状态从 `NOT_STARTED` 更新为 `STARTED`，记录实际开始时间并发布事件。
- **备注：** 幂等判断，仅工单状态为 `NOT_STARTED` 时触发；发布 `WorkOrderStatusChangedEvent`。

##### updateWorkOrderOnReport(Long workOrderId)（private）
- **功能：** 报工或撤销后重算工单完成数量；所有工序均处于终态且工单类型为 `PRODUCTION` 时推进工单至 `REPORTED`。
- **备注：**
  - 终态判断包含四种状态：`REPORTED`、`INSPECTED`、`TRANSPORTED`、`HANDLED`
  - 仅 `PRODUCTION` 类型工单触发状态流转，其他类型仅更新完成数量
  - 状态流转时发布 `WorkOrderStatusChangedEvent`

---

## 4. 模块内调用关系

```
ReportService
├── startWork(operationId, userId)
│   ├── getOperationOrThrow(operationId)          → OperationMapper.selectById
│   ├── OperationMapper.updateById(operation)      [NOT_STARTED → STARTED]
│   └── updateWorkOrderStatusOnStart(workOrderId)
│       ├── WorkOrderMapper.selectById
│       ├── WorkOrderMapper.updateById             [NOT_STARTED → STARTED]
│       └── eventPublisher.publish(WorkOrderStatusChangedEvent)
│
├── reportWork(request, userId, deviceId)
│   ├── getOperationOrThrow(operationId)
│   ├── ReportRecordMapper.sumReportedQuantityByOperationId
│   ├── ReportRecordMapper.insert(record)          [新增报工记录]
│   ├── eventPublisher.publish(ReportRecordSavedEvent)
│   ├── OperationMapper.updateById(operation)      [更新 completedQuantity 及状态]
│   └── updateWorkOrderOnReport(workOrderId)
│       ├── WorkOrderMapper.selectById
│       ├── OperationMapper.findByWorkOrderId
│       ├── WorkOrderMapper.updateById
│       └── eventPublisher.publish(WorkOrderStatusChangedEvent) [仅全部完成时]
│
├── undoReport(request, userId)
│   ├── getOperationOrThrow(operationId)
│   ├── ReportRecordMapper.findLatestByOperationIdAndUser
│   ├── ReportRecordMapper.updateById(latest)      [isUndone=true]
│   ├── ReportRecordMapper.sumReportedQuantityByOperationId
│   ├── OperationMapper.updateById(operation)      [重算状态回滚]
│   └── updateWorkOrderOnReport(workOrderId)
│
└── getReportHistory(operationId)
    └── ReportRecordMapper.findActiveByOperationId
```

**跨模块依赖：**

| 本模块调用方 | 依赖目标 | 依赖说明 |
|-------------|---------|---------|
| `ReportService` | `OperationMapper`（operation 模块） | 读取与更新工序实体状态 |
| `ReportService` | `WorkOrderMapper`（workorder 模块） | 读取与更新工单实体状态 |
| `ReportService` | `ReportRecordSavedEvent`（mesintegration 模块） | 报工完成后触发 MES 数据推送 |
| `ReportService` | `WorkOrderStatusChangedEvent`（mesintegration 模块） | 工单状态变更后触发 MES 通知 |

---

## 5. 重要业务逻辑说明

### 5.1 报工数量校验与自动填充

`reportWork` 对报工数量执行两阶段处理：

1. **自动填充：** 若未传入 `reportedQuantity`（为 null），则以剩余可报数量作为本次报工量，支持"一键全量报工"场景。
2. **上限校验：** 若传入了显式数量，则校验必须 > 0 且不超过剩余可报数量，超出时抛出携带明确超额信息的 `BusinessException`。

### 5.2 工序状态流转

```
NOT_STARTED
    │ startWork()
    ▼
  STARTED  ◄────────────────────────────────────────────┐
    │                                                    │
    │ reportWork() (completedQty < plannedQty)           │ undoReport() (newCompleted > 0)
    ▼                                                    │
  STARTED ────────────────────────────────────────────► ┘
    │
    │ reportWork() (completedQty >= plannedQty)
    ▼
 REPORTED
    │
    └─── undoReport() (newCompleted == 0) ──► NOT_STARTED
```

### 5.3 工单状态联动

- **首次开工：** `updateWorkOrderStatusOnStart` 将工单从 `NOT_STARTED` 推进至 `STARTED`，记录 `actualStartTime`，幂等设计防止重复变更。
- **全部完成：** `updateWorkOrderOnReport` 在所有工序均处于终态且工单类型为 `PRODUCTION` 时，将工单推进至 `REPORTED`。

### 5.4 事件驱动的 MES 集成

| 事件类 | 发布时机 | 用途 |
|--------|---------|------|
| `ReportRecordSavedEvent` | 报工记录 insert 成功后 | 将报工数据异步推送至 MES |
| `WorkOrderStatusChangedEvent` | 工单状态变更后 | 将工单状态变更通知 MES |

事件通过 `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` 实现：业务事务提交成功后，在独立线程池中异步推送 MES，推送失败不影响业务事务。

### 5.5 撤销设计约束

1. **仅限最新记录：** 只能撤销当前用户在同一工序下最近一条有效报工，不支持指定历史记录撤销。
2. **用户隔离：** 以 `(operationId, userId)` 为作用域，用户 A 不能撤销用户 B 的报工记录。
3. **工单不降级：** `updateWorkOrderOnReport` 仅在"全部已完成"时推进工单状态，撤销后工单不会从 `STARTED` 回退至 `NOT_STARTED`。

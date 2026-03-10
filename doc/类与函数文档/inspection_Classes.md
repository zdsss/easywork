# inspection 模块 — 类与函数文档

## 1. 模块概述

inspection（质检）模块负责对工单进行质量检验的全流程管理。核心业务场景：当工单处于 `REPORTED`（已上报）状态后，质检员对工单对应的生产工序进行质量检验，记录检验结果（通过/不通过）及相关数量数据，并自动更新工单状态为 `INSPECT_PASSED` 或 `INSPECT_FAILED`，同时通过 Spring 应用事件机制将质检记录及工单状态变更推送给 MES 系统。

模块分层架构：

```
controller  →  service  →  repository（Mapper）
                  ↓
              entity / dto
                  ↓
         ApplicationEventPublisher（事件发布）
```

模块目录结构：

```
inspection/
├── controller/
│   └── InspectionController.java
├── dto/
│   └── InspectionRequest.java
├── entity/
│   └── InspectionRecord.java
├── repository/
│   └── InspectionRecordMapper.java
└── service/
    └── InspectionService.java
```

---

## 2. 核心类列表

| 类名 | 所在文件 | 职责描述 |
|------|---------|---------|
| `InspectionController` | `controller/InspectionController.java` | REST 控制器，暴露质检相关 HTTP 接口，负责接收请求并委托 Service 处理 |
| `InspectionRequest` | `dto/InspectionRequest.java` | 数据传输对象，封装前端提交质检结果时携带的请求参数，并承担参数校验职责 |
| `InspectionRecord` | `entity/InspectionRecord.java` | 数据库实体，映射 `inspection_records` 表，存储质检记录的完整信息 |
| `InspectionRecordMapper` | `repository/InspectionRecordMapper.java` | MyBatis-Plus Mapper 接口，提供对 `inspection_records` 表的 CRUD 操作 |
| `InspectionService` | `service/InspectionService.java` | 核心业务 Service，实现质检提交的完整业务逻辑，包括工单状态校验、记录持久化及事件发布 |

---

## 3. 类详细说明

### 3.1 InspectionController

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/inspection/controller/InspectionController.java`
**类型：** Controller
**职责：** 作为 inspection 模块的 HTTP 入口层，将 `/api/admin/inspections` 路径下的请求路由到对应的 Service 方法。通过 `SecurityUtils` 获取当前登录用户 ID 作为质检员身份。

#### 方法列表

| 方法名 | 访问修饰符 | 返回类型 | 功能描述 |
|--------|-----------|---------|---------|
| `submitInspection` | `public` | `ApiResponse<InspectionRecord>` | 接收质检结果提交请求，调用 Service 完成质检记录创建与工单状态更新 |

#### 方法详情

##### submitInspection(@Valid @RequestBody InspectionRequest request)
- **功能：** 接收前端传入的质检请求体，从 Spring Security 上下文中获取当前质检员的用户 ID，调用 `InspectionService.submitInspection()` 完成业务处理。
- **HTTP 方法/路径：** `POST /api/admin/inspections`
- **参数：**
  - `request` (InspectionRequest)：质检请求体，由 `@Valid` 触发 Bean Validation 校验
- **返回值：** `ApiResponse<InspectionRecord>` — 包含创建成功的质检记录实体对象
- **异常：** `MethodArgumentNotValidException` — 参数校验失败；业务异常由 Service 层抛出
- **备注：** `inspectorId` 通过 `SecurityUtils.getCurrentUserId()` 从安全上下文提取，防止伪造质检员身份。

---

### 3.2 InspectionRequest

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/inspection/dto/InspectionRequest.java`
**类型：** DTO（请求体）
**职责：** 封装前端提交质检结果时的请求参数，使用 Lombok `@Data` 和 Jakarta Bean Validation 注解。

#### 字段说明

| 字段名 | 类型 | 是否必填 | 说明 |
|--------|------|---------|------|
| `workOrderId` | `Long` | 是（`@NotNull`） | 被质检的工单 ID |
| `operationId` | `Long` | 否 | 被质检的工序 ID，可选 |
| `inspectionResult` | `String` | 是（`@NotNull`） | 质检结果：`PASSED` 或 `FAILED` |
| `inspectedQuantity` | `BigDecimal` | 否 | 实际检验数量 |
| `qualifiedQuantity` | `BigDecimal` | 否 | 合格数量 |
| `defectQuantity` | `BigDecimal` | 否 | 不合格（缺陷）数量 |
| `defectReason` | `String` | 否 | 缺陷原因描述 |
| `notes` | `String` | 否 | 质检备注说明 |

---

### 3.3 InspectionRecord

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/inspection/entity/InspectionRecord.java`
**类型：** Entity（数据库实体）
**职责：** 映射数据库表 `inspection_records`，承载质检记录的完整数据结构。

#### 字段说明

| 字段名 | 类型 | 注解/说明 |
|--------|------|---------|
| `id` | `Long` | `@TableId(type = IdType.AUTO)`，自增主键 |
| `workOrderId` | `Long` | 关联工单 ID |
| `operationId` | `Long` | 关联工序 ID |
| `inspectorId` | `Long` | 质检员用户 ID |
| `inspectionType` | `String` | 检验类型，当前固定为 `QUALITY` |
| `inspectionResult` | `String` | 检验结果：`PASSED` 或 `FAILED` |
| `inspectedQuantity` | `BigDecimal` | 实际检验数量 |
| `qualifiedQuantity` | `BigDecimal` | 合格数量 |
| `defectQuantity` | `BigDecimal` | 不合格数量 |
| `defectReason` | `String` | 缺陷原因 |
| `status` | `String` | 记录状态，创建时固定写入 `INSPECTED` |
| `inspectionTime` | `LocalDateTime` | 质检时间，保存时设置为当前时间 |
| `notes` | `String` | 备注 |
| `deleted` | `Integer` | `@TableLogic`，逻辑删除标志 |
| `createdAt` | `LocalDateTime` | `@TableField(fill = INSERT)`，创建时间 |
| `updatedAt` | `LocalDateTime` | `@TableField(fill = INSERT_UPDATE)`，更新时间 |

---

### 3.4 InspectionRecordMapper

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/inspection/repository/InspectionRecordMapper.java`
**类型：** Mapper / Repository
**职责：** 继承 MyBatis-Plus `BaseMapper<InspectionRecord>`，当前模块主要使用 `insert` 方法插入新记录。

#### 主要继承方法（来自 BaseMapper）

| 方法名 | 返回类型 | 功能描述 |
|--------|---------|---------|
| `insert(T entity)` | `int` | 插入一条质检记录 |
| `selectById(Serializable id)` | `T` | 根据主键查询 |
| `selectList(Wrapper<T> queryWrapper)` | `List<T>` | 条件查询列表 |
| `updateById(T entity)` | `int` | 根据主键更新 |

---

### 3.5 InspectionService

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/inspection/service/InspectionService.java`
**类型：** Service
**职责：** inspection 模块的核心业务逻辑层，负责协调数据校验、质检记录持久化、工单状态更新及 MES 集成事件发布。

#### 依赖注入

| 依赖 | 类型 | 用途 |
|------|------|------|
| `inspectionRecordMapper` | `InspectionRecordMapper` | 插入质检记录 |
| `workOrderMapper` | `WorkOrderMapper` | 查询并更新工单状态 |
| `eventPublisher` | `ApplicationEventPublisher` | 发布质检完成及工单状态变更事件 |

#### 方法列表

| 方法名 | 访问修饰符 | 返回类型 | 功能描述 |
|--------|-----------|---------|---------|
| `submitInspection` | `public` | `InspectionRecord` | 提交质检结果，包含工单状态校验、记录持久化、工单状态更新及 MES 事件发布 |

#### 方法详情

##### submitInspection(InspectionRequest request, Long inspectorId)
- **功能：** 提交一条质检记录，根据质检结果更新工单状态，向 MES 集成模块发布两个领域事件。整个方法被 `@Transactional` 标注，确保原子性。
- **参数：**
  - `request` (InspectionRequest)：包含工单 ID、工序 ID、检验结果及数量信息
  - `inspectorId` (Long)：当前执行质检操作的用户 ID，由 Controller 层从安全上下文中提取
- **返回值：** `InspectionRecord` — 已持久化的质检记录实体（含自增主键 `id`）
- **异常：**
  - `BusinessException("Work order not found: ...")` — 工单 ID 不存在或已被逻辑删除
  - `BusinessException("Work order must be in REPORTED status...")` — 工单当前状态不是 `REPORTED`
- **备注：** 业务执行步骤：
  1. **工单合法性校验**：查询工单，若不存在或 `deleted == 1` 则抛异常
  2. **工单状态前置校验**：仅允许对 `REPORTED` 状态的工单进行质检
  3. **构建并插入质检记录**：`inspectionType` 固定 `"QUALITY"`，`status` 固定 `"INSPECTED"`，`inspectionTime` 为当前时间
  4. **发布质检记录保存事件**：发布 `InspectionRecordSavedEvent`，MES 模块在事务提交后消费
  5. **更新工单状态并发布变更事件**：`PASSED` → `INSPECT_PASSED`，`FAILED` → `INSPECT_FAILED`，随后发布 `WorkOrderStatusChangedEvent`

---

## 4. 模块内调用关系

```
HTTP POST /api/admin/inspections
        │
        ▼
InspectionController.submitInspection(request)
        │  从 SecurityUtils 获取 inspectorId
        │
        ▼
InspectionService.submitInspection(request, inspectorId)
        │
        ├─① WorkOrderMapper.selectById(workOrderId)          # 查询工单
        │       └─ 校验存在性 & 状态是否为 REPORTED
        │
        ├─② InspectionRecordMapper.insert(record)            # 插入质检记录
        │
        ├─③ ApplicationEventPublisher
        │       └─ publish(InspectionRecordSavedEvent)        # 通知 MES
        │
        ├─④ WorkOrderMapper.updateById(workOrder)            # 更新工单状态
        │       └─ PASSED → INSPECT_PASSED
        │          FAILED → INSPECT_FAILED
        │
        └─⑤ ApplicationEventPublisher
                └─ publish(WorkOrderStatusChangedEvent)       # 通知 MES 状态变更
```

**跨模块依赖：**

| 依赖来源 | 被依赖类 | 说明 |
|---------|---------|------|
| `inspection` → `workorder` | `WorkOrderMapper`、`WorkOrder` | 查询和更新工单数据 |
| `inspection` → `mesintegration` | `InspectionRecordSavedEvent`、`WorkOrderStatusChangedEvent` | 通过事件机制向 MES 推送 |
| `inspection` → `common` | `ApiResponse`、`SecurityUtils`、`BusinessException` | 公共工具类与异常体系 |

---

## 5. 重要业务逻辑说明

### 5.1 工单状态机约束

质检操作对工单状态有严格的前置约束：只有处于 `REPORTED` 状态的工单才能被质检。状态流转路径：

```
REPORTED  →（质检通过）→  INSPECT_PASSED
REPORTED  →（质检不通过）→  INSPECT_FAILED
```

### 5.2 事务与事件发布时序
`@Transactional` 保证数据一致性。`InspectionRecordSavedEvent` 通过 `@TransactionalEventListener(AFTER_COMMIT)` 在事务提交后才触发，避免 MES 查询到未提交的脏数据。

### 5.3 质检类型固定为 QUALITY
`inspectionType` 在 Service 层硬编码为 `"QUALITY"`，仅支持质量检验类型，该字段预留了未来扩展空间。

### 5.4 质检员身份安全性
质检员 ID 不由前端传入，而是由 Controller 层通过 `SecurityUtils.getCurrentUserId()` 从 Spring Security 认证上下文中提取，防止伪造。

### 5.5 数量字段均为可选
`InspectionRequest` 中的数量字段均无 `@NotNull` 校验，允许质检员仅记录通过/不通过结果而不填写具体数量，适应轻量级质检场景。

### 5.6 逻辑删除与防御性编程
`InspectionRecord` 使用 `@TableLogic`。`InspectionService` 在查询工单时额外手动校验了 `deleted == 1` 的情况，是跨模块调用时的防御性编程实践。

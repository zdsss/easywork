# call 模块 — 类与函数文档

## 1. 模块概述

call 模块（呼叫/安灯模块）是 EasyWork 工单系统中负责处理车间安灯呼叫业务的功能模块。安灯（Andon）是一种源自精益生产的可视化管理机制，工人在生产过程中遇到问题时可发起呼叫，由相关人员响应并处理。

该模块实现了呼叫记录的完整生命周期管理，涵盖：

- **发起呼叫**：操作人员针对某一工单发起呼叫请求，记录呼叫类型与描述信息。
- **接受处理**：处理人员认领呼叫，记录响应时间与处理人 ID。
- **完成处理**：处理人员填写处理结果，将呼叫标记为已处理并记录完成时间。

模块包结构：

```
modules/call/
├── dto/          # 请求体 DTO（CallRequest）
├── entity/       # 数据库实体（CallRecord）
├── repository/   # 数据访问层（CallRecordMapper）
└── service/      # 业务逻辑层（CallService）
```

---

## 2. 核心类列表

| 类名 | 所在文件 | 职责描述 |
|------|---------|---------|
| `CallRequest` | `dto/CallRequest.java` | 呼叫创建请求的数据传输对象，携带工单 ID、工序 ID、呼叫类型、描述等入参，包含 JSR-380 校验注解 |
| `CallRecord` | `entity/CallRecord.java` | 呼叫记录的数据库实体类，映射 `call_records` 表，记录呼叫全生命周期字段 |
| `CallRecordMapper` | `repository/CallRecordMapper.java` | MyBatis-Plus Mapper 接口，继承 `BaseMapper<CallRecord>`，提供标准 CRUD 操作 |
| `CallService` | `service/CallService.java` | 呼叫业务核心服务类，实现呼叫发起、接受处理、完成处理三个核心业务方法 |

---

## 3. 类详细说明

### 3.1 CallRequest

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/call/dto/CallRequest.java`
**类型：** DTO（Data Transfer Object）
**职责：** 封装前端或上层调用方发起呼叫时所提交的请求参数。

#### 字段列表

| 字段名 | 类型 | 校验注解 | 说明 |
|--------|------|---------|------|
| `workOrderId` | `Long` | `@NotNull` | 关联的工单 ID，必填 |
| `operationId` | `Long` | 无 | 关联的工序 ID，可选，用于定位具体工位 |
| `callType` | `String` | `@NotBlank` | 呼叫类型，必填 |
| `description` | `String` | 无 | 呼叫描述/补充说明，可选 |

---

### 3.2 CallRecord

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/call/entity/CallRecord.java`
**类型：** Entity（数据库实体）
**职责：** 映射数据库 `call_records` 表，持久化一条呼叫记录的完整生命周期数据。

#### 字段列表

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `id` | `Long` | 主键，数据库自增 |
| `workOrderId` | `Long` | 关联的工单 ID |
| `operationId` | `Long` | 关联的工序 ID（可选） |
| `callType` | `String` | 呼叫类型 |
| `callerId` | `Long` | 发起呼叫的用户 ID |
| `handlerId` | `Long` | 接受/处理呼叫的用户 ID |
| `status` | `String` | 呼叫状态（`NOT_HANDLED` / `HANDLING` / `HANDLED`） |
| `callTime` | `LocalDateTime` | 呼叫发起时间 |
| `handleTime` | `LocalDateTime` | 开始处理时间 |
| `completeTime` | `LocalDateTime` | 处理完成时间 |
| `description` | `String` | 呼叫描述 |
| `handleResult` | `String` | 处理结果描述 |
| `notes` | `String` | 备注信息 |
| `deleted` | `Integer` | `@TableLogic`，逻辑删除标记（0=正常，1=已删除） |
| `createdAt` | `LocalDateTime` | `@TableField(fill = INSERT)`，创建时间 |
| `updatedAt` | `LocalDateTime` | `@TableField(fill = INSERT_UPDATE)`，更新时间 |

#### 状态枚举说明

| 状态值 | 含义 |
|--------|------|
| `NOT_HANDLED` | 待处理（呼叫刚发起，尚无人认领） |
| `HANDLING` | 处理中（已有人认领，正在处理） |
| `HANDLED` | 已处理（处理完毕，已填写处理结果） |

---

### 3.3 CallRecordMapper

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/call/repository/CallRecordMapper.java`
**类型：** Repository / Mapper
**职责：** 继承 MyBatis-Plus `BaseMapper<CallRecord>`，当前接口未自定义扩展方法，所有能力均来自 `BaseMapper` 继承。

#### 主要继承方法（来自 BaseMapper）

| 方法名 | 返回类型 | 功能描述 |
|--------|---------|---------|
| `insert(CallRecord entity)` | `int` | 插入一条呼叫记录 |
| `updateById(CallRecord entity)` | `int` | 根据主键更新呼叫记录 |
| `selectById(Serializable id)` | `CallRecord` | 根据主键查询（自动过滤逻辑删除记录） |
| `selectList(Wrapper<CallRecord>)` | `List<CallRecord>` | 按条件查询列表 |

---

### 3.4 CallService

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/call/service/CallService.java`
**类型：** Service（业务服务类）
**职责：** 呼叫模块的核心业务逻辑层，封装呼叫发起、接受处理、完成处理三个业务操作。依赖 `CallRecordMapper` 进行呼叫记录持久化，依赖 `WorkOrderMapper` 校验关联工单的合法性。

#### 方法列表

| 方法名 | 访问修饰符 | 返回类型 | 功能描述 |
|--------|-----------|---------|---------|
| `createCall(CallRequest, Long)` | `public` | `CallRecord` | 校验工单有效性，创建新呼叫记录，状态初始化为 `NOT_HANDLED` |
| `handleCall(Long, Long)` | `public` | `CallRecord` | 校验状态后，将呼叫状态流转为 `HANDLING`，记录处理人与处理开始时间 |
| `completeCall(Long, Long, String)` | `public` | `CallRecord` | 将呼叫状态流转为 `HANDLED`，记录处理结果与完成时间 |

#### 方法详情

##### createCall(CallRequest request, Long callerId)
- **功能：** 发起一次新的呼叫。校验目标工单是否存在，通过后将请求参数映射为 `CallRecord` 实体，设置初始状态为 `NOT_HANDLED`、记录发起人 ID 与呼叫时间，然后插入数据库。
- **参数：**
  - `request` (CallRequest)：呼叫创建请求体
  - `callerId` (Long)：当前发起呼叫的用户 ID
- **返回值：** `CallRecord` — 已持久化的呼叫记录实体
- **异常：** `BusinessException` — 工单不存在或已逻辑删除时抛出 `"Work order not found: {workOrderId}"`
- **备注：** `@Transactional`；初始 `status` 固定为 `"NOT_HANDLED"`；使用 `LocalDateTime.now()` 作为 `callTime`。

##### handleCall(Long callId, Long handlerId)
- **功能：** 处理人认领并开始处理一条呼叫记录。校验记录存在性和当前状态（必须为 `NOT_HANDLED`），通过后更新状态为 `HANDLING`，记录处理人 ID 与处理开始时间。
- **参数：**
  - `callId` (Long)：要处理的呼叫记录 ID
  - `handlerId` (Long)：认领并处理该呼叫的用户 ID
- **返回值：** `CallRecord` — 更新后的呼叫记录实体
- **异常：**
  - `BusinessException` — 呼叫记录不存在时抛出 `"Call record not found: {callId}"`
  - `BusinessException` — 记录状态不为 `NOT_HANDLED` 时抛出 `"Call is already being handled or completed"`
- **备注：** `@Transactional`；状态检查仅允许 `NOT_HANDLED` 状态的呼叫被认领，防止并发重复认领。

##### completeCall(Long callId, Long handlerId, String handleResult)
- **功能：** 处理人完成呼叫处理，填写处理结果并关闭呼叫。校验呼叫记录存在性后，更新状态为 `HANDLED`，记录处理人 ID、处理结果及完成时间。
- **参数：**
  - `callId` (Long)：要完成的呼叫记录 ID
  - `handlerId` (Long)：完成处理的用户 ID
  - `handleResult` (String)：处理结果的文字描述
- **返回值：** `CallRecord` — 更新后的呼叫记录实体
- **异常：** `BusinessException` — 呼叫记录不存在时抛出
- **备注：** `@Transactional`；当前实现未校验完成前状态是否为 `HANDLING`，允许从 `NOT_HANDLED` 直接跳转到 `HANDLED`。

---

## 4. 模块内调用关系

```
调用方（DeviceController 等）
        │
        ▼
CallService
  ├── createCall(request, callerId)
  │     ├── WorkOrderMapper.selectById(workOrderId)   ← 校验工单有效性
  │     └── CallRecordMapper.insert(record)            ← 持久化新呼叫记录
  │
  ├── handleCall(callId, handlerId)
  │     ├── CallRecordMapper.selectById(callId)        ← 获取并校验呼叫记录
  │     └── CallRecordMapper.updateById(record)        ← 更新状态为 HANDLING
  │
  └── completeCall(callId, handlerId, handleResult)
        ├── CallRecordMapper.selectById(callId)        ← 获取并校验呼叫记录
        └── CallRecordMapper.updateById(record)        ← 更新状态为 HANDLED

CallRecordMapper → 数据库表 call_records
WorkOrderMapper  → 数据库表 work_orders（跨模块依赖）
```

**跨模块依赖：** `CallService` 依赖 `modules.workorder.repository.WorkOrderMapper`，用于在创建呼叫时验证关联工单的有效性。这是 call 模块唯一的跨模块依赖。

---

## 5. 重要业务逻辑说明

### 5.1 呼叫生命周期与状态机

```
NOT_HANDLED  ──handleCall()──▶  HANDLING  ──completeCall()──▶  HANDLED
     │                                                               ▲
     └──────────────────completeCall()（允许跳过认领步骤）──────────┘
```

`handleCall` 对前置状态做了强校验（必须为 `NOT_HANDLED`），而 `completeCall` 未对前置状态做校验，允许从任意状态直接完成。

### 5.2 工单有效性双重校验
`createCall` 中，除 `selectById` 自动过滤逻辑删除记录外，额外检查了 `workOrder.getDeleted() == 1`，属于防御性编程。

### 5.3 软删除机制
`CallRecord` 通过 `@TableLogic` 实现逻辑删除，`deleteById` 实际执行 `UPDATE ... SET deleted=1`，所有查询自动追加 `WHERE deleted=0`。

### 5.4 并发安全提示
`handleCall` 通过状态检查防止同一呼叫被多人重复认领，在高并发场景下建议结合数据库乐观锁（version 字段）或分布式锁进一步加固，以彻底避免 TOCTOU 竞态条件。

### 5.5 设备端呼叫入口
`DeviceController` 中的 `callAndon()`、`callInspection()`、`callTransport()` 方法最终均委托 `CallService.createCall()` 执行，呼叫类型分别为 `"ANDON"`、`"INSPECTION"`、`"TRANSPORT"`。

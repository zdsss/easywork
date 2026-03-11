# operation 模块 — 类与函数文档

## 1. 模块概述

`operation`（工序）模块是 EasyWork 工单系统的核心子模块之一，负责管理工单中每道工序的定义、状态跟踪以及人员/班组分配关系。

该模块不直接对外暴露 REST 接口，而是作为数据访问层被 `workorder`、`mesintegration`、`report` 等上层服务模块引用，承担工序数据的持久化与查询职责。

模块包结构如下：

```
modules/operation/
├── entity/       # 数据库实体（Operation、OperationAssignment）
└── repository/   # 数据访问层（OperationMapper、OperationAssignmentMapper）
```

---

## 2. 核心类列表

| 类名 | 所在文件 | 职责描述 |
|------|---------|---------|
| `Operation` | `entity/Operation.java` | 工序实体，映射 `operations` 表，描述单道工序的完整属性，包含编号、类型、序号、计划/完成数量、状态、工时、工位等信息 |
| `OperationAssignment` | `entity/OperationAssignment.java` | 工序分配实体，映射 `operation_assignments` 表，记录工序与用户或班组之间的分配关系 |
| `OperationMapper` | `repository/OperationMapper.java` | 工序数据访问接口，提供标准 CRUD 及按工单、按用户/班组查询未完成工序的自定义方法 |
| `OperationAssignmentMapper` | `repository/OperationAssignmentMapper.java` | 工序分配数据访问接口，提供按工序 ID 查询有效分配记录的方法 |

---

## 3. 类详细说明

### 3.1 Operation

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/operation/entity/Operation.java`
**类型：** Entity（数据库实体）
**职责：** 表示一道工序的完整数据模型，通过 `@TableName("operations")` 映射到数据库 `operations` 表。

#### 字段列表

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `id` | `Long` | 主键，数据库自增（`@TableId(type = IdType.AUTO)`） |
| `workOrderId` | `Long` | 所属工单 ID |
| `operationNumber` | `String` | 工序编号，业务唯一标识 |
| `operationName` | `String` | 工序名称 |
| `operationType` | `String` | 工序类型（加工、检验、运输等） |
| `sequenceNumber` | `Integer` | 工序顺序号，决定同一工单内工序的执行顺序 |
| `plannedQuantity` | `BigDecimal` | 计划数量 |
| `completedQuantity` | `BigDecimal` | 已完成数量 |
| `status` | `String` | 工序状态（`NOT_STARTED`、`STARTED`、`REPORTED` 等） |
| `standardTime` | `Integer` | 标准工时（分钟） |
| `actualTime` | `Integer` | 实际工时（分钟） |
| `stationCode` | `String` | 工位编码 |
| `stationName` | `String` | 工位名称 |
| `notes` | `String` | 备注信息 |
| `deleted` | `Integer` | 逻辑删除标志（`@TableLogic`，0=正常，1=已删除） |
| `createdAt` | `LocalDateTime` | 创建时间，INSERT 时自动填充 |
| `updatedAt` | `LocalDateTime` | 更新时间，INSERT 和 UPDATE 时自动填充 |

---

### 3.2 OperationAssignment

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/operation/entity/OperationAssignment.java`
**类型：** Entity（数据库实体）
**职责：** 表示工序与执行主体（用户或班组）之间的分配关系，支持两种分配类型：`USER`（直接分配给个人）和 `TEAM`（分配给班组）。

#### 字段列表

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `id` | `Long` | 主键，数据库自增 |
| `operationId` | `Long` | 关联的工序 ID |
| `assignmentType` | `String` | 分配类型：`USER` 或 `TEAM` |
| `userId` | `Long` | 被分配的用户 ID（`USER` 类型时有效） |
| `teamId` | `Long` | 被分配的班组 ID（`TEAM` 类型时有效） |
| `assignedAt` | `LocalDateTime` | 分配发生的时间 |
| `deleted` | `Integer` | 逻辑删除标志 |
| `createdAt` | `LocalDateTime` | 创建时间，INSERT 时自动填充 |
| `updatedAt` | `LocalDateTime` | 更新时间，INSERT 和 UPDATE 时自动填充 |

---

### 3.3 OperationMapper

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/operation/repository/OperationMapper.java`
**类型：** Mapper（数据访问接口）
**职责：** 继承 `BaseMapper<Operation>` 获得完整基础 CRUD 能力，并扩展了自定义查询方法，是工序数据的核心访问入口。

#### 方法列表

| 方法名 | 访问修饰符 | 返回类型 | 功能描述 |
|--------|-----------|---------|---------|
| `findByWorkOrderId(Long)` | `public default` | `List<Operation>` | 查询指定工单下所有未删除的工序，按顺序号升序排列 |
| `findEarliestUnfinishedByUserAndWorkOrder(Long, Long)` | `public` | `Operation` | 查询 USER 类型分配中，指定用户在某工单中序号最小的未完成工序 |
| `findEarliestUnfinishedByTeamUserAndWorkOrder(Long, Long)` | `public` | `Operation` | 查询 TEAM 类型分配中，指定用户通过所属班组在某工单中序号最小的未完成工序 |

#### 方法详情

##### findByWorkOrderId(Long workOrderId)
- **功能：** 查询指定工单下所有有效工序，按 `sequenceNumber` 升序排列。
- **参数：**
  - `workOrderId` (Long)：目标工单的主键 ID
- **返回值：** `List<Operation>` — 工序对象列表；无匹配时返回空列表
- **备注：** 接口 `default` 方法，`LambdaQueryWrapper` 构建，过滤 `deleted = 0`，排序 `sequence_number ASC`。

##### findEarliestUnfinishedByUserAndWorkOrder(Long userId, Long workOrderId)
- **功能：** 查询以 `USER` 类型直接分配给指定用户、属于指定工单、且状态不在终态集合中的序号最小工序。
- **参数：**
  - `userId` (Long)：执行用户的主键 ID
  - `workOrderId` (Long)：目标工单的主键 ID
- **返回值：** `Operation` — 序号最小的未完成工序；无匹配时返回 `null`
- **备注：** 使用 `@Select` 内嵌 SQL。终态过滤：`status NOT IN ('REPORTED', 'INSPECTED', 'TRANSPORTED', 'HANDLED')`。`LIMIT 1` 取最早工序。

##### findEarliestUnfinishedByTeamUserAndWorkOrder(Long userId, Long workOrderId)
- **功能：** 通过双重 JOIN（`operation_assignments` + `team_members`），查询通过班组分配给该用户所在班组的最早未完成工序。
- **参数：**
  - `userId` (Long)：班组成员用户的主键 ID
  - `workOrderId` (Long)：目标工单的主键 ID
- **返回值：** `Operation` — 序号最小的未完成工序；无匹配时返回 `null`
- **备注：** 分配类型为 `TEAM`，需额外 JOIN `team_members` 确认班组成员身份。所有 JOIN 均附加 `deleted = 0` 过滤。

---

### 3.4 OperationAssignmentMapper

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/operation/repository/OperationAssignmentMapper.java`
**类型：** Mapper（数据访问接口）
**职责：** `OperationAssignment` 实体的数据访问层，提供按工序 ID 查询有效分配记录的能力。

#### 方法列表

| 方法名 | 访问修饰符 | 返回类型 | 功能描述 |
|--------|-----------|---------|---------|
| `findByOperationId(Long)` | `public default` | `List<OperationAssignment>` | 查询指定工序下所有未逻辑删除的分配记录（含 USER 和 TEAM 两种类型） |

#### 方法详情

##### findByOperationId(Long operationId)
- **功能：** 查询指定工序 ID 对应的所有有效分配记录。
- **参数：**
  - `operationId` (Long)：目标工序的主键 ID
- **返回值：** `List<OperationAssignment>` — 分配记录列表；无匹配时返回空列表
- **备注：** 上层可通过 `assignmentType` 字段区分 USER/TEAM 类型记录。

---

## 4. 模块内调用关系

```
operation 模块（纯数据访问层，无 Controller）

Operation (Entity)
    └── 被 OperationMapper 作为泛型参数使用

OperationAssignment (Entity)
    └── 被 OperationAssignmentMapper 作为泛型参数使用

OperationMapper (Mapper)
    └── findEarliestUnfinishedByTeamUserAndWorkOrder 内部 SQL 关联 team_members 表
```

**被上层模块引用情况：**

| 调用方模块 | 调用方类 | 使用的 Mapper |
|-----------|---------|--------------|
| `workorder` | `WorkOrderService` | `OperationMapper`、`OperationAssignmentMapper` |
| `mesintegration` | `MesInboundService` | `OperationMapper`、`OperationAssignmentMapper` |
| `mesintegration` | `MesOutboundService` | `OperationMapper` |
| `report` | `ReportService` | `OperationMapper` |

---

## 5. 重要业务逻辑说明

### 5.1 工序状态机与终态定义

工序 `status` 字段的关键终态（完成后所处状态）：

| 状态值 | 含义 |
|--------|------|
| `REPORTED` | 已报工 — 工人已提交报工 |
| `INSPECTED` | 已检验 — 质检完成 |
| `TRANSPORTED` | 已转运 — 物料转运完成 |
| `HANDLED` | 已处理 — 系统或管理员标记完毕 |

两个自定义查询方法均通过 `status NOT IN (...)` 排除终态，确保返回的始终是待执行工序。

### 5.2 双通道分配模型（USER vs TEAM）

- **USER 模式**：直接分配给特定用户，`userId` 有值，查询使用 `findEarliestUnfinishedByUserAndWorkOrder()`。
- **TEAM 模式**：分配给整个班组，`teamId` 有值，班组内所有成员均可处理，查询使用 `findEarliestUnfinishedByTeamUserAndWorkOrder()`。

上层业务通常同时调用两个方法，取 `sequenceNumber` 更小者作为用户当前应优先处理的工序。

### 5.3 逻辑删除机制
两个实体均使用 `@TableLogic`。MyBatis-Plus 在标准查询中自动附加 `deleted = 0`；但在 `@Select` 内嵌 SQL 中，须手动为每个关联表添加 `deleted = 0` 过滤。

### 5.4 工序顺序控制
`sequenceNumber` 是执行顺序的核心字段：所有查询均按 `sequence_number ASC` 排序，并通过 `LIMIT 1` 保证每次只取序号最小的待处理工序，确保工人按生产流程顺序逐道执行。

### 5.5 MES 系统集成
- **入站（Inbound）**：MES 下发工单时，将工序定义及分配信息写入本地库。
- **出站（Outbound）**：工序状态变更后，将最新状态同步推送回 MES 系统。

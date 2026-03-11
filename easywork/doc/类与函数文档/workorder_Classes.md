# workorder 模块 — 类与函数文档

## 1. 模块概述

`workorder` 模块是 EasyWork 系统的工单管理核心模块，负责工单的全生命周期管理，包括工单创建、工序生成、人员/团队派工、工单查询与状态追踪。该模块采用经典的分层架构（Controller → Service → Repository → Entity），并通过 DTO 对象实现接口层与持久层的数据隔离。

**主要业务场景：**
1. 管理员创建工单，并同步生成所属工序。
2. 管理员将工序派工给指定用户（直接派工）或团队（团队派工）。
3. 工人通过用户 ID 查询自己被派工的所有工单（支持双路径合并：直接派工 + 团队派工）。
4. 管理员按状态分页查询全部工单，或通过 ID / 条码查询单张工单详情。

---

## 2. 核心类列表

| 类名 | 所在文件 | 职责描述 |
|------|---------|---------|
| `AdminWorkOrderController` | `controller/AdminWorkOrderController.java` | REST 控制器，暴露管理端工单 HTTP 接口 |
| `WorkOrderService` | `service/WorkOrderService.java` | 核心业务服务，封装工单全部业务逻辑 |
| `WorkOrderMapper` | `repository/WorkOrderMapper.java` | MyBatis-Plus Mapper，扩展双路径派工查询 SQL |
| `WorkOrder` | `entity/WorkOrder.java` | 工单数据库实体，映射 `work_orders` 表 |
| `WorkOrderDTO` | `dto/WorkOrderDTO.java` | 工单数据传输对象，含内部类 `OperationSummary` |
| `CreateWorkOrderRequest` | `dto/CreateWorkOrderRequest.java` | 创建工单请求体，含内部类 `OperationInput` |
| `AssignWorkOrderRequest` | `dto/AssignWorkOrderRequest.java` | 工序派工请求体 |

---

## 3. 类详细说明

### 3.1 AdminWorkOrderController

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/workorder/controller/AdminWorkOrderController.java`
**类型：** Controller（REST 控制器）
**职责：** 作为管理端工单管理的 HTTP 入口，接收前端请求并委托 `WorkOrderService` 执行业务逻辑，统一封装返回 `ApiResponse` 格式。基路由为 `/api/admin/work-orders`。

#### 方法列表

| 方法名 | 访问修饰符 | 返回类型 | HTTP 路径 | 功能描述 |
|--------|-----------|---------|----------|---------|
| `createWorkOrder` | `public` | `ApiResponse<WorkOrderDTO>` | `POST /api/admin/work-orders` | 创建新工单 |
| `listWorkOrders` | `public` | `ApiResponse<List<WorkOrderDTO>>` | `GET /api/admin/work-orders` | 分页查询工单列表（可按状态过滤） |
| `getWorkOrder` | `public` | `ApiResponse<WorkOrderDTO>` | `GET /api/admin/work-orders/{id}` | 按 ID 查询工单详情 |
| `assignWorkOrder` | `public` | `ApiResponse<Void>` | `POST /api/admin/work-orders/assign` | 将工序派工给用户或团队 |

#### 方法详情

##### createWorkOrder(@Valid @RequestBody CreateWorkOrderRequest request)
- **功能：** 接收创建工单请求，获取当前登录用户 ID 作为创建人，调用服务层创建工单及关联工序，返回创建结果 DTO。
- **参数：**
  - `request` (CreateWorkOrderRequest)：工单创建请求体，包含工单号、类型、产品信息、计划数量、工序列表等，需通过 `@Valid` 校验。
- **返回值：** `ApiResponse<WorkOrderDTO>` — 封装创建成功的工单详情。
- **备注：** 通过 `SecurityUtils.getCurrentUserId()` 获取当前操作用户 ID，避免客户端伪造创建人信息。

##### listWorkOrders(int page, int size, String status)
- **功能：** 分页查询所有工单，支持按工单状态过滤，默认返回第 1 页、每页 20 条。
- **参数：**
  - `page` (int)：页码，默认值 `1`
  - `size` (int)：每页条数，默认值 `20`
  - `status` (String)：工单状态过滤条件，为空则返回所有状态工单
- **返回值：** `ApiResponse<List<WorkOrderDTO>>` — 封装当前页的工单 DTO 列表。

##### getWorkOrder(@PathVariable Long id)
- **功能：** 按主键 ID 查询单张工单的详细信息（含工序列表）。
- **参数：**
  - `id` (Long)：工单主键 ID
- **返回值：** `ApiResponse<WorkOrderDTO>` — 封装工单详情 DTO，含所有关联工序摘要。
- **异常：** 若工单不存在或已逻辑删除，服务层抛出 `BusinessException`。

##### assignWorkOrder(@Valid @RequestBody AssignWorkOrderRequest request)
- **功能：** 将指定工序派工给用户列表（USER 类型）或团队列表（TEAM 类型）。
- **参数：**
  - `request` (AssignWorkOrderRequest)：派工请求体，包含工序 ID、派工类型及目标用户/团队 ID 列表
- **返回值：** `ApiResponse<Void>` — 固定返回成功消息 `"Assignment successful"`。

---

### 3.2 WorkOrderService

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/workorder/service/WorkOrderService.java`
**类型：** Service（业务服务层）
**职责：** 工单模块的核心业务处理类，负责工单创建（含工序批量生成）、工序派工（用户/团队双模式）、工单多维度查询以及实体到 DTO 的转换。

#### 方法列表

| 方法名 | 访问修饰符 | 返回类型 | 功能描述 |
|--------|-----------|---------|---------|
| `createWorkOrder` | `public` | `WorkOrderDTO` | 创建工单及关联工序（事务） |
| `assignWorkOrder` | `public` | `void` | 工序派工给用户或团队（事务） |
| `getAssignedWorkOrders` | `public` | `List<WorkOrderDTO>` | 双路径查询当前用户被派工的工单 |
| `getWorkOrderById` | `public` | `WorkOrderDTO` | 按 ID 查询工单详情 |
| `listAllWorkOrders` | `public` | `List<WorkOrderDTO>` | 分页查询全部工单（可状态过滤） |
| `getWorkOrderByBarcode` | `public` | `WorkOrderDTO` | 按条码（工单号）查询工单 |
| `toDTO` | `private` | `WorkOrderDTO` | WorkOrder 实体转 DTO（含工序列表） |

#### 方法详情

##### createWorkOrder(CreateWorkOrderRequest request, Long createdBy)
- **功能：** 在单个事务内完成工单主记录创建和所有工序记录的批量创建。创建前校验工单号唯一性，工序编号自动按格式 `{工单号}-OP{序号三位补零}` 生成，初始状态均为 `NOT_STARTED`。
- **参数：**
  - `request` (CreateWorkOrderRequest)：工单创建请求，包含工单基本信息及工序列表
  - `createdBy` (Long)：创建人用户 ID
- **返回值：** `WorkOrderDTO` — 创建成功的工单详情
- **异常：** `BusinessException` — 工单号已存在时抛出
- **备注：** `@Transactional`；未指定时，工序数量继承工单数量，工序类型默认 `"PRODUCTION"`。

##### assignWorkOrder(AssignWorkOrderRequest request)
- **功能：** 根据派工类型，将工序执行责任分配给指定的用户列表（USER 模式）或团队列表（TEAM 模式）。每条派工记录写入 `operation_assignments` 表。
- **参数：**
  - `request` (AssignWorkOrderRequest)：派工请求
- **返回值：** `void`
- **异常：** `BusinessException` — 工序不存在或已删除时抛出
- **备注：** `@Transactional`；当前实现不校验重复派工。

##### getAssignedWorkOrders(Long userId)
- **功能：** 合并查询指定用户通过"直接派工"和"团队派工"两种路径获得的工单，去重后按优先级降序、计划开始时间升序排序。
- **参数：**
  - `userId` (Long)：当前登录用户的 ID
- **返回值：** `List<WorkOrderDTO>` — 去重合并、排序后的派工工单列表，不含 `COMPLETED` 和 `INSPECT_FAILED` 状态工单
- **备注（关键逻辑）：** 分别调用 `findByDirectUserId` 和 `findByTeamMemberId` 获取两个列表，以直接派工为基准，追加团队派工中 ID 未出现在直接派工列表中的工单（`Stream.noneMatch` 判重）。

##### getWorkOrderById(Long id)
- **功能：** 通过主键查询工单，校验逻辑删除状态后返回含工序列表的完整 DTO。
- **参数：**
  - `id` (Long)：工单主键 ID
- **返回值：** `WorkOrderDTO` — 工单详情
- **异常：** `BusinessException` — 工单不存在或 `deleted == 1` 时抛出

##### listAllWorkOrders(int page, int size, String status)
- **功能：** 分页查询所有未删除工单，支持按 `status` 精确过滤，按优先级降序、创建时间降序排列。
- **参数：**
  - `page` (int)：页码（从 1 开始）
  - `size` (int)：每页记录数
  - `status` (String)：状态过滤，为 null 或空白时不过滤
- **返回值：** `List<WorkOrderDTO>` — 当前页工单 DTO 列表

##### getWorkOrderByBarcode(String barcode, Long userId)
- **功能：** 将条码值视为工单号，精确匹配查询工单，适用于扫码操作场景。
- **参数：**
  - `barcode` (String)：条码字符串，等同于 `orderNumber`
  - `userId` (Long)：当前用户 ID（预留，当前未参与查询逻辑）
- **返回值：** `WorkOrderDTO` — 匹配的工单详情
- **异常：** `BusinessException` — 条码对应工单不存在时抛出

##### toDTO(WorkOrder workOrder)（private）
- **功能：** 将 `WorkOrder` 实体转换为 `WorkOrderDTO`，同时查询并填充关联的工序摘要列表。
- **参数：**
  - `workOrder` (WorkOrder)：工单实体对象
- **返回值：** `WorkOrderDTO` — 含完整字段映射及工序摘要列表的 DTO
- **备注：** `remainingQuantity` = `plannedQuantity - completedQuantity`；工序列表通过 `findByWorkOrderId` 查询填充（N+1 问题存在）。

---

### 3.3 WorkOrderMapper

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/workorder/repository/WorkOrderMapper.java`
**类型：** Repository / Mapper
**职责：** 继承 MyBatis-Plus `BaseMapper<WorkOrder>`，扩展三个自定义查询方法实现双路径派工场景的数据检索。

#### 方法列表

| 方法名 | 返回类型 | 功能描述 |
|--------|---------|---------|
| `findByOrderNumber(String)` | `Optional<WorkOrder>` | 按工单号查询未删除工单（default 方法） |
| `findByDirectUserId(Long)` | `List<WorkOrder>` | 查询 USER 类型派工给指定用户的工单 |
| `findByTeamMemberId(Long)` | `List<WorkOrder>` | 查询 TEAM 类型派工且用户属于该团队的工单 |

#### 方法详情

##### findByDirectUserId(@Param("userId") Long userId)
- **功能：** 多表联查，找出通过"直接用户派工"（`assignment_type = 'USER'`）分配给指定用户的所有活跃工单。
- **参数：**
  - `userId` (Long)：目标用户 ID
- **返回值：** `List<WorkOrder>` — 符合条件的工单列表（已去重、排序）
- **SQL 核心逻辑：**
  ```sql
  SELECT DISTINCT wo.* FROM work_orders wo
  JOIN operations op ON op.work_order_id = wo.id AND op.deleted = 0
  JOIN operation_assignments oa ON oa.operation_id = op.id AND oa.deleted = 0
  WHERE oa.assignment_type = 'USER' AND oa.user_id = #{userId}
    AND wo.deleted = 0 AND wo.status NOT IN ('COMPLETED', 'INSPECT_FAILED')
  ORDER BY wo.priority DESC, wo.planned_start_time ASC
  ```

##### findByTeamMemberId(@Param("userId") Long userId)
- **功能：** 四表联查，找出通过"团队派工"且用户属于被派工团队的所有活跃工单。
- **参数：**
  - `userId` (Long)：目标用户 ID（以团队成员身份参与查询）
- **返回值：** `List<WorkOrder>` — 符合条件的工单列表
- **SQL 核心逻辑：**
  ```sql
  SELECT DISTINCT wo.* FROM work_orders wo
  JOIN operations op ON op.work_order_id = wo.id AND op.deleted = 0
  JOIN operation_assignments oa ON oa.operation_id = op.id AND oa.deleted = 0
  JOIN team_members tm ON tm.team_id = oa.team_id AND tm.deleted = 0
  WHERE oa.assignment_type = 'TEAM' AND tm.user_id = #{userId}
    AND wo.deleted = 0 AND wo.status NOT IN ('COMPLETED', 'INSPECT_FAILED')
  ORDER BY wo.priority DESC, wo.planned_start_time ASC
  ```

---

### 3.4 WorkOrder

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/workorder/entity/WorkOrder.java`
**类型：** Entity（MyBatis-Plus 实体类）
**职责：** 映射数据库表 `work_orders`，承载工单所有持久化字段。

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `id` | `Long` | 主键，自增 |
| `orderNumber` | `String` | 工单编号，业务唯一键 |
| `orderType` | `String` | 工单类型 |
| `productCode` | `String` | 产品编码 |
| `productName` | `String` | 产品名称 |
| `plannedQuantity` | `BigDecimal` | 计划数量 |
| `completedQuantity` | `BigDecimal` | 已完成数量 |
| `status` | `String` | 工单状态 |
| `priority` | `Integer` | 优先级，越大越高 |
| `plannedStartTime` | `LocalDateTime` | 计划开始时间 |
| `plannedEndTime` | `LocalDateTime` | 计划结束时间 |
| `actualStartTime` | `LocalDateTime` | 实际开始时间 |
| `actualEndTime` | `LocalDateTime` | 实际结束时间 |
| `workshop` | `String` | 所属车间 |
| `productionLine` | `String` | 所属生产线 |
| `notes` | `String` | 备注信息 |
| `deleted` | `Integer` | `@TableLogic`，逻辑删除标记 |
| `createdAt` | `LocalDateTime` | 创建时间（自动填充） |
| `updatedAt` | `LocalDateTime` | 更新时间（自动填充） |
| `createdBy` | `Long` | 创建人用户 ID |

---

### 3.5 WorkOrderDTO

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/workorder/dto/WorkOrderDTO.java`
**类型：** DTO（数据传输对象）
**职责：** 工单信息向外暴露的数据载体，屏蔽审计字段，增加计算字段 `remainingQuantity` 和聚合的工序摘要列表 `operations`。包含内部类 `OperationSummary`（工序摘要）。

---

### 3.6 CreateWorkOrderRequest

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/workorder/dto/CreateWorkOrderRequest.java`
**类型：** DTO（请求体）
**职责：** 封装创建工单的请求参数，通过内部类 `OperationInput` 支持在创建工单时同步批量创建工序。

**主要校验规则：**
- `orderNumber`：`@NotBlank`
- `orderType`：`@NotBlank`
- `plannedQuantity`：`@NotNull` + `@DecimalMin("0.01")`

---

### 3.7 AssignWorkOrderRequest

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/workorder/dto/AssignWorkOrderRequest.java`
**类型：** DTO（请求体）
**职责：** 封装工序派工操作的请求参数，支持 USER 和 TEAM 两种派工模式。

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `operationId` | `Long` | 目标工序 ID |
| `assignmentType` | `String` | `USER` 或 `TEAM` |
| `userIds` | `List<Long>` | USER 模式下的目标用户 ID 列表 |
| `teamIds` | `List<Long>` | TEAM 模式下的目标团队 ID 列表 |

---

## 4. 模块内调用关系

```
AdminWorkOrderController
    ├─ createWorkOrder   → WorkOrderService.createWorkOrder
    │                         ├─ WorkOrderMapper.findByOrderNumber (唯一性校验)
    │                         ├─ WorkOrderMapper.insert
    │                         └─ OperationMapper.insert × N
    │
    ├─ listWorkOrders    → WorkOrderService.listAllWorkOrders
    │                         └─ WorkOrderMapper.selectPage → toDTO × N → OperationMapper.findByWorkOrderId
    │
    ├─ getWorkOrder      → WorkOrderService.getWorkOrderById
    │                         └─ WorkOrderMapper.selectById → toDTO → OperationMapper.findByWorkOrderId
    │
    └─ assignWorkOrder   → WorkOrderService.assignWorkOrder
                              ├─ OperationMapper.selectById (工序存在性校验)
                              └─ OperationAssignmentMapper.insert × N

WorkOrderService.getAssignedWorkOrders(userId)
    ├─ WorkOrderMapper.findByDirectUserId(userId)    ← 直接派工路径
    ├─ WorkOrderMapper.findByTeamMemberId(userId)    ← 团队派工路径
    ├─ Stream 去重 + 排序
    └─ toDTO × N → OperationMapper.findByWorkOrderId
```

**跨模块依赖：**

| 依赖类 | 所在包 |
|--------|--------|
| `OperationMapper` | `modules.operation.repository` |
| `OperationAssignmentMapper` | `modules.operation.repository` |
| `SecurityUtils` | `common.util` |

---

## 5. 重要业务逻辑说明

### 5.1 双路径派工查询（核心逻辑）

系统支持两种工序派工方式，工人查询自己的工单时需同时覆盖两条路径：

| 路径 | 描述 |
|------|------|
| **直接派工（USER）** | `assignment_type='USER'` AND `user_id=#{userId}` |
| **团队派工（TEAM）** | `assignment_type='TEAM'` JOIN `team_members` WHERE `user_id=#{userId}` |

合并逻辑：以直接派工列表为基础，追加团队派工中 ID 不重复的工单，最后统一按 `priority DESC`、`plannedStartTime ASC` 排序。两条 SQL 均在数据库侧排除终态工单。

### 5.2 工单创建事务与工序自动编号

`@Transactional` 保证工单主记录与所有工序在同一事务中提交。工序编号规则：`{工单号}-OP{三位序号}`，如 `WO-2024-001-OP001`。

### 5.3 工单状态枚举

| 状态值 | 含义 |
|--------|------|
| `NOT_STARTED` | 未开始（初始状态） |
| `STARTED` | 进行中 |
| `REPORTED` | 已报工 |
| `INSPECT_PASSED` | 质检通过 |
| `INSPECT_FAILED` | 质检失败 |
| `COMPLETED` | 已完成 |

`COMPLETED` 和 `INSPECT_FAILED` 状态的工单被派工查询 SQL 自动过滤，不出现在工人的工单列表中。

### 5.4 N+1 查询问题说明
`toDTO()` 每次调用均触发一次 `OperationMapper.findByWorkOrderId()` 查询。在分页场景下每页 N 条工单会执行 1+N 次数据库查询，建议后续改为批量 IN 查询优化。

### 5.5 逻辑删除机制
`WorkOrder.deleted` 字段标注 `@TableLogic`，MyBatis-Plus 自动在 `BaseMapper` 查询中追加 `AND deleted = 0`。自定义 `@Select` SQL 需手动添加 `wo.deleted = 0` 及关联表的 `deleted = 0` 条件。

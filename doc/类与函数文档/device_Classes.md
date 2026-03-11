# device 模块 — 类与函数文档

## 1. 模块概述

device（设备）模块是面向工厂手持工业设备的后端服务模块，承担以下核心职责：

- **设备身份管理**：维护工业手持设备的基本信息（编码、类型、MAC 地址、状态等），并记录设备的登录历史。
- **设备端 HTTP 接口**：通过 `DeviceController` 统一对外暴露 RESTful API，供手持终端调用，涵盖登录认证、工单查询、生产报工、条码扫描、安灯呼叫等完整业务流程。
- **数据持久化**：通过 MyBatis-Plus 的 `BaseMapper` 扩展接口 `DeviceMapper` 对 `devices` 数据库表执行 CRUD 操作。

模块包路径：`com.xiaobai.workorder.modules.device`

| 子包 | 说明 |
|------|------|
| `controller` | HTTP 接口层，对手持设备暴露 REST API |
| `entity` | 实体类，映射数据库表 |
| `repository` | Mapper 接口，负责数据库访问 |
| `service` | 业务逻辑层，处理设备相关业务规则 |

---

## 2. 核心类列表

| 类名 | 所在文件 | 职责描述 |
|------|---------|---------|
| `DeviceController` | `controller/DeviceController.java` | REST 控制器，聚合多个业务服务，向手持工业设备提供统一的 HTTP API 入口，涵盖登录、工单、报工、条码扫描、安灯呼叫等接口 |
| `Device` | `entity/Device.java` | 设备实体类，映射数据库表 `devices`，存储设备编码、类型、MAC 地址、状态及登录记录等信息 |
| `DeviceMapper` | `repository/DeviceMapper.java` | MyBatis-Plus Mapper 接口，继承 `BaseMapper<Device>`，提供对 `devices` 表的基础 CRUD 及按编码查询能力 |
| `DeviceService` | `service/DeviceService.java` | 设备业务服务类，封装设备状态校验、登录记录更新、按编码查询等核心业务逻辑 |

---

## 3. 类详细说明

### 3.1 DeviceController

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/device/controller/DeviceController.java`
**类型：** Controller（REST 控制器）
**职责：** 作为手持工业设备与后端系统之间的统一入口，聚合 `AuthService`、`WorkOrderService`、`ReportService`、`CallService` 四个业务服务，将设备端发起的 HTTP 请求路由至对应的业务逻辑。所有接口均以 `/api/device` 为根路径。

#### 方法列表

| 方法名 | 访问修饰符 | 返回类型 | 功能描述 |
|--------|-----------|---------|---------|
| `login` | `public` | `ApiResponse<LoginResponse>` | 设备端员工工号密码登录 |
| `getAssignedWorkOrders` | `public` | `ApiResponse<List<WorkOrderDTO>>` | 获取当前登录工人被分配的工单列表 |
| `startWork` | `public` | `ApiResponse<Void>` | 开始指定工序的生产作业 |
| `reportWork` | `public` | `ApiResponse<ReportRecord>` | 上报工序完工数量 |
| `scanStart` | `public` | `ApiResponse<WorkOrderDTO>` | 扫码开工，自动解析工序并开始作业 |
| `scanReport` | `public` | `ApiResponse<WorkOrderDTO>` | 扫码报工，自动解析工序并提交报工 |
| `undoReport` | `public` | `ApiResponse<Void>` | 撤销上一次报工记录 |
| `callAndon` | `public` | `ApiResponse<CallRecord>` | 触发安灯呼叫 |
| `callInspection` | `public` | `ApiResponse<CallRecord>` | 触发质检呼叫 |
| `callTransport` | `public` | `ApiResponse<CallRecord>` | 触发物流/搬运呼叫 |
| `buildCallRequest` | `private` | `CallRequest` | 从请求体 Map 中构建 `CallRequest` 对象（内部公共方法） |

#### 方法详情

##### login(@Valid @RequestBody LoginRequest request)
- **功能：** 接收设备端登录凭据（工号 + 密码），委托 `AuthService.login()` 完成认证，返回含 Token 的登录响应。
- **HTTP 方法：** `POST /api/device/login`
- **参数：**
  - `request` (LoginRequest)：登录请求体，包含工号和密码
- **返回值：** `ApiResponse<LoginResponse>` — 封装 Token 及用户信息
- **备注：** 此接口无需认证即可访问，是设备端使用所有其他接口的前提。

##### getAssignedWorkOrders()
- **功能：** 从 Spring Security 上下文获取当前用户 ID，查询分配给该工人的全部工单列表。
- **HTTP 方法：** `GET /api/device/work-orders`
- **参数：** 无（用户 ID 从 `SecurityUtils.getCurrentUserId()` 自动获取）
- **返回值：** `ApiResponse<List<WorkOrderDTO>>` — 工单列表，含工单基本信息及工序状态

##### startWork(@RequestBody Map<String, Long> body)
- **功能：** 接收含 `operationId` 的请求体，调用 `ReportService.startWork()` 将工序标记为"已开始"。
- **HTTP 方法：** `POST /api/device/start`
- **参数：**
  - `body` (Map<String, Long>)：必须包含键 `operationId`
- **返回值：** `ApiResponse<Void>` — 操作成功
- **异常：** `BusinessException` — `operationId` 为 null 时抛出

##### reportWork(@Valid @RequestBody ReportRequest request)
- **功能：** 接收报工请求，调用 `ReportService.reportWork()` 提交工序报工记录。
- **HTTP 方法：** `POST /api/device/report`
- **参数：**
  - `request` (ReportRequest)：报工请求体，包含工序 ID 和完工数量
- **返回值：** `ApiResponse<ReportRecord>` — 创建的报工记录实体

##### scanStart(@RequestBody Map<String, String> body)
- **功能：** 通过条码扫描开工。解析条码对应工单，自动找到第一个 `NOT_STARTED` 或 `STARTED` 状态的工序并触发开工，返回工单最新状态。
- **HTTP 方法：** `POST /api/device/scan/start`
- **参数：**
  - `body` (Map<String, String>)：必须包含键 `barcode`
- **返回值：** `ApiResponse<WorkOrderDTO>` — 包含最新工序状态的工单数据
- **异常：** `BusinessException` — `barcode` 为 null 或空白时抛出
- **备注：** 使用 `Stream.filter().findFirst().ifPresent()` 实现"一码开工"逻辑，简化设备端操作步骤。

##### scanReport(@RequestBody Map<String, String> body)
- **功能：** 通过条码扫描报工。找到第一个 `STARTED` 或 `NOT_STARTED` 工序，自动提交报工，返回工单最新状态。
- **HTTP 方法：** `POST /api/device/scan/report`
- **参数：**
  - `body` (Map<String, String>)：必须包含键 `barcode`
- **返回值：** `ApiResponse<WorkOrderDTO>` — 包含最新工序状态的工单数据

##### undoReport(@Valid @RequestBody UndoReportRequest request)
- **功能：** 撤销工人最近一次提交的报工记录，恢复工序上一个状态。
- **HTTP 方法：** `POST /api/device/report/undo`
- **参数：**
  - `request` (UndoReportRequest)：撤销请求体
- **返回值：** `ApiResponse<Void>` — 操作成功

##### callAndon / callInspection / callTransport(@RequestBody Map<String, Object> body)
- **功能：** 分别触发安灯、质检、物流呼叫。
- **HTTP 方法：** `POST /api/device/call/{andon|inspection|transport}`
- **参数：**
  - `body`：必须包含 `workOrderId`，可选包含 `operationId` 和 `description`
- **返回值：** `ApiResponse<CallRecord>` — 创建的呼叫记录
- **备注：** 三者共用私有方法 `buildCallRequest(body, callType)` 构建请求对象。

##### buildCallRequest(Map<String, Object> body, String callType)（private）
- **功能：** 从通用请求体 Map 提取字段，构建类型安全的 `CallRequest` 对象，供三个呼叫接口复用。
- **参数：**
  - `body` (Map<String, Object>)：原始请求体
  - `callType` (String)：`"ANDON"` / `"INSPECTION"` / `"TRANSPORT"`
- **返回值：** `CallRequest` — 填充好字段的呼叫请求对象
- **异常：** `BusinessException` — `workOrderId` 为 null 时抛出
- **备注：** 兼容请求体中 String 和 Number 两种 JSON 类型的 ID 值。

---

### 3.2 Device

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/device/entity/Device.java`
**类型：** Entity（实体类）
**职责：** 映射数据库表 `devices`，使用 MyBatis-Plus 注解实现自动主键、逻辑删除和自动填充时间戳等功能。

#### 字段列表

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `id` | `Long` | 主键，数据库自增 |
| `deviceCode` | `String` | 设备唯一编码 |
| `deviceName` | `String` | 设备名称 |
| `deviceType` | `String` | 设备类型 |
| `macAddress` | `String` | 设备 MAC 地址 |
| `status` | `String` | 设备状态（`ACTIVE` 等） |
| `lastLoginAt` | `LocalDateTime` | 最后一次登录时间 |
| `lastLoginUserId` | `Long` | 最后一次登录的用户 ID |
| `deleted` | `Integer` | 逻辑删除标记（0=正常/1=已删除） |
| `createdAt` | `LocalDateTime` | 创建时间，插入时自动填充 |
| `updatedAt` | `LocalDateTime` | 更新时间，插入/更新时自动填充 |

---

### 3.3 DeviceMapper

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/device/repository/DeviceMapper.java`
**类型：** Mapper / Repository（数据访问接口）
**职责：** 继承 MyBatis-Plus `BaseMapper<Device>`，扩展了按设备编码查询的业务能力。

#### 方法列表

| 方法名 | 访问修饰符 | 返回类型 | 功能描述 |
|--------|-----------|---------|---------|
| `findByDeviceCode` | `public default` | `Optional<Device>` | 按设备编码查询未删除的设备记录 |

#### 方法详情

##### findByDeviceCode(String deviceCode)
- **功能：** 根据设备编码精确查询一条未被逻辑删除的设备记录。
- **参数：**
  - `deviceCode` (String)：设备唯一业务编码
- **返回值：** `Optional<Device>` — 找到则返回设备实体，否则返回空
- **备注：** 使用 `LambdaQueryWrapper` 构建查询，附加 `deleted = 0` 条件。

---

### 3.4 DeviceService

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/device/service/DeviceService.java`
**类型：** Service（业务服务类）
**职责：** 封装设备相关的核心业务逻辑，包含设备激活状态校验、登录记录更新、按编码查询设备。

#### 方法列表

| 方法名 | 访问修饰符 | 返回类型 | 功能描述 |
|--------|-----------|---------|---------|
| `recordLogin` | `public` | `void` | 校验设备状态并记录设备的最新登录信息 |
| `findByCode` | `public` | `Device` | 按设备编码查询设备，不存在时抛出业务异常 |

#### 方法详情

##### recordLogin(String deviceCode, Long userId)
- **功能：** 查询设备 → 校验状态为 `ACTIVE` → 更新 `lastLoginAt` 和 `lastLoginUserId` → 持久化并打印日志。
- **参数：**
  - `deviceCode` (String)：发起登录的设备编码
  - `userId` (Long)：成功登录的用户 ID
- **返回值：** `void`
- **异常：**
  - `BusinessException` — 设备编码不存在时抛出 `"Device not found: {deviceCode}"`
  - `BusinessException` — 设备状态不为 `ACTIVE` 时抛出 `"Device is not active: {deviceCode}"`
- **备注：** 标注 `@Transactional`，保证查询与更新在同一事务中执行。

##### findByCode(String deviceCode)
- **功能：** 封装 `DeviceMapper.findByDeviceCode()`，不存在时主动抛出业务异常。
- **参数：**
  - `deviceCode` (String)：要查询的设备编码
- **返回值：** `Device` — 对应的设备实体对象
- **异常：** `BusinessException` — `"Device not found: {deviceCode}"`

---

## 4. 模块内调用关系

```
HTTP 请求（手持设备）
        │
        ▼
┌──────────────────────────────────────────────────────┐
│                  DeviceController                    │
│  POST /login         → AuthService.login()           │
│  GET  /work-orders   → WorkOrderService.getAssigned  │
│  POST /start         → ReportService.startWork()     │
│  POST /report        → ReportService.reportWork()    │
│  POST /scan/start    → WorkOrderService/ReportService│
│  POST /scan/report   → WorkOrderService/ReportService│
│  POST /report/undo   → ReportService.undoReport()    │
│  POST /call/*        → buildCallRequest → CallService│
└──────────────────────────────────────────────────────┘

device 模块内部：
DeviceService → DeviceMapper → 数据库 devices 表
```

**跨模块依赖：**

| 被依赖模块 | 依赖的类/接口 |
|-----------|-------------|
| `auth` | `AuthService`、`LoginRequest`、`LoginResponse` |
| `workorder` | `WorkOrderService`、`WorkOrderDTO` |
| `report` | `ReportService`、`ReportRequest`、`UndoReportRequest`、`ReportRecord` |
| `call` | `CallService`、`CallRequest`、`CallRecord` |
| `common` | `ApiResponse`、`BusinessException`、`SecurityUtils` |

---

## 5. 重要业务逻辑说明

### 5.1 扫码一码操作（自动工序解析）

`scanStart` 和 `scanReport` 实现"一码操作"设计：工人只需扫描工单条码，系统自动定位第一个未完成工序，无需手动选择工序 ID。

```java
workOrder.getOperations().stream()
    .filter(op -> "NOT_STARTED".equals(op.getStatus()) || "STARTED".equals(op.getStatus()))
    .findFirst()
    .ifPresent(op -> reportService.startWork(op.getId(), userId));
```

若所有工序均已完成则静默跳过（不报错）。

### 5.2 设备激活状态校验
`DeviceService.recordLogin()` 强制要求设备状态为 `ACTIVE` 才允许登录，未激活设备将被拒绝，确保只有经过管理员授权的设备才能接入系统。

### 5.3 三类呼叫的统一处理模式
`callAndon`、`callInspection`、`callTransport` 共用私有方法 `buildCallRequest()`，通过传入不同 `callType` 区分呼叫类型，消除重复代码。

### 5.4 逻辑删除保证数据安全
`Device` 实体通过 `@TableLogic` 注解的 `deleted` 字段实现逻辑删除，所有查询自动过滤已删除记录。

### 5.5 事务一致性保障
`DeviceService.recordLogin()` 标注 `@Transactional`，将"查询 → 校验 → 更新"三步包裹在单一事务中，防止并发场景下的数据竞态问题。

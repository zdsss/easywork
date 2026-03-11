# statistics 模块 — 类与函数文档

## 1. 模块概述

`statistics`（统计）模块是 EasyWork 工单系统的生产数据看板模块，负责对全量工单、报工记录及工人绩效数据进行聚合计算，并通过 RESTful 接口将汇总结果暴露给管理端前端。

该模块遵循经典的三层架构：

```
Controller（接入层）
    └── StatisticsController

Service（业务层）
    └── StatisticsService

DTO（数据传输层）
    └── StatisticsDTO
         ├── WorkOrderTypeStat（内部静态类）
         └── WorkerStat（内部静态类）
```

模块对外只暴露一个 HTTP 端点（`GET /api/admin/statistics/dashboard`），所有依赖均通过构造器注入（Lombok `@RequiredArgsConstructor`）。

---

## 2. 核心类列表

| 类名 | 所在文件 | 职责描述 |
|------|---------|---------|
| `StatisticsController` | `controller/StatisticsController.java` | HTTP 入口控制器，接收管理端请求并委托 Service 层完成统计计算，返回统一响应体 |
| `StatisticsService` | `service/StatisticsService.java` | 核心业务逻辑层，跨越 workorder、report、user 三个模块的 Mapper 完成数据聚合与指标计算 |
| `StatisticsDTO` | `dto/StatisticsDTO.java` | 仪表盘聚合结果的数据传输对象，包含工单概况、完成率、分类统计及工人绩效四大维度 |
| `StatisticsDTO.WorkOrderTypeStat` | `dto/StatisticsDTO.java`（内部类） | 按工单类型聚合的统计结果子对象 |
| `StatisticsDTO.WorkerStat` | `dto/StatisticsDTO.java`（内部类） | 按工人维度聚合的绩效子对象 |

---

## 3. 类详细说明

### 3.1 StatisticsController

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/statistics/controller/StatisticsController.java`
**类型：** Controller（REST 控制器）
**职责：** 统计模块的 HTTP 入口，将前端仪表盘数据请求转发给 `StatisticsService`，并将计算结果包装为统一的 `ApiResponse` 格式返回。

#### 方法列表

| 方法名 | 访问修饰符 | 返回类型 | 功能描述 |
|--------|-----------|---------|---------|
| `getDashboard()` | `public` | `ApiResponse<StatisticsDTO>` | 获取仪表盘统计概览数据 |

#### 方法详情

##### getDashboard()
- **功能：** 处理 `GET /api/admin/statistics/dashboard` 请求，调用 Service 获取统计数据并以 `ApiResponse.success(...)` 封装返回。
- **参数：** 无
- **返回值：** `ApiResponse<StatisticsDTO>` — 包含统一状态码与仪表盘聚合数据的响应体
- **备注：** 只读查询，无写操作。

---

### 3.2 StatisticsService

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/statistics/service/StatisticsService.java`
**类型：** Service（业务服务层）
**职责：** 统计模块的核心业务类，依赖跨模块的三个 MyBatis-Plus Mapper，在内存中完成工单状态分组、完成率计算、工单类型聚合及工人报工绩效聚合。

#### 方法列表

| 方法名 | 访问修饰符 | 返回类型 | 功能描述 |
|--------|-----------|---------|---------|
| `getDashboardStats()` | `public` | `StatisticsDTO` | 聚合计算全量仪表盘统计指标并返回 |

#### 方法详情

##### getDashboardStats()
- **功能：** 一次性查询全量工单与报工记录，在 JVM 内存中完成多维度流式聚合，生成包含工单概况、完成率、分类统计、工人绩效的完整仪表盘数据对象。
- **参数：** 无
- **返回值：** `StatisticsDTO` — 填充了所有统计维度数据的仪表盘结果对象
- **异常：** 无显式声明；底层 MyBatis-Plus 查询失败时将抛出运行时异常
- **备注：** 内部分四个阶段执行，详见第 5 节重要业务逻辑说明。

---

### 3.3 StatisticsDTO

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/statistics/dto/StatisticsDTO.java`
**类型：** DTO（数据传输对象）
**职责：** 仪表盘接口的顶层返回数据结构，聚合了工单数量概况、整体完成率、工单类型分类列表及工人绩效列表四个维度。

#### 字段列表

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `totalWorkOrders` | `Long` | 工单总数（未删除） |
| `notStartedCount` | `Long` | 状态为 `NOT_STARTED` 的工单数 |
| `startedCount` | `Long` | 状态为 `STARTED` 的工单数 |
| `reportedCount` | `Long` | 状态为 `REPORTED` 的工单数 |
| `completedCount` | `Long` | 状态为 `INSPECT_PASSED` 或 `COMPLETED` 的工单数 |
| `overallCompletionRate` | `BigDecimal` | 整体完成率（百分比，保留 1 位小数） |
| `typeStats` | `List<WorkOrderTypeStat>` | 按工单类型聚合的统计列表 |
| `workerStats` | `List<WorkerStat>` | 按工人聚合的绩效统计列表 |

---

### 3.4 StatisticsDTO.WorkOrderTypeStat（内部静态类）

**职责：** 承载单一工单类型维度的聚合统计结果。

#### 字段列表

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `orderType` | `String` | 工单类型标识（PRODUCTION、INSPECTION、TRANSPORT、ANDON） |
| `count` | `Long` | 该类型工单总数 |
| `completedCount` | `Long` | 该类型已完成工单数（含 REPORTED、INSPECT_PASSED、COMPLETED） |

---

### 3.5 StatisticsDTO.WorkerStat（内部静态类）

**职责：** 承载单名工人的报工绩效汇总结果。

#### 字段列表

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `userId` | `Long` | 工人用户 ID |
| `realName` | `String` | 工人真实姓名 |
| `employeeNumber` | `String` | 工号 |
| `reportCount` | `Long` | 有效报工记录条数 |
| `totalReported` | `BigDecimal` | 报工数量累计总和 |

---

## 4. 模块内调用关系

```
HTTP GET /api/admin/statistics/dashboard
        │
        ▼
StatisticsController.getDashboard()
        │  调用
        ▼
StatisticsService.getDashboardStats()
        │
        ├─── WorkOrderMapper.selectList(LambdaQueryWrapper)
        │         └── Stream 聚合：总数、各状态计数、完成率、类型分组
        │
        ├─── ReportRecordMapper.selectList(LambdaQueryWrapper)
        │         └── 按 userId 分组
        │
        └─── UserMapper.selectById(userId)  ← 循环调用（每位工人一次）
                  └── 构建 WorkerStat
        │
        ▼
返回 StatisticsDTO → ApiResponse<StatisticsDTO>
```

**跨模块依赖：**

| 被依赖模块 | 被依赖类 |
|-----------|---------|
| `workorder` | `WorkOrder`、`WorkOrderMapper` |
| `report` | `ReportRecord`、`ReportRecordMapper` |
| `user` | `User`、`UserMapper` |

---

## 5. 重要业务逻辑说明

### 5.1 工单完成率的计算口径
完成率分子为 `reportedCount + completedCount`（含 `REPORTED`、`INSPECT_PASSED`、`COMPLETED` 三种状态），保留 1 位小数，HALF_UP 四舍五入，工单总数为 0 时返回 0.0 防止除零。

### 5.2 类型统计与基础统计的已完成口径差异
基础统计 `completedCount` 仅含 `INSPECT_PASSED` 和 `COMPLETED`；类型统计 `WorkOrderTypeStat.completedCount` 额外包含 `REPORTED`，与整体完成率分子口径一致，使用时需注意区分。

### 5.3 工人绩效统计中的 N+1 查询问题
当前实现对每位不同工人各执行一次 `userMapper.selectById(userId)`，属于典型 N+1 查询，工人数量多时存在性能瓶颈。建议优化为 `userMapper.selectBatchIds(byUser.keySet())` 批量查询。

### 5.4 全量内存加载策略
全量工单与报工记录一次性加载至内存后用 Stream 多轮过滤，适合万级以内数据量。数据量增大后建议改为数据库端 `GROUP BY + COUNT/SUM` 聚合查询。

### 5.5 软删除过滤
工单查询通过 `deleted = 0` 过滤逻辑删除数据；报工记录查询同时过滤 `deleted = 0` 与 `isUndone = false`，排除已撤销报工，确保统计准确性。

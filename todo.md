# 待办任务（2026-03-12）

> 来源：代码库审视报告，113 个测试全绿后的下一步工作。

---

## 任务 A：工单产品名搜索全链路修复（优先级：高）

**问题**：搜索框存在但完全无效，后端也不支持此参数。

### 后端
- [ ] `WorkOrderService.listAllWorkOrders()` 签名增加 `String productName` 参数
- [ ] LambdaQueryWrapper 增加 `like` 条件（模糊匹配 `productName`）
- [ ] `AdminWorkOrderController.listWorkOrders()` 新增 `@RequestParam(required = false) String productName`，传给 Service

### 前端（easywork-admin）
- [ ] `WorkOrderListView.vue` 的 `loadData()` 中追加 `productName` 参数到请求

### 测试
- [ ] `WorkOrderServiceTest` 新增 `should_filterByProductName_when_productNameProvided`

**验收**：输入产品名点击查询，列表按模糊匹配过滤；`mvn test` 全绿。

---

## 任务 B：Dashboard 补充统计字段（优先级：中）

**问题**：后端 `StatisticsDTO` 已有字段，前端未展示。

### 前端（easywork-admin）
- [ ] `DashboardView.vue` stats 数组增加第 5 张卡片：未开始工单数（`notStartedCount`）
- [ ] 新增完成率进度条：`overallCompletionRate`（BigDecimal，需 `* 100` 取整）
- [ ] 新增工单类型分布表格：展示 `typeStats`（工单类型 / 总数 / 已完成数）

**验收**：Dashboard 显示 5 个卡片（含未开始）、完成率进度条、类型分布表格。

---

## 任务 C：工人端质检结果详情（优先级：低）

**问题**：质检通过/失败后，工人端看不到任何质检详情。

### 后端
- [ ] `InspectionService` 新增 `getLatestByWorkOrderId(Long workOrderId)` 方法（按 `inspectedAt` 倒序取第一条）
- [ ] `DeviceController` 新增接口：`GET /api/device/inspections/{workOrderId}`，权限 WORKER 或 ADMIN

### 前端（easywork-worker）
- [ ] `WorkOrderDetailView.vue` 当状态为 `INSPECT_PASSED` 或 `INSPECT_FAILED` 时显示质检结果卡片
- [ ] 卡片内容：检验员姓名、检验时间、合格数量、不合格数量、不合格原因

### 测试
- [ ] `DeviceControllerTest` 新增 `should_return200_when_getInspectionDetail`

**验收**：工单状态为质检通过/失败时，工人端详情页底部出现质检结果卡片；`mvn test` 全绿。

---

## 任务 D：补充 macOS 工具路径文档（优先级：低）

**问题**：`easywork/.claude.md` 的 macOS 工具路径栏目为"待补充"，跨平台协作时 Claude 无法在 macOS 上生成正确命令。

- [ ] 在 macOS 机器上确认 Java 21 实际路径（Homebrew 或官方安装）
- [ ] 在 macOS 机器上确认 Maven 实际路径
- [ ] 更新 `easywork/.claude.md` 第 6.4 节 macOS 工具路径表格
- [ ] 同步更新根目录 `CLAUDE.md`"Windows 工具路径"章节，新增 macOS 一栏
- [ ] 提交并推送

**验收**：`easywork/.claude.md` macOS 栏有实际路径；两端（Windows/macOS）均可通过 CLAUDE.md 找到完整命令。

---

## 关键文件路径

| 文件 | 路径 |
|------|------|
| WorkOrderService | `easywork/src/main/java/com/xiaobai/workorder/modules/workorder/service/WorkOrderService.java` |
| AdminWorkOrderController | `easywork/src/main/java/com/xiaobai/workorder/modules/workorder/controller/AdminWorkOrderController.java` |
| WorkOrderServiceTest | `easywork/src/test/java/com/xiaobai/workorder/modules/workorder/service/WorkOrderServiceTest.java` |
| WorkOrderListView（管理端）| `easywork-admin/src/views/workorder/WorkOrderListView.vue` |
| DashboardView | `easywork-admin/src/views/DashboardView.vue` |
| InspectionService | `easywork/src/main/java/com/xiaobai/workorder/modules/inspection/service/InspectionService.java` |
| DeviceController | `easywork/src/main/java/com/xiaobai/workorder/modules/device/controller/DeviceController.java` |
| DeviceControllerTest | `easywork/src/test/java/com/xiaobai/workorder/modules/device/controller/DeviceControllerTest.java` |
| WorkOrderDetailView（工人端）| `easywork-worker/src/views/WorkOrderDetailView.vue` |

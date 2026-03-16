# Todo

## 待办

（当前无待办项）

---

## 已完成（2026-03-16）

### CTO 审查报告 — P0 修复
- [x] **P0-A** `OperationDependencyService.getPredecessors()` 查询方向完全反了：由 `predecessorOperationId = ?` 改为 `operationId = ?`，同步修复 `getDependencies()` 缺少 `deleted = 0` 过滤
- [x] **P0-B** `useHardwareInput.js` 扫码枪 Enter 在输入框聚焦时无法触发：改为普通字符始终累积缓冲区（透传给输入框），扫码枪 Enter 即使聚焦也拦截调用 `onScan`；新增 Backspace 回调
- [x] **P0-D** `WorkOrderService.completeWorkOrder()` 仅允许 INSPECT_PASSED → COMPLETED：按 orderType 分支，非生产工单允许 REPORTED → COMPLETED

### CTO 审查报告 — P1 修复
- [x] **P1-A** 工人端报工数量快捷键输入：字符串拼接改为整数追加（`current * 10 + digit`），Backspace 删末位数字
- [x] **P1-B** 工人端工序列表方向键导航：实现 `onNavigate` 回调，`activeOpIndex` 追踪焦点工序卡片，`scrollIntoView` + 蓝色边框高亮，快捷键 1/2 优先对焦点工序操作
- [x] **P1-C** 扫码班组匹配补全：新增 `findEarliestNotStartedByUserAndWorkOrder` / `findEarliestNotStartedByTeamUserAndWorkOrder`，`resolveOperationForStart()` 准确定位最前道未开工工序
- [x] **P1-D** 管理端工单创建依赖失败静默：捕获每条依赖的异常，最终以 `ElMessage.warning` 列出失败项名称
- [x] **P1-E** 离线队列失败通知：`processQueue()` 返回 `{ processed, failed, skipped }`，联网后显示失败操作名称列表
- [x] **P1 Bug** `useHardwareInput.js` capture phase 输入框拦截（已在 P0-B 统一修复）

### CTO 审查报告 — P2 质量改进
- [x] **P2-A** `WorkOrder` 实体添加 `@Version` 乐观锁字段 + 迁移 V1.6
- [x] **P2-B** `operation_dependencies` 添加 UNIQUE(operation_id, predecessor_operation_id) 约束 + `operation_logs.user_id` 索引（V1.6 迁移）
- [x] **P2-C** `AuditLogAspect.logOperation()` 添加 `@Transactional`，before-state 快照与业务逻辑在同一事务内；**bug fix**：`getCurrentUserId()` 改为注入 `SecurityUtils`，修复 audit log user_id 为 null 导致的 500 错误
- [x] **P2-D** `StatisticsService` `completedCount` 语义统一：dashboard 与 typeStats 均定义为 INSPECT_PASSED + COMPLETED（不含 REPORTED）
- [x] **P2-E** 管理端依赖图拓扑排序：Kahn 算法按依赖层次布局，消除节点重叠
- [x] **P2-F** `DashboardView` ECharts 添加 `window.resize` 监听，防止图表超出容器

### CTO 审查报告 — P3 架构改进
- [x] **P3-C** 管理端依赖配置界面移除 CONDITIONAL 选项（半实现功能，避免误用）

### 前端兼容性修复
- [x] **管理端 Vite 版本**：`easywork-admin` `package.json` 中 vite 由 `^7.3.1` 降为 `^5.4.0`（Node 18 不支持 `crypto.hash`，Vite 7.x 强依赖此 API），`@vitejs/plugin-vue` 同步降为 `^5.0.0`

---

## 已完成（2026-03-15）

- [x] **P1** `useHardwareInput.js` — 硬件输入语义化层（扫码枪识别 50ms 阈值、方向键导航、数字快捷键 1-5、ESC 返回）
- [x] **P1** `KeyHints.vue` — 固定在 tabbar 上方的快捷键提示条
- [x] **P1** `WorkOrderListView` — 替换 usePhysicalKeys → useHardwareInput；onScan 扫码开工后刷新并跳转详情
- [x] **P1** `WorkOrderDetailView` — 数字快捷键 1=开工/2=报工/3=质检/4=撤销/5=返工；报工对话框开启时数字键追加数量；ESC 先关对话框再返回；onScan 尝试开工/报工
- [x] **P1** `ScanView` — 开工/报工模式切换（Tab 键）；onScan 自动触发；KeyHints

## 已完成（2026-03-14）

- [x] **P0** WorkOrderStatus 新增 SCRAPPED 终态（V1.5 migration）
- [x] **P0** ReportService 按 orderType 分支处理状态流转
- [x] **P0** ReportService 串行前置工序依赖检查
- [x] **P0** OperationDependencyService.getPredecessors() 查询方向 Bug 修复
- [x] **P0** DeviceController 扫码枪班组+用户优先级匹配逻辑补全
- [x] **P0** 新增 POST /api/device/inspect（检验员工人端提交质检）
- [x] **P0** InspectionService 扩展 REWORK/SCRAP 处理
- [x] **P0** 工人端报工数量预填（剩余量）+ 上限校验
- [x] **P1** 工人端检验工单显示「提交质检」按钮（替代「报工」）
- [x] **P1** 工人端 / 管理端状态标签按 orderType 映射（statusLabel.js）
- [x] **P1** T9 九键键盘集成到工人端登录页密码输入
- [x] **P1** 管理端 InspectionView 改为只读（质检操作移至工人端）
- [x] **P1** 管理端工单创建 orderType 选项修正（PRODUCTION/INSPECTION/TRANSPORT/ANDON）
- [x] **P2** force-start-before-report 配置（默认 false）
- [x] **P1** `easywork-worker/src/views/ScanView.vue` — 调用摄像头或接收扫码枪键盘输入
- [x] **P1** 路由注册 `/scan`，入口放在工单列表页顶部
- [x] **P1** 扫码后调用 `POST /device/scan/start` 或 `POST /device/scan/report`，自动跳转结果
- [x] **P1** `BatchView.vue` — 多选工序 + 批量开工/报工
- [x] **P1** 调用 `POST /device/batch/start` / `POST /device/batch/report`
- [x] **P2** `WorkOrderEditView.vue` — 编辑工单基本信息（产品名/数量/时间/备注）
- [x] **P2** 需后端新增 `PUT /api/admin/work-orders/:id` 接口
- [x] **P2** 按 orderType 分组展示完成率
- [x] **P2** 质检通过率趋势图（折线图，ECharts）
- [x] **P3** 管理端工单详情页：工序依赖关系用有向图展示（Vue Flow）

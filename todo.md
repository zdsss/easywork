# Todo

## 待办

### P1 — 工人端扫码页面
- [ ] `easywork-worker/src/views/ScanView.vue` — 调用摄像头或接收扫码枪键盘输入
- [ ] 路由注册 `/scan`，入口放在工单列表页顶部
- [ ] 扫码后调用 `POST /device/scan/start` 或 `POST /device/scan/report`，自动跳转结果

### P1 — 工人端批量操作页面
- [ ] `BatchView.vue` — 多选工序 + 批量开工/报工
- [ ] 调用 `POST /device/batch/start` / `POST /device/batch/report`

### P2 — 管理端工单编辑
- [ ] `WorkOrderEditView.vue` — 编辑工单基本信息（产品名/数量/时间/备注）
- [ ] 需后端新增 `PUT /api/admin/work-orders/:id` 接口

### P2 — 统计看板增强
- [ ] 按 orderType 分组展示完成率
- [ ] 质检通过率趋势图（折线图）

### P3 — 工序依赖可视化
- [ ] 管理端工单详情页：工序依赖关系用有向图展示（可用 Vue Flow 或 D3）

---

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

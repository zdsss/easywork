/**
 * 生成以"用户故事 + 业务链路"为主线的可视化 PDF 报告
 * 基于真实截图展示完整操作链路
 */

const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const UTILS_DIR = __dirname;
const PDF_FILE = path.join(UTILS_DIR, 'current_state_report.pdf');
const SCREENSHOTS_DIR = path.join(UTILS_DIR, 'screenshots');

function img(filename, caption) {
  const fp = path.join(SCREENSHOTS_DIR, filename);
  if (!fs.existsSync(fp)) return `<div class="img-missing">[ 截图未找到: ${filename} ]</div>`;
  const data = fs.readFileSync(fp);
  const b64 = data.toString('base64');
  return `
<figure class="screenshot">
  <img src="data:image/png;base64,${b64}" alt="${caption}">
  <figcaption>${caption}</figcaption>
</figure>`;
}

function step(num, title, description) {
  return `<div class="step"><span class="step-num">${num}</span><div class="step-content"><strong>${title}</strong><p>${description}</p></div></div>`;
}

function flowArrow() {
  return '<div class="flow-arrow">▼</div>';
}

function stateTag(label, color) {
  const colors = {
    'not_started': '#94a3b8',
    'started': '#3b82f6',
    'reported': '#f59e0b',
    'inspect_passed': '#10b981',
    'inspect_failed': '#ef4444',
    'completed': '#6366f1',
    'scrapped': '#dc2626'
  };
  const bg = colors[color] || '#94a3b8';
  return `<span class="state-tag" style="background:${bg}">${label}</span>`;
}

const html = `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<title>易工单系统 — 功能链路报告</title>
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body {
    font-family: 'Microsoft YaHei', 'PingFang SC', 'Noto Sans CJK SC', sans-serif;
    font-size: 13px;
    line-height: 1.7;
    color: #1e293b;
    background: #fff;
  }

  /* ===== 封面 ===== */
  .cover {
    height: 100vh;
    background: linear-gradient(135deg, #0f172a 0%, #1e3a5f 50%, #0f4c75 100%);
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    color: white;
    page-break-after: always;
    text-align: center;
    padding: 60px;
  }
  .cover .badge {
    background: rgba(255,255,255,0.15);
    border: 1px solid rgba(255,255,255,0.3);
    border-radius: 30px;
    padding: 6px 20px;
    font-size: 12px;
    letter-spacing: 2px;
    text-transform: uppercase;
    margin-bottom: 30px;
  }
  .cover h1 { font-size: 42px; font-weight: 700; margin-bottom: 16px; letter-spacing: 1px; }
  .cover h2 { font-size: 20px; font-weight: 300; opacity: 0.8; margin-bottom: 50px; }
  .cover .meta { display: flex; gap: 40px; margin-top: 20px; }
  .cover .meta-item { text-align: center; }
  .cover .meta-item .val { font-size: 28px; font-weight: 700; color: #60a5fa; }
  .cover .meta-item .lbl { font-size: 11px; opacity: 0.6; margin-top: 4px; }
  .cover .divider { width: 80px; height: 3px; background: #60a5fa; margin: 30px auto; border-radius: 2px; }
  .cover .date { opacity: 0.5; font-size: 12px; position: absolute; bottom: 40px; }

  /* ===== 目录 ===== */
  .toc-page {
    padding: 60px;
    page-break-after: always;
  }
  .toc-page h2 { font-size: 28px; color: #0f172a; border-left: 5px solid #2563eb; padding-left: 16px; margin-bottom: 30px; }
  .toc-item { display: flex; align-items: baseline; padding: 10px 0; border-bottom: 1px dashed #e2e8f0; }
  .toc-num { font-size: 14px; font-weight: 700; color: #2563eb; width: 40px; flex-shrink: 0; }
  .toc-title { flex: 1; font-size: 14px; color: #334155; }
  .toc-desc { font-size: 12px; color: #94a3b8; margin-left: 10px; }

  /* ===== 通用页面布局 ===== */
  .page {
    padding: 50px 60px;
    page-break-after: always;
    min-height: 100vh;
  }
  .page:last-child { page-break-after: avoid; }

  /* ===== 章节标题 ===== */
  .chapter-header {
    background: linear-gradient(90deg, #1e3a5f, #2563eb);
    color: white;
    padding: 20px 30px;
    border-radius: 10px;
    margin-bottom: 30px;
    display: flex;
    align-items: center;
    gap: 16px;
  }
  .chapter-header .ch-num {
    font-size: 36px;
    font-weight: 900;
    opacity: 0.3;
    line-height: 1;
  }
  .chapter-header .ch-text h2 { font-size: 22px; font-weight: 700; }
  .chapter-header .ch-text p { font-size: 13px; opacity: 0.75; margin-top: 4px; }

  /* ===== 用户角色标签 ===== */
  .role-tag {
    display: inline-block;
    padding: 3px 10px;
    border-radius: 12px;
    font-size: 11px;
    font-weight: 600;
    margin-right: 6px;
  }
  .role-admin { background: #dbeafe; color: #1d4ed8; }
  .role-worker { background: #d1fae5; color: #065f46; }
  .role-system { background: #f3e8ff; color: #7c3aed; }

  /* ===== 截图 ===== */
  figure.screenshot {
    margin: 16px 0;
    border: 1px solid #e2e8f0;
    border-radius: 8px;
    overflow: hidden;
    box-shadow: 0 2px 8px rgba(0,0,0,0.08);
    page-break-inside: avoid;
  }
  figure.screenshot img {
    width: 100%;
    height: auto;
    display: block;
  }
  figure.screenshot figcaption {
    background: #f8fafc;
    padding: 8px 14px;
    font-size: 11px;
    color: #64748b;
    border-top: 1px solid #e2e8f0;
    text-align: center;
  }

  /* ===== 并排截图 ===== */
  .screenshot-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 16px;
    margin: 16px 0;
  }
  .screenshot-grid.three { grid-template-columns: 1fr 1fr 1fr; }
  .screenshot-grid figure.screenshot { margin: 0; }

  /* ===== 链路流程步骤 ===== */
  .flow-container { margin: 20px 0; }
  .step {
    display: flex;
    align-items: flex-start;
    gap: 14px;
    padding: 14px 18px;
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    border-radius: 8px;
    margin-bottom: 4px;
  }
  .step-num {
    width: 28px;
    height: 28px;
    background: #2563eb;
    color: white;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 13px;
    font-weight: 700;
    flex-shrink: 0;
  }
  .step-content strong { font-size: 14px; color: #0f172a; display: block; margin-bottom: 2px; }
  .step-content p { font-size: 12px; color: #64748b; }
  .flow-arrow {
    text-align: center;
    color: #94a3b8;
    font-size: 18px;
    padding: 2px 0;
  }

  /* ===== 状态标签 ===== */
  .state-tag {
    display: inline-block;
    color: white;
    padding: 2px 10px;
    border-radius: 10px;
    font-size: 11px;
    font-weight: 600;
    margin: 2px;
  }

  /* ===== 状态流转图 ===== */
  .state-flow {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px;
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    border-radius: 8px;
    padding: 16px 20px;
    margin: 14px 0;
  }
  .state-flow .arrow { color: #94a3b8; font-size: 16px; }

  /* ===== 技术信息卡片 ===== */
  .info-card {
    background: #f0f9ff;
    border-left: 4px solid #2563eb;
    padding: 14px 18px;
    border-radius: 0 8px 8px 0;
    margin: 12px 0;
    font-size: 12px;
  }
  .info-card .label { font-weight: 700; color: #1e3a5f; margin-bottom: 4px; }
  .info-card code {
    background: rgba(37,99,235,0.1);
    padding: 2px 6px;
    border-radius: 4px;
    font-family: 'Courier New', monospace;
    font-size: 11px;
    color: #1d4ed8;
  }

  /* ===== 风险/发现卡片 ===== */
  .alert {
    border-radius: 8px;
    padding: 12px 16px;
    margin: 10px 0;
    font-size: 12px;
  }
  .alert-red { background: #fef2f2; border-left: 4px solid #ef4444; }
  .alert-yellow { background: #fffbeb; border-left: 4px solid #f59e0b; }
  .alert-green { background: #f0fdf4; border-left: 4px solid #10b981; }
  .alert-blue { background: #eff6ff; border-left: 4px solid #2563eb; }
  .alert strong { color: #1e293b; display: block; margin-bottom: 3px; }

  /* ===== 功能总览表 ===== */
  table {
    width: 100%;
    border-collapse: collapse;
    font-size: 12px;
    margin: 14px 0;
  }
  thead th {
    background: #1e3a5f;
    color: white;
    padding: 10px 12px;
    text-align: left;
    font-weight: 600;
  }
  tbody td {
    padding: 8px 12px;
    border-bottom: 1px solid #f1f5f9;
    vertical-align: top;
  }
  tbody tr:hover td { background: #f8fafc; }
  tbody tr:nth-child(even) td { background: #fafafa; }

  /* ===== 架构图 ===== */
  .arch-diagram {
    background: #0f172a;
    color: #94a3b8;
    font-family: 'Courier New', monospace;
    font-size: 11px;
    padding: 20px;
    border-radius: 8px;
    line-height: 1.6;
    margin: 14px 0;
    white-space: pre;
    page-break-inside: avoid;
  }
  .arch-diagram .hl { color: #60a5fa; }
  .arch-diagram .hl2 { color: #34d399; }
  .arch-diagram .hl3 { color: #f9a8d4; }

  /* ===== 小标题 ===== */
  h3 { font-size: 17px; color: #1e3a5f; margin: 24px 0 12px; border-bottom: 2px solid #e2e8f0; padding-bottom: 6px; }
  h4 { font-size: 14px; color: #334155; margin: 18px 0 8px; }
  p { margin: 8px 0; color: #475569; font-size: 13px; }

  /* ===== API 路径展示 ===== */
  .api-badge {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    background: #f1f5f9;
    border: 1px solid #e2e8f0;
    border-radius: 6px;
    padding: 4px 10px;
    font-size: 11px;
    margin: 3px 0;
  }
  .method { font-weight: 700; padding: 1px 6px; border-radius: 4px; font-size: 10px; }
  .method-post { background: #dcfce7; color: #16a34a; }
  .method-get { background: #dbeafe; color: #1d4ed8; }
  .method-put { background: #fef9c3; color: #a16207; }
  .method-delete { background: #fee2e2; color: #dc2626; }

  @media print {
    .cover, .page, .toc-page { page-break-after: always; }
  }
  @page { size: A4; margin: 15mm 18mm; }
</style>
</head>
<body>

<!-- ===== 封面 ===== -->
<div class="cover">
  <div class="badge">功能链路分析报告 v1.0</div>
  <h1>易工单系统</h1>
  <h2>XiaoBai Easy WorkOrder System</h2>
  <div class="divider"></div>
  <div class="meta">
    <div class="meta-item"><div class="val">50</div><div class="lbl">已实现功能点</div></div>
    <div class="meta-item"><div class="val">3</div><div class="lbl">子系统</div></div>
    <div class="meta-item"><div class="val">140</div><div class="lbl">单元测试</div></div>
    <div class="meta-item"><div class="val">10</div><div class="lbl">数据库迁移版本</div></div>
  </div>
  <div class="date">报告生成时间：2026-03-18 | 调研方式：静态代码分析 + 实际页面截图</div>
</div>

<!-- ===== 目录 ===== -->
<div class="toc-page">
  <h2>报告目录</h2>
  <div class="toc-item"><span class="toc-num">01</span><span class="toc-title">系统架构总览</span><span class="toc-desc">三端分离架构、技术栈、部署方式</span></div>
  <div class="toc-item"><span class="toc-num">02</span><span class="toc-title">用户故事 A：工单生命周期</span><span class="toc-desc">创建 → 派工 → 开工 → 报工 → 质检 → 完成</span></div>
  <div class="toc-item"><span class="toc-num">03</span><span class="toc-title">用户故事 B：工人操作链路</span><span class="toc-desc">工人端扫码、开工、报工、撤销、批量操作</span></div>
  <div class="toc-item"><span class="toc-num">04</span><span class="toc-title">用户故事 C：质检与返工链路</span><span class="toc-desc">质检录入、通过/失败/报废、返工循环</span></div>
  <div class="toc-item"><span class="toc-num">05</span><span class="toc-title">用户故事 D：Andon 呼叫链路</span><span class="toc-desc">工人触发 → 管理员响应 → 处理完成</span></div>
  <div class="toc-item"><span class="toc-num">06</span><span class="toc-title">管理后台功能全览</span><span class="toc-desc">用户、班组、统计、审计、MES集成</span></div>
  <div class="toc-item"><span class="toc-num">07</span><span class="toc-title">关键风险与缺口</span><span class="toc-desc">架构风险、代码质量、功能缺口</span></div>
  <div class="toc-item"><span class="toc-num">08</span><span class="toc-title">架构改进建议</span><span class="toc-desc">Agent/MCP/Skills 视角的落地建议</span></div>
</div>

<!-- ===== 第 1 章：系统架构总览 ===== -->
<div class="page">
  <div class="chapter-header">
    <div class="ch-num">01</div>
    <div class="ch-text">
      <h2>系统架构总览</h2>
      <p>三端分离架构 · Spring Boot 后端 · Vue3 双前端 · PostgreSQL + Redis 数据层</p>
    </div>
  </div>

  <div class="arch-diagram"><span class="hl">┌──────────────────────────────────────────────────────────────────────────┐</span>
<span class="hl">│                          客户端层                                        │</span>
<span class="hl">│                                                                          │</span>
<span class="hl">│  ┌─────────────────────────────┐    ┌─────────────────────────────────┐  │</span>
<span class="hl2">│  │    easywork-admin (PC端)    │    │   easywork-worker (设备端/PWA) │  │</span>
<span class="hl2">│  │  Vue3 + Element Plus        │    │  Vue3 + Vant · T9键盘 · 扫码枪 │  │</span>
<span class="hl2">│  │  localhost:5173             │    │  localhost:5174                 │  │</span>
<span class="hl">│  └─────────────┬───────────────┘    └────────────────┬────────────────┘  │</span>
<span class="hl">└────────────────┼────────────────────────────────────┼────────────────────┘</span>
                 │ /api/admin/*                        │ /api/device/*
                 └──────────────────┬──────────────────┘
                                    ▼
<span class="hl2">┌──────────────────────────────────────────────────────────────────────────┐</span>
<span class="hl2">│              后端服务 (easywork) — Spring Boot 3.2 / Java 21             │</span>
<span class="hl2">│                        localhost:8080                                    │</span>
<span class="hl2">│                                                                          │</span>
<span class="hl2">│  认证/JWT  │  工单状态机  │  报工服务  │  质检  │  Andon  │  统计  │ MES │</span>
<span class="hl2">│                                                                          │</span>
<span class="hl2">│         Spring Security 6 + Bucket4j 速率限制 + MapStruct               │</span>
<span class="hl">└─────────────────────────┬──────────────────────────┬─────────────────────┘</span>
                          │                          │
           <span class="hl3">┌─────────────▼────────────┐  ┌──────▼──────────────────────┐</span>
           <span class="hl3">│  PostgreSQL 16            │  │  Redis 7 (Sentinel 高可用)  │</span>
           <span class="hl3">│  Flyway V1.0 → V1.9      │  │  幂等性·速率限制·统计缓存   │</span>
           <span class="hl3">└──────────────────────────┘  └─────────────────────────────┘</span></div>

  <h3>技术栈一览</h3>
  <table>
    <thead><tr><th>分层</th><th>技术选型</th><th>版本</th><th>核心用途</th></tr></thead>
    <tbody>
      <tr><td>后端框架</td><td>Spring Boot</td><td>3.2.3</td><td>Web + 安全 + 自动配置</td></tr>
      <tr><td>数据访问</td><td>MyBatis-Plus</td><td>3.5.7</td><td>ORM + 乐观锁 + 逻辑删除</td></tr>
      <tr><td>认证</td><td>Spring Security 6 + JJWT</td><td>0.12.5</td><td>JWT 无状态认证</td></tr>
      <tr><td>数据库</td><td>PostgreSQL</td><td>16</td><td>主数据存储，Flyway 版本管理</td></tr>
      <tr><td>缓存</td><td>Redis 7 + Sentinel</td><td>—</td><td>幂等性、速率限制、本地 Caffeine 缓存</td></tr>
      <tr><td>管理端 UI</td><td>Vue 3 + Element Plus</td><td>3.5 / 2.13</td><td>PC 端管理后台</td></tr>
      <tr><td>工人端 UI</td><td>Vue 3 + Vant</td><td>3.5 / 4.9</td><td>移动端/手持设备，支持 T9 键盘</td></tr>
      <tr><td>图表</td><td>ECharts</td><td>6.0</td><td>统计看板可视化</td></tr>
      <tr><td>工序图</td><td>Vue Flow</td><td>1.48</td><td>工序依赖有向图（DAG）</td></tr>
      <tr><td>离线支持</td><td>Service Worker + IndexedDB</td><td>—</td><td>断网排队、联网重放</td></tr>
    </tbody>
  </table>

  <h3>工单状态机核心规则</h3>
  <h4>生产型工单 (PRODUCTION)</h4>
  <div class="state-flow">
    ${stateTag('未开始', 'not_started')}
    <span class="arrow">→ 开工 →</span>
    ${stateTag('已开始', 'started')}
    <span class="arrow">→ 报工 →</span>
    ${stateTag('待质检', 'reported')}
    <span class="arrow">→ 质检通过 →</span>
    ${stateTag('质检通过', 'inspect_passed')}
    <span class="arrow">→ 完成 →</span>
    ${stateTag('已完成', 'completed')}
  </div>
  <div class="state-flow">
    ${stateTag('待质检', 'reported')}
    <span class="arrow">→ 质检失败 →</span>
    ${stateTag('质检失败', 'inspect_failed')}
    <span class="arrow">→ 返工重开 →</span>
    ${stateTag('待质检', 'reported')}
    <span class="arrow">（循环直至通过）</span>
  </div>
  <div class="state-flow">
    ${stateTag('待质检', 'reported')}
    <span class="arrow">→ 报废 →</span>
    ${stateTag('已报废', 'scrapped')}
    <span class="arrow">（终态）</span>
  </div>
  <h4>简单型工单 (INSPECTION / TRANSPORT / ANDON)</h4>
  <div class="state-flow">
    ${stateTag('未开始', 'not_started')}
    <span class="arrow">→ 开始 →</span>
    ${stateTag('已开始', 'started')}
    <span class="arrow">→ 完成 →</span>
    ${stateTag('已完成', 'completed')}
  </div>
</div>

<!-- ===== 第 2 章：用户故事 A — 工单生命周期 ===== -->
<div class="page">
  <div class="chapter-header">
    <div class="ch-num">02</div>
    <div class="ch-text">
      <h2>用户故事 A：工单生命周期</h2>
      <p>从管理员创建工单，到最终完成的完整链路 · 角色：<span style="color:#60a5fa">管理员</span></p>
    </div>
  </div>

  <h3>A1. 管理员登录</h3>
  <div class="flow-container">
    ${step(1, '访问系统', '管理员打开浏览器访问管理端，系统检测未登录，自动跳转登录页')}
    ${flowArrow()}
    ${step(2, '输入员工号 + 密码', '输入员工号（如 ADMIN001）和密码，点击登录')}
    ${flowArrow()}
    ${step(3, '获取 JWT Token', '后端验证通过，返回 JWT Token，前端存入 localStorage，跳转看板')}
  </div>
  <div class="info-card">
    <div class="label">接口</div>
    <span class="api-badge"><span class="method method-post">POST</span>/api/auth/login · 参数: {employeeNumber, password}</span>
    <br><span style="font-size:11px; color:#64748b; margin-top:6px; display:block">速率限制：10次/60秒（Bucket4j）· CORS 白名单配置</span>
  </div>
  ${img('01-admin-login.png', '管理端登录页 — 输入员工号和密码')}
</div>

<div class="page">
  <div class="chapter-header">
    <div class="ch-num">02</div>
    <div class="ch-text">
      <h2>用户故事 A（续）：创建工单 & 统计看板</h2>
      <p>登录后首先看到统计看板，然后进入工单管理模块</p>
    </div>
  </div>

  <h3>A2. 统计看板（登录后默认页）</h3>
  <p>登录成功后自动跳转 /dashboard，展示：工单总量、完成率、质检通过率、质检趋势折线图（近7天/N天）</p>
  ${img('02-admin-dashboard.png', '统计看板 — ECharts 可视化（工单数量、质检趋势）')}

  <div class="info-card">
    <div class="label">接口</div>
    <span class="api-badge"><span class="method method-get">GET</span>/api/admin/statistics/dashboard</span>
    <span class="api-badge"><span class="method method-get">GET</span>/api/admin/statistics/inspection-trend?days=7</span>
    <br><span style="font-size:11px; color:#64748b; margin-top:6px; display:block">后端 StatisticsService 使用 Caffeine 本地缓存，减少数据库聚合查询压力</span>
  </div>

  <h3>A3. 工单列表</h3>
  <p>展示全部工单，支持按状态（未开始/已开始/待质检/已完成等）和产品名筛选，分页显示。</p>
  ${img('03-admin-workorder-list.png', '工单列表 — 状态筛选 + 分页')}
</div>

<div class="page">
  <div class="chapter-header">
    <div class="ch-num">02</div>
    <div class="ch-text">
      <h2>用户故事 A（续）：创建工单 & 工单详情</h2>
      <p>新建工单并查看工序依赖关系图</p>
    </div>
  </div>

  <h3>A4. 创建工单</h3>
  <p>支持4种工单类型：生产(PRODUCTION)、质检(INSPECTION)、搬运(TRANSPORT)、Andon。每种类型对应不同状态流转规则。</p>
  ${img('05-admin-workorder-create.png', '创建工单页 — 选择类型、填写产品信息、添加工序')}

  <div class="info-card">
    <div class="label">接口 + 后端文件</div>
    <span class="api-badge"><span class="method method-post">POST</span>/api/admin/work-orders · {productName, quantity, type, operations[]}</span>
    <br><span style="font-size:11px; color:#64748b; margin-top:6px; display:block">
      AdminWorkOrderController → WorkOrderService → WorkOrderMapper → work_orders + operations 表<br>
      创建时工单状态自动设为 NOT_STARTED，operations 各自也初始化为 NOT_STARTED
    </span>
  </div>

  <h3>A5. 工单详情 + 工序有向图</h3>
  <p>工单详情页展示：工序列表、每道工序当前状态、已完成/计划数量。若配置了工序依赖关系，还会渲染 DAG 有向图（Vue Flow + Kahn 拓扑排序）。</p>
  ${img('04-admin-workorder-detail.png', '工单详情 — 工序状态 + 操作按钮（派工/质检/完成）')}
</div>

<!-- ===== 第 3 章：用户故事 B — 工人操作链路 ===== -->
<div class="page">
  <div class="chapter-header">
    <div class="ch-num">03</div>
    <div class="ch-text">
      <h2>用户故事 B：工人操作链路</h2>
      <p>工人在车间手持设备上的完整操作流程 · 角色：<span style="color:#34d399">工人</span></p>
    </div>
  </div>

  <h3>B1. 工人登录（T9 键盘）</h3>
  <p>工人端专为手持设备/PDA 设计，密码输入支持 T9 九键键盘，方向键可在工单列表中导航，数字快捷键执行操作。</p>

  <div class="screenshot-grid">
    ${img('12-worker-login.png', '工人端登录页 — T9 九键键盘输入密码')}
    ${img('13-worker-workorder-list.png', '工人端工单列表 — 方向键导航 + 焦点高亮')}
  </div>

  <div class="info-card">
    <div class="label">接口 + 硬件适配</div>
    <span class="api-badge"><span class="method method-post">POST</span>/api/device/login · {employeeNumber, password}</span>
    <br><span style="font-size:11px; color:#64748b; margin-top:6px; display:block">
      useHardwareInput.js：50ms 阈值识别扫码枪输入 · 方向键 ↑↓ 导航工单/工序列表<br>
      数字快捷键：1=开工 / 2=报工 / 3=质检 / 4=撤销 / 5=返工（工单详情页）
    </span>
  </div>

  <h3>B2. 工单详情（工人视角）</h3>
  <p>工人在工单详情页可看到本工单的所有工序及其状态，点击工序执行相应操作。键盘快捷键覆盖所有操作，无需触屏。</p>
  ${img('14-worker-workorder-detail.png', '工人端工单详情 — 工序操作（开工/报工/撤销/质检）')}
</div>

<div class="page">
  <div class="chapter-header">
    <div class="ch-num">03</div>
    <div class="ch-text">
      <h2>用户故事 B（续）：扫码操作 & 批量操作</h2>
      <p>扫码枪快速开工/报工 · 批量操作支持幂等性</p>
    </div>
  </div>

  <h3>B3. 扫码开工/报工</h3>
  <p>工人将扫码枪扫描工序/工单条码，系统自动识别条码类型并执行相应操作（开工或报工），无需手动查找工序。</p>

  <div class="flow-container">
    ${step(1, '扫描条码', '扫码枪扫描贴在工序卡/工单上的条码，系统在 50ms 内完成输入收集')}
    ${flowArrow()}
    ${step(2, '条码解析', 'ScanService 自动判断：工单条码 or 工序条码，找到对应记录')}
    ${flowArrow()}
    ${step(3, '执行操作', '根据当前操作模式（开工/报工），自动触发 startWork 或 reportWork')}
    ${flowArrow()}
    ${step(4, '返回结果', '显示操作成功/失败提示，刷新工序状态')}
  </div>

  ${img('15-worker-scan.png', '扫码页 — 扫码枪/手动输入条码，切换开工/报工模式')}

  <div class="info-card">
    <div class="label">接口</div>
    <span class="api-badge"><span class="method method-post">POST</span>/api/device/scan/start · {barcode}</span>
    <span class="api-badge"><span class="method method-post">POST</span>/api/device/scan/report · {barcode}</span>
  </div>

  <h3>B4. 批量开工/报工（幂等性保证）</h3>
  <p>工人可多选工序后批量执行开工或报工。每次批量操作生成 UUID 作为幂等 Key，网络重传不会导致重复操作。</p>

  <div class="info-card">
    <div class="label">幂等机制</div>
    <span style="font-size:12px; color:#1e293b;">
      请求头携带 <code>Idempotency-Key: {UUID}</code><br>
      后端 IdempotencyService 将结果缓存到 Redis，TTL 30分钟<br>
      重复提交时直接返回缓存结果，不执行二次操作
    </span>
  </div>

  <div class="info-card">
    <div class="label">离线支持</div>
    <span style="font-size:12px; color:#1e293b;">
      断网时操作自动进入 IndexedDB 离线队列（offlineQueue.js）<br>
      Service Worker 监听网络恢复，自动重放队列中的请求<br>
      重放结果返回 {processed, failed, skipped}
    </span>
  </div>
</div>

<!-- ===== 第 4 章：用户故事 C — 质检与返工 ===== -->
<div class="page">
  <div class="chapter-header">
    <div class="ch-num">04</div>
    <div class="ch-text">
      <h2>用户故事 C：质检与返工链路</h2>
      <p>工序报工后进入质检环节，质检结果触发通过/失败/报废三种分支</p>
    </div>
  </div>

  <h3>C1. 质检管理（管理端）</h3>
  <p>管理员或质检员在管理端查看"待质检"状态的工单，录入质检结果。</p>

  <div class="flow-container">
    ${step(1, '工单进入待质检', '工人完成所有工序报工后，工单状态自动变为 REPORTED（待质检）')}
    ${flowArrow()}
    ${step(2, '质检录入', '质检员在管理端 /inspection 页面，选择工单，填写检查数量、合格数量、缺陷数量、缺陷原因')}
    ${flowArrow()}
    ${step(3, '质检结果判定', '选择 PASSED / FAILED / SCRAPPED 三种结果之一')}
    ${flowArrow()}
    ${step(4, '状态流转', 'PASSED → INSPECT_PASSED（可完成）/ FAILED → INSPECT_FAILED（需返工）/ SCRAPPED → 终态')}
  </div>

  ${img('06-admin-inspection.png', '质检管理页 — 待质检工单列表 + 质检结果录入表单')}

  <div class="info-card">
    <div class="label">接口 + 后端文件</div>
    <span class="api-badge"><span class="method method-post">POST</span>/api/admin/inspections · {workOrderId, inspectionResult, inspectedQuantity, ...}</span>
    <br><span style="font-size:11px; color:#64748b; margin-top:6px; display:block">
      InspectionController → InspectionService → inspection_records 表<br>
      质检结果同步触发 WorkOrderStateMachine 状态转换
    </span>
  </div>

  <h3>C2. 返工流程</h3>
  <p>质检失败后，管理员可通过"重新打开"操作将工单状态从 INSPECT_FAILED 切回 REPORTED，工人重新执行工序并报工，直至质检通过。</p>
  <div class="state-flow">
    ${stateTag('待质检', 'reported')}
    <span class="arrow">→ 质检失败 →</span>
    ${stateTag('质检失败', 'inspect_failed')}
    <span class="arrow">→ 重新打开 →</span>
    ${stateTag('待质检', 'reported')}
    <span class="arrow">→（重新报工循环）</span>
  </div>
  <div class="info-card">
    <div class="label">接口</div>
    <span class="api-badge"><span class="method method-put">PUT</span>/api/admin/work-orders/{id}/reopen</span>
    <span class="api-badge"><span class="method method-put">PUT</span>/api/admin/work-orders/{id}/complete</span>
  </div>
</div>

<!-- ===== 第 5 章：用户故事 D — Andon 呼叫 ===== -->
<div class="page">
  <div class="chapter-header">
    <div class="ch-num">05</div>
    <div class="ch-text">
      <h2>用户故事 D：Andon 呼叫链路</h2>
      <p>工人遇到异常时触发呼叫，管理员实时响应并处理 · 角色：<span style="color:#34d399">工人</span> + <span style="color:#60a5fa">管理员</span></p>
    </div>
  </div>

  <h3>D1. 工人触发呼叫（三类）</h3>
  <p>工人端 CallView 提供3种呼叫类型：</p>
  <table>
    <thead><tr><th>呼叫类型</th><th>使用场景</th><th>接口</th></tr></thead>
    <tbody>
      <tr><td><strong>Andon</strong></td><td>生产线异常停机、设备故障</td><td>POST /api/device/call/andon</td></tr>
      <tr><td><strong>质检呼叫</strong></td><td>需要质检员到现场检查</td><td>POST /api/device/call/inspection</td></tr>
      <tr><td><strong>搬运呼叫</strong></td><td>需要物料搬运/转序</td><td>POST /api/device/call/transport</td></tr>
    </tbody>
  </table>

  <div class="screenshot-grid">
    ${img('16-worker-call.png', '工人端呼叫页 — 选择呼叫类型 + 描述')}
    ${img('07-admin-calls.png', '管理端呼叫列表 — PENDING/HANDLING/HANDLED 状态')}
  </div>

  <h3>D2. 管理员处理呼叫</h3>
  <div class="flow-container">
    ${step(1, '呼叫触发', '工人点击呼叫按钮，系统创建 CallRecord，状态 PENDING')}
    ${flowArrow()}
    ${step(2, '管理员接单', '管理员在 /calls 页面看到新呼叫，点击"处理"，状态变为 HANDLING，记录处理人')}
    ${flowArrow()}
    ${step(3, '处理完成', '管理员填写处理结果，点击"完成"，状态变为 HANDLED，记录完成时间')}
  </div>

  <div class="alert alert-yellow">
    <strong>⚠️ 当前限制：无实时推送</strong>
    管理员需手动刷新页面才能看到新呼叫。系统尚未实现 WebSocket/SSE 推送，Andon 场景的时效性依赖人工轮询。
  </div>
</div>

<!-- ===== 第 6 章：管理后台功能全览 ===== -->
<div class="page">
  <div class="chapter-header">
    <div class="ch-num">06</div>
    <div class="ch-text">
      <h2>管理后台功能全览</h2>
      <p>用户管理、班组管理、审计日志、MES 集成</p>
    </div>
  </div>

  <div class="screenshot-grid">
    ${img('08-admin-users.png', '用户管理 — 创建/编辑/软删除用户，分配 ADMIN/WORKER 角色')}
    ${img('09-admin-teams.png', '班组管理 — 创建班组、添加/移除成员')}
  </div>

  <div class="screenshot-grid">
    ${img('10-admin-audit.png', '审计日志 — ISO 9001 合规，AOP 自动记录操作，支持按用户/操作类型筛选')}
    ${img('11-admin-mes.png', 'MES 集成监控 — 同步统计（成功/失败/重试）+ 同步日志查询')}
  </div>

  <h3>MES 双向集成架构</h3>
  <div class="flow-container">
    ${step('入', 'MES → 易工单（Webhook 入站）', 'POST /api/mes/work-orders/import — MES 推送工单，自动创建工单+工序+分配，mesOrderId 幂等')}
    ${step('出', '易工单 → MES（定时出站推送）', 'MesSyncScheduler 定时将报工/状态/质检结果推送回 MES，指数退避重试，最多5次')}
  </div>
  <div class="alert alert-yellow">
    <strong>⚠️ 当前状态</strong>MES 集成代码已完整实现，但无真实 MES 系统对接。出站推送在无 MES 环境时会持续重试并累积日志。
  </div>
</div>

<!-- ===== 第 7 章：关键风险与缺口 ===== -->
<div class="page">
  <div class="chapter-header">
    <div class="ch-num">07</div>
    <div class="ch-text">
      <h2>关键风险与缺口</h2>
      <p>架构风险 · 代码质量 · 功能缺口 · 建议下一步行动</p>
    </div>
  </div>

  <h3>🔴 高优先级风险</h3>
  <div class="alert alert-red">
    <strong>R1 · JWT Secret 无默认值</strong>
    APP_JWT_SECRET 环境变量缺失时服务启动失败（这是有意的 Fail Fast 设计）。CI/CD 流水线和生产部署必须注入该变量，建议使用 Kubernetes Secret 或 Vault 管理。
  </div>

  <h3>🟡 中优先级风险</h3>
  <div class="alert alert-yellow">
    <strong>R2 · 无实时推送 (WebSocket/SSE)</strong>
    Andon 呼叫等时效性强的场景，管理员需手动刷新页面。建议引入 WebSocket 或 SSE 实现实时通知。
  </div>
  <div class="alert alert-yellow">
    <strong>R3 · DeviceController 过于臃肿</strong>
    工人端所有操作（报工、扫码、质检、呼叫、返工）集中在一个 Controller，约 20+ 方法。建议按业务拆分为独立 Controller。
  </div>
  <div class="alert alert-yellow">
    <strong>R4 · statusLabel.js 双份维护</strong>
    Admin 和 Worker 两个项目各有一份状态标签映射文件，需同步维护。建议提取为共享包或 monorepo 工具模块。
  </div>
  <div class="alert alert-yellow">
    <strong>R5 · 工序依赖疑似缺少环检测</strong>
    AddDependency 接口在添加依赖时疑似未检测循环依赖。若形成环路，Kahn 拓扑排序将陷入死循环。需人工确认并添加 DFS 环检测。
  </div>

  <h3>🟢 功能缺口</h3>
  <div class="alert alert-blue">
    <strong>G1 · 工人产出统计接口疑似缺失</strong>
    前端 api/statistics.js 中有 getWorkerOutput 调用，但后端 /api/admin/statistics/worker-output 接口待确认是否实现。
  </div>
  <div class="alert alert-blue">
    <strong>G2 · 无工单模板/复制功能</strong>
    高频创建相同产品工单时，需每次手填。建议增加"复制工单"或"工单模板"功能。
  </div>
  <div class="alert alert-blue">
    <strong>G3 · 权限粒度较粗</strong>
    目前仅 ADMIN / WORKER 两个角色，无法实现"只能操作自己班组的工单"等细粒度控制。
  </div>

  <h3>建议下一步行动</h3>
  <table>
    <thead><tr><th>优先级</th><th>行动项</th><th>说明</th></tr></thead>
    <tbody>
      <tr><td>🔴 立即</td><td>验证 Docker 完整启动</td><td>执行 docker-compose up -d，全链路端到端测试</td></tr>
      <tr><td>🔴 立即</td><td>确认 worker-output 接口</td><td>检查 /api/admin/statistics/worker-output 是否实现</td></tr>
      <tr><td>🟡 短期</td><td>添加工序依赖环检测</td><td>AdminOperationDependencyController#add 中加 DFS 检测</td></tr>
      <tr><td>🟡 短期</td><td>拆分 DeviceController</td><td>按扫码/报工/呼叫/质检拆分为4个 Controller</td></tr>
      <tr><td>🟡 中期</td><td>引入 WebSocket/SSE</td><td>为 Andon 呼叫实现实时推送</td></tr>
      <tr><td>🟢 长期</td><td>TypeScript 迁移</td><td>前端引入 TS 提升类型安全</td></tr>
    </tbody>
  </table>
</div>

<!-- ===== 第 8 章：架构改进建议 ===== -->
<div class="page">
  <div class="chapter-header">
    <div class="ch-num">08</div>
    <div class="ch-text">
      <h2>架构改进建议：Agent / MCP / Skills 视角</h2>
      <p>评估引入现代 AI 工程架构的可行性与落地路径</p>
    </div>
  </div>

  <h3>8.1 将"代码调研"封装为可复用 Skill ✅ 强烈推荐</h3>
  <p>本次调研流程本身就是一个高度结构化、可重复执行的工作流，完全可以封装为 <code>code-audit-skill</code>：</p>
  <div class="arch-diagram"><span class="hl">Skill: code-audit</span>
  输入: repo_path, output_dir, run_mode (static | with_browser)
  <span class="hl2">子步骤（可并行）:</span>
    1. TechStackScanner    → 识别框架/工具链 → tech_stack.json
    2. RouteAPIMapper       → 提取路由+接口  → api_map.json
    3. FeatureExtractor    → 推断功能点      → features.json
    4. BrowserAuditor      → 页面截图        → screenshots/
    5. ReportGenerator     → 合并生成报告    → report.md + report.pdf
  <span class="hl2">输出: current_state_report.md, current_state_report.pdf, screenshots/</span></div>

  <h3>8.2 Multi-Agent 并行子任务流 ✅ 推荐（大型仓库效果明显）</h3>
  <table>
    <thead><tr><th>Agent</th><th>工具</th><th>职责</th></tr></thead>
    <tbody>
      <tr><td>Repo Scanner</td><td>Glob, Grep, Read</td><td>文件结构、技术栈识别</td></tr>
      <tr><td>Route/API Mapper</td><td>Read (controllers/routers)</td><td>接口路径、参数提取</td></tr>
      <tr><td>Browser Auditor</td><td>Bash, Playwright MCP</td><td>前端启动、截图、页面验证</td></tr>
      <tr><td>Report Generator</td><td>Write, PDF MCP</td><td>汇总所有 JSON，生成报告</td></tr>
    </tbody>
  </table>

  <h3>8.3 MCP 接入建议</h3>
  <table>
    <thead><tr><th>MCP/插件</th><th>用途</th><th>优先级</th></tr></thead>
    <tbody>
      <tr><td>Playwright MCP</td><td>浏览器截图、页面导航、表单填写</td><td>🔴 P1（解决当前手动截图问题）</td></tr>
      <tr><td>PDF Export MCP</td><td>Markdown → PDF 高质量转换</td><td>🟡 P2</td></tr>
      <tr><td>知识库/向量库 MCP</td><td>历史调研存档、版本对比</td><td>🟡 P2（适合迭代项目）</td></tr>
      <tr><td>GitHub MCP</td><td>直接读取 PR/Issue/代码</td><td>🟢 P3</td></tr>
    </tbody>
  </table>

  <h3>8.4 对于易工单业务本身 — 部分适合</h3>
  <div class="alert alert-green">
    <strong>✅ 适合引入：智能派工助手</strong>
    基于工人历史绩效、当前负荷、技能标签，AI 推荐最优派工方案。可作为 LLM 调用层接入 /api/admin/work-orders/assign。
  </div>
  <div class="alert alert-blue">
    <strong>💡 中期可考虑：质量预测</strong>
    基于历史质检记录，预测当前工序的质量风险，提前提示质检员重点关注。
  </div>
  <div class="alert alert-yellow">
    <strong>⚠️ 不建议：将核心状态机替换为 LLM</strong>
    工单状态机逻辑明确、规则固定，LLM 引入会增加不确定性和延迟。当前 WorkOrderStateMachine 设计已足够健壮。
  </div>
</div>

</body>
</html>`;

fs.writeFileSync(path.join(UTILS_DIR, '_report_visual_temp.html'), html, 'utf-8');
console.log('[PDF生成] 可视化 HTML 已生成');

async function main() {
  const executablePath = 'C:\\Users\\Administrator\\utils\\.playwright\\chromium-1208\\chrome-win64\\chrome.exe';
  const { chromium } = require('playwright');
  const browser = await chromium.launch({
    headless: true,
    executablePath,
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-gpu']
  });

  const page = await browser.newPage();
  const fileUrl = `file:///${path.join(UTILS_DIR, '_report_visual_temp.html').replace(/\\/g, '/')}`;
  console.log('[PDF生成] 加载页面...');
  await page.goto(fileUrl, { waitUntil: 'networkidle', timeout: 60000 });
  await page.waitForTimeout(3000);

  console.log('[PDF生成] 打印 PDF...');
  await page.pdf({
    path: PDF_FILE,
    format: 'A4',
    printBackground: true,
    margin: { top: '15mm', right: '15mm', bottom: '15mm', left: '15mm' },
    displayHeaderFooter: true,
    headerTemplate: '<div style="font-size:9px;color:#94a3b8;width:100%;text-align:right;padding:0 20px;">易工单系统 功能链路报告 v1.0</div>',
    footerTemplate: '<div style="font-size:9px;color:#94a3b8;width:100%;text-align:center;"><span class="pageNumber"></span> / <span class="totalPages"></span></div>'
  });

  const stats = require('fs').statSync(PDF_FILE);
  console.log(`[PDF生成] 完成！文件: ${PDF_FILE} (${(stats.size / 1024 / 1024).toFixed(1)} MB)`);

  await browser.close();

  if (fs.existsSync(path.join(UTILS_DIR, '_report_visual_temp.html'))) {
    fs.unlinkSync(path.join(UTILS_DIR, '_report_visual_temp.html'));
  }
}

main().catch(err => { console.error('[错误]', err.message); process.exit(1); });

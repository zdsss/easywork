# XiaoBai Easy WorkOrder System

轻量级制造业工单系统（MES），前后端完全分离。

## 系统架构

```
                    ┌─────────────────────────────┐
                    │   easywork（本仓库）          │
                    │   Spring Boot 后端 :8080     │
                    │                             │
                    │  /api/auth/**   (公开)       │
                    │  /api/admin/**  (ADMIN 角色) │
                    │  /api/device/** (WORKER 角色)│
                    │  /api/mes/**    (ADMIN 角色) │
                    └────────────┬────────────────┘
                                 │ HTTP / JSON (JWT)
               ┌─────────────────┴──────────────────┐
               │                                    │
  ┌────────────▼──────────────┐      ┌──────────────▼────────────┐
  │   easywork-admin          │      │   easywork-worker         │
  │   Vue3 + Element Plus     │      │   Vue3 + Vant（移动端）    │
  │   管理人员 / PC 浏览器     │      │   工人 / 平板·手机         │
  │                           │      │                           │
  │  · 统计看板               │      │  · 查看派发工单            │
  │  · 创建 / 管理工单         │      │  · 开工 / 报工 / 撤销      │
  │  · 派工（人员或班组）      │      │  · 扫码开/报工            │
  │  · 完成工单 / 返工         │      │  · 呼叫（Andon/质检/运输） │
  │  · 质检录入               │      │  · 查看质检结果            │
  │  · 用户 / 班组管理         │      └───────────────────────────┘
  │  · MES 同步监控            │
  └───────────────────────────┘
```

三个仓库独立部署，通过 JWT Token 区分角色权限（ADMIN / WORKER）。

| 仓库 | 端口 | 说明 |
|------|------|------|
| `easywork` | `8080` | 后端 Spring Boot，提供全部 REST API |
| `easywork-admin` | `5173` | 管理端前端，调用 `/api/admin/**` |
| `easywork-worker` | `5174` | 工人端前端，调用 `/api/device/**` |

---

## 环境要求

| 工具 | 版本 |
|------|------|
| Java | 21 |
| Maven | 3.9+ |
| Docker & Docker Compose | 任意近期版本 |
| Node.js | 18+ |

---

## 启动方法

### 1. 启动基础设施（PostgreSQL + Redis）

```bash
cd easywork
docker compose up -d postgres redis
```

### 2. 启动后端

> macOS / Linux（需要 Java 21）：

```bash
JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home" \
  /opt/homebrew/bin/mvn spring-boot:run
```

> Windows：

```bash
set JAVA_HOME=D:\Software\Java21
D:\Software\apache-maven-3.9.13\bin\mvn spring-boot:run
```

启动成功后：
- API 基础地址：`http://localhost:8080`
- Swagger 文档：`http://localhost:8080/swagger-ui.html`

### 3. 启动管理端前端

```bash
cd easywork-admin
npm install
npm run dev
# 访问 http://localhost:5173
```

### 4. 启动工人端前端

```bash
cd easywork-worker
npm install
npm run dev
# 访问 http://localhost:5174
```

---

## 默认账号

| 员工号 | 密码 | 角色 | 用途 |
|--------|------|------|------|
| `ADMIN001` | `admin123` | ADMIN | 管理端 `localhost:5173` |
| `WORKER001` | `worker123` | WORKER | 工人端 `localhost:5174` |

---

## 运行测试

```bash
cd easywork

# 单元测试（无需 Docker）
JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home" \
  /opt/homebrew/bin/mvn test

# 集成测试（需先启动 postgres）
# docker compose up -d postgres redis
# 然后同上命令，集成测试会自动连接 localhost:5432
```

当前测试状态：**140 个单元测试全绿**（集成测试需启动 PostgreSQL）

---

## 工单状态流转

```
生产工单 (PRODUCTION):
  NOT_STARTED → STARTED → REPORTED → INSPECT_PASSED → COMPLETED
                        ↑            → INSPECT_FAILED → (返工后重新报工)
                        │            → SCRAPPED（报废终态）
                        └──── 撤销报工（STARTED）

检验工单 (INSPECTION):
  NOT_STARTED → STARTED → COMPLETED（报工即完成，不走质检）

转运工单 (TRANSPORT):
  NOT_STARTED → STARTED → COMPLETED

安灯工单 (ANDON):
  NOT_STARTED → STARTED → COMPLETED
```

| 状态 | 含义 |
|------|------|
| `NOT_STARTED` | 已创建，未开工 |
| `STARTED` | 已开工 |
| `REPORTED` | 已报工（生产工单等待质检；其余类型由系统自动推进至 COMPLETED） |
| `INSPECT_PASSED` | 质检通过（仅生产工单） |
| `INSPECT_FAILED` | 质检不合格，可返工（仅生产工单） |
| `SCRAPPED` | 报废终态（仅生产工单） |
| `COMPLETED` | 已完成关闭 |

---

## 停止服务

```bash
cd easywork
docker compose down
```

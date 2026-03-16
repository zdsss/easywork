# Workspace: XiaoBai Easy WorkOrder System

制造业工单系统（MES），由三个独立 Git 仓库组成，前后端完全分离。

---

## 仓库结构

```
easyworks/
├── easywork/          ← 后端 Spring Boot（主仓库）
├── easywork-admin/    ← 管理端前端 Vue3
└── easywork-worker/   ← 工人端前端 Vue3
```

---

## 各项目概览

### easywork（后端）
- **技术栈**：Java 21 / Spring Boot 3.2 / MyBatis-Plus 3.5 / Spring Security 6 / JWT
- **数据库**：PostgreSQL 15（端口 5432，库名 `workorder_db`）
- **缓存**：Redis（端口 6379）
- **运行端口**：`8080`
- **启动命令**：`docker-compose up -d postgres redis` → 见下方"Windows 工具路径"运行命令
- **Swagger**：`http://localhost:8080/swagger-ui.html`
- **包路径**：`com.xiaobai.workorder`
- **测试**：140 个单元测试，全部通过

### easywork-admin（管理端前端）
- **技术栈**：Vue 3 / Vite / Element Plus / Vue Router / Pinia / Axios
- **运行端口**：`5173`（Vite 默认）
- **对接 API**：`/api/auth/**`、`/api/admin/**`
- **使用角色**：ADMIN
- **用户场景**：PC 浏览器，管理人员操作

### easywork-worker（工人端前端）
- **技术栈**：Vue 3 / Vite / Vant / Vue Router / Pinia / Axios
- **运行端口**：`5174`（避免与 admin 冲突）
- **对接 API**：`/api/auth/**`、`/api/device/**`
- **使用角色**：WORKER
- **用户场景**：移动端浏览器（平板/手机），工人操作
- **硬件输入**：`useHardwareInput.js`（扫码枪识别 50ms 阈值、方向键导航、数字快捷键、ESC 返回）；`KeyHints.vue`（固定在 tabbar 上方的快捷键提示条）

---

## API 权限规则

| 路径前缀 | 角色要求 | 说明 |
|----------|----------|------|
| `/api/auth/**` | 公开 | 登录接口 |
| `/api/device/**` | WORKER 或 ADMIN | 工人端接口 |
| `/api/admin/**` | ADMIN | 管理端接口 |
| `/api/mes/**` | ADMIN | MES 集成接口 |

---

## API 响应格式

```json
{ "code": 200, "message": "Success", "data": { ... }, "timestamp": 1234567890 }
```

- `code === 200`：成功，取 `data`
- 其他 code：失败，取 `message` 展示错误

---

## 认证机制

- 登录后返回 JWT Token
- 前端存入 `localStorage`（key: `token`）
- 请求头：`Authorization: Bearer <token>`
- Token 有效期：24 小时（`app.jwt.expiration: 86400000`）

---

## 默认账号

| 员工号 | 密码 | 角色 |
|--------|------|------|
| `ADMIN001` | `admin123` | ADMIN |

---

## 本地开发环境

```
后端：  http://localhost:8080
Admin： http://localhost:5173
Worker：http://localhost:5174
```

前端开发时，Vite 代理配置应将 `/api` 转发到 `http://localhost:8080`。

---

## 模块说明（后端）

| 模块 | 路径 | 说明 |
|------|------|------|
| auth | `modules/auth` | 登录认证 |
| user | `modules/user` | 用户管理 |
| team | `modules/team` | 班组管理 |
| device | `modules/device` | 工人端 BFF |
| workorder | `modules/workorder` | 工单生命周期 |
| operation | `modules/operation` | 工序 / 工序依赖 / 返工记录 |
| report | `modules/report` | 报工状态机（核心） |
| inspection | `modules/inspection` | 质检 |
| call | `modules/call` | Andon 呼叫 |
| statistics | `modules/statistics` | 统计看板 |
| mesintegration | `modules/mesintegration` | MES 双向同步 |
| audit | `modules/audit` | 操作审计日志（ISO 9001） |

---

## 工人端关键组件（前端）

| 文件 | 说明 |
|------|------|
| `composables/useHardwareInput.js` | 硬件输入语义化层：扫码枪识别（capture phase，50ms 阈值）、方向键导航、数字快捷键、ESC 返回、退格键。扫码枪字符在输入框聚焦时仍累积缓冲区，扫码枪 Enter 即使聚焦也拦截并触发 onScan；普通按键透传不干扰键盘录入 |
| `composables/usePhysicalKeys.js` | 原始键盘事件层（保留兼容，TestView 使用） |
| `components/KeyHints.vue` | 固定在 tabbar（50px）上方的快捷键提示条，hints 为空时不渲染 |
| `components/T9Input.vue` | T9 九键键盘，用于登录页密码输入 |
| `components/StatusBar.vue` | 右上角状态栏（网络/电池/扫码状态） |
| `utils/offlineQueue.js` | IndexedDB 离线队列，断网时排队，联网后自动提交 |

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

---

## 工具路径（固定配置，必须使用完整路径）

> **重要**：在此项目执行 Java/Maven 命令，必须使用以下完整路径，不要使用裸命令 `mvn`（会报 command not found）。

### Windows 10

| 工具 | 路径 |
|------|------|
| Java 21 | `D:/Software/Java21` |
| Maven 3.9.13 | `D:/Software/apache-maven-3.9.13/bin/mvn` |
| Docker | `C:/Program Files/Docker/Docker/resources/bin/docker.exe` |

**标准测试命令（在 `easywork/` 目录下执行）：**

```bash
JAVA_HOME="D:/Software/Java21" "D:/Software/apache-maven-3.9.13/bin/mvn" test
```

**后端启动命令：**

```bash
JAVA_HOME="D:/Software/Java21" "D:/Software/apache-maven-3.9.13/bin/mvn" spring-boot:run
```

**Docker 启动数据库（在 `easywork/` 目录下执行）：**

```bash
"/c/Program Files/Docker/Docker/resources/bin/docker.exe" compose up -d postgres redis
```

**Docker 常用命令：**

```bash
# 查看运行中容器
"/c/Program Files/Docker/Docker/resources/bin/docker.exe" ps

# 停止数据库
"/c/Program Files/Docker/Docker/resources/bin/docker.exe" compose down
```

### macOS

| 工具 | 路径 |
|------|------|
| Java 21 | `/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home` |
| Maven 3.9.13 | `/opt/homebrew/bin/mvn` |

**标准测试命令（在 `easywork/` 目录下执行）：**

```bash
JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home" /opt/homebrew/bin/mvn test
```

**后端启动命令：**

```bash
JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home" /opt/homebrew/bin/mvn spring-boot:run
```

---

## 项目配置目录结构

```
easyworks/
├── .claude/
│   ├── settings.local.json   ← Claude 本地权限配置（不纳入 git 追踪）
│   └── plans/                ← 实现计划 md 文件（纳入 git 追踪）
└── doc/                      ← 项目技术文档（流程图、模块说明、类文档）
```

- **Plans 存放位置**：`.claude/plans/`（本项目目录，不要写入 C:\Users 下）
- **技术文档**：`doc/` 目录

---

## 注意事项

- 后端使用 `SessionCreationPolicy.STATELESS`，前端不依赖 Cookie/Session
- MES 集成默认关闭（`app.mes.integration.enabled: false`），联调时按需开启
- 逻辑删除字段：`deleted`（1=已删除，0=正常）

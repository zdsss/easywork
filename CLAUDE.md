# Workspace: XiaoBai Easy WorkOrder System

制造业工单系统（MES），由三个独立 Git 仓库组成，前后端完全分离。

---

## 仓库结构

```
D:\Program\tools\
├── easywork\          ← 后端 Spring Boot（主仓库）
├── easywork-admin\    ← 管理端前端 Vue3（待创建）
└── easywork-worker\   ← 工人端前端 Vue3（待创建）
```

---

## 各项目概览

### easywork（后端）
- **技术栈**：Java 21 / Spring Boot 3.2 / MyBatis-Plus 3.5 / Spring Security 6 / JWT
- **数据库**：PostgreSQL 15（端口 5432，库名 `workorder_db`）
- **缓存**：Redis（端口 6379）
- **运行端口**：`8080`
- **启动命令**：`docker-compose up -d postgres redis` → `mvn spring-boot:run`
- **Swagger**：`http://localhost:8080/swagger-ui.html`
- **包路径**：`com.xiaobai.workorder`
- **测试**：98 个单元测试，全部通过（`mvn test`）

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
| report | `modules/report` | 报工状态机（核心） |
| inspection | `modules/inspection` | 质检 |
| call | `modules/call` | Andon 呼叫 |
| statistics | `modules/statistics` | 统计看板 |
| mesintegration | `modules/mesintegration` | MES 双向同步 |

---

## 工单状态流转

```
NOT_STARTED → STARTED → REPORTED → INSPECT_PASSED / INSPECT_FAILED → COMPLETED
```

---

## 注意事项

- 后端使用 `SessionCreationPolicy.STATELESS`，前端不依赖 Cookie/Session
- MES 集成默认关闭（`app.mes.integration.enabled: false`），联调时按需开启
- 逻辑删除字段：`deleted`（1=已删除，0=正常）

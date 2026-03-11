# XiaoBai Easy WorkOrder System

轻量级制造业工单系统（MES）。

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
  │  · 派工（人员或班组）      │      │  · 呼叫（Andon/质检/运输） │
  │  · 质检录入               │      │                           │
  │  · 用户 / 班组管理         │      └───────────────────────────┘
  │  · MES 同步监控            │
  └───────────────────────────┘
```

三个仓库独立部署，通过 JWT Token 区分角色权限（ADMIN / WORKER）。

| 仓库 | 说明 |
|------|------|
| `easywork` | 后端 Spring Boot，提供全部 REST API |
| `easywork-admin` | 管理端前端，调用 `/api/admin/**` |
| `easywork-worker` | 工人端前端，调用 `/api/device/**` |

---

## 环境要求

- Java 21
- Maven 3.9+
- Docker & Docker Compose

## 启动方法

### 1. 启动基础设施（PostgreSQL + Redis）

```bash
docker-compose up -d postgres redis
```

### 2. 启动应用

> 注意：必须使用 Java 21，系统默认 Java 版本若高于 21 需显式指定。

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home mvn spring-boot:run
```

## 访问地址

- API 基础地址：`http://localhost:8080`
- Swagger 文档：`http://localhost:8080/swagger-ui.html`

## 默认账号

| 员工号 | 密码 |
|--------|------|
| `ADMIN001` | `admin123` |

## 停止服务

```bash
docker-compose down
```

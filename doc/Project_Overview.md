# XiaoBai Easy WorkOrder System — 项目总览文档

> **版本**：1.0.0 · **更新日期**：2026-03-10 · **语言**：Java 21 · **框架**：Spring Boot 3.2.3

---

## 目录

1. [项目概述](#1-项目概述)
2. [架构设计](#2-架构设计)
3. [配置与依赖说明](#3-配置与依赖说明)
4. [使用示例](#4-使用示例)
5. [备注：已知问题与优化建议](#5-备注已知问题与优化建议)

---

## 1. 项目概述

### 1.1 系统目标

**XiaoBai Easy WorkOrder System**（小白轻量工单系统）是一套面向制造业车间现场的轻量级 MES 终端系统。其核心目标是：

- 为工人提供简洁的工单执行入口（扫码开工、报工、呼叫）
- 为管理人员提供工单创建、指派、质检、统计的后台控制台
- 作为车间执行层与上游 MES 系统之间的双向数据桥梁

### 1.2 集成客户端

系统同时服务三类客户端：

| 客户端 | 类型 | 接入方式 |
|---|---|---|
| 工业手持报工设备 | 硬件（带按键和扫码器） | REST API (`/api/device/**`) |
| 移动 App | iOS / Android | REST API（与手持设备相同路径） |
| Web 管理控制台 | 浏览器 | REST API (`/api/admin/**`) |

### 1.3 核心功能

#### 手持设备端

| 功能 | 说明 |
|---|---|
| 员工登录 | 工号 + 密码，JWT 鉴权 |
| 查看工单 | 查询个人 / 班组下已指派的工单列表 |
| 开始工序 | 将工序状态从 `NOT_STARTED` 变更为 `STARTED` |
| 报工 | 填写完工数量（默认填充剩余数量），校验不可超报 |
| 扫码开工 | 扫描工单条码，系统自动定位当前用户最早未完成工序并开工 |
| 扫码报工 | 扫描工单条码，系统自动定位并执行报工 |
| 撤销报工 | 撤销最近一次报工记录 |
| 呼叫 Andon | 触发设备 / 异常呼叫 |
| 呼叫质检 | 触发质检请求 |
| 呼叫运输 | 触发物料搬运请求 |

#### 管理控制台端

| 功能 | 说明 |
|---|---|
| 用户管理 | 创建 / 查询工人账号 |
| 班组管理 | 创建班组、分配成员 |
| 工单管理 | 创建工单（含工序）、按状态筛选、查看详情 |
| 工序指派 | 将工序指派给个人或班组 |
| 质检管理 | 提交质检结果（通过 / 不通过），驱动工单状态流转 |
| 生产统计 | 工单完工率、按类型统计、工人产出统计 |
| MES 集成监控 | 查看同步日志、成功 / 失败统计 |

#### MES 集成

| 方向 | 功能 |
|---|---|
| **入站（MES → 本系统）** | MES 通过 Webhook 推送工单，自动创建工单 + 工序 + 指派 |
| **出站（本系统 → MES）** | 报工完成、工单状态变更、质检结果完成后异步推送至 MES |

### 1.4 工单类型与状态机

#### 工单类型

| 类型 | 说明 |
|---|---|
| `PRODUCTION` | 生产工单（主体类型） |
| `INSPECTION` | 质检工单 |
| `TRANSPORT` | 运输工单 |
| `ANDON` | 异常处理工单 |

#### 生产工单状态流转

```
NOT_STARTED ──► STARTED ──► REPORTED ──► INSPECT_PASSED ──► COMPLETED
                                │
                                └──► INSPECT_FAILED
```

| 状态 | 触发条件 |
|---|---|
| `NOT_STARTED` | 工单创建初始状态 |
| `STARTED` | 第一个工序被工人开工时自动更新 |
| `REPORTED` | 所有工序报工完成（完工量 ≥ 计划量）时自动更新 |
| `INSPECT_PASSED` | 质检员提交通过结果 |
| `INSPECT_FAILED` | 质检员提交不通过结果 |
| `COMPLETED` | 质检通过后的最终状态 |

#### 工序状态流转

```
NOT_STARTED ──► STARTED ──► REPORTED
```

#### 呼叫记录状态流转

```
NOT_HANDLED ──► HANDLING ──► HANDLED
```

---

## 2. 架构设计

### 2.1 整体架构风格

采用 **DDD 启发的模块化单体（Modular Monolith）** 架构：

- 所有模块部署在同一个 Spring Boot 进程中，避免分布式复杂性
- 模块间通过 Spring 应用事件（`ApplicationEvent`）解耦，不直接调用彼此的 Service（关键路径除外）
- 对外暴露统一的 REST API，由 Spring Security 做认证授权

```
┌─────────────────────────────────────────────────────────┐
│                    Spring Boot Application               │
│                                                         │
│  ┌────────────┐ ┌────────────┐ ┌──────────────────────┐ │
│  │ Device API │ │ Admin API  │ │   MES Webhook API    │ │
│  │ /api/device│ │ /api/admin │ │   /api/mes           │ │
│  └─────┬──────┘ └─────┬──────┘ └──────────┬───────────┘ │
│        │              │                   │             │
│  ┌─────▼──────────────▼───────────────────▼───────────┐ │
│  │                  Security Layer                     │ │
│  │   JWT Filter → Authentication → Role Authorization  │ │
│  └─────────────────────────┬───────────────────────────┘ │
│                            │                             │
│  ┌──────────┬──────────┬───▼─────┬──────────┬─────────┐  │
│  │  auth    │  user    │  team   │  device  │workorder│  │
│  ├──────────┼──────────┼─────────┼──────────┼─────────┤  │
│  │operation │  report  │inspect  │  call    │statistics│  │
│  └──────────┴──────────┴─────────┴──────────┴─────────┘  │
│                            │                             │
│  ┌─────────────────────────▼───────────────────────────┐ │
│  │               mes-integration module                 │ │
│  │  Events → Async Listener → HTTP Push to MES         │ │
│  └────────────────────────────────────────────────────┘  │
│                                                         │
└──────────────────────────┬──────────────────────────────┘
                           │
            ┌──────────────┼──────────────┐
            ▼              ▼              ▼
       PostgreSQL        Redis      External MES
```

### 2.2 包结构

```
src/main/java/com/xiaobai/workorder/
│
├── WorkOrderApplication.java           # 启动类
│
├── config/                             # 全局配置
│   ├── SecurityConfig.java             # Spring Security + CORS
│   ├── JwtTokenProvider.java           # JWT 生成与验证
│   ├── JwtAuthenticationFilter.java    # JWT 请求过滤器
│   ├── MybatisPlusConfig.java          # 分页插件 + MapperScan
│   ├── MybatisPlusMetaHandler.java     # 自动填充 createdAt/updatedAt
│   ├── RedisConfig.java                # Redis 序列化配置
│   ├── OpenApiConfig.java              # Swagger + Bearer 认证
│   └── AsyncConfig.java                # MES 异步线程池
│
├── common/                             # 通用基础组件
│   ├── response/
│   │   ├── ApiResponse.java            # 统一响应包装
│   │   └── PageResponse.java           # 分页响应包装
│   ├── exception/
│   │   ├── BusinessException.java      # 业务异常（带 code）
│   │   └── GlobalExceptionHandler.java # 全局异常处理
│   ├── constant/
│   │   ├── WorkOrderStatus.java        # 工单状态常量
│   │   └── OperationType.java          # 工序类型常量
│   └── util/
│       └── SecurityUtils.java          # 获取当前登录用户 ID
│
└── modules/                            # 业务模块（各自独立）
    ├── auth/                           # 认证模块
    ├── user/                           # 用户模块
    ├── team/                           # 班组模块
    ├── device/                         # 设备模块（手持 API 入口）
    ├── workorder/                      # 工单模块
    ├── operation/                      # 工序模块
    ├── report/                         # 报工模块
    ├── inspection/                     # 质检模块
    ├── call/                           # 呼叫模块
    ├── statistics/                     # 统计模块
    └── mesintegration/                 # MES 集成模块
```

每个模块内部遵循一致的分层结构：

```
modules/{module}/
├── controller/     REST 控制器
├── service/        业务逻辑
├── repository/     MyBatis Plus Mapper
├── entity/         数据库实体
├── dto/            请求 / 响应 DTO
├── event/          Spring 应用事件（仅 mesintegration）
├── client/         外部 HTTP 客户端（仅 mesintegration）
├── scheduler/      定时任务（仅 mesintegration）
├── config/         模块级配置（仅 mesintegration）
└── constant/       模块级常量（仅 mesintegration）
```

### 2.3 模块职责与依赖关系

```
┌────────┐    ┌────────┐    ┌────────┐
│  auth  │───►│  user  │    │  team  │
└────────┘    └────┬───┘    └───┬────┘
                   │            │
             ┌─────▼────────────▼─────┐
             │        device           │  ← 手持设备 API 聚合层
             │  (DeviceController)     │
             └─┬────┬────┬───┬────┬───┘
               │    │    │   │    │
          work │  op│  rpt│  ins│  call
          order│    │    │   │    │
               │    │    │   │    │
               └────┴────┴───┴────┘
                          │
                          │ ApplicationEvent
                          ▼
               ┌─────────────────────┐
               │   mes-integration   │
               │  (async outbound)   │
               └─────────────────────┘

statistics ──── reads from workorder + report
```

**关键依赖说明**：

- `auth` 模块依赖 `user.UserDetailsServiceImpl` 完成 Spring Security 认证
- `device.DeviceController` 是手持设备的 API 聚合层，内部调用 auth / workorder / report / call 服务
- `report.ReportService` 和 `inspection.InspectionService` 通过 `ApplicationEventPublisher` 发布事件，与 `mesintegration` 解耦
- `mesintegration` 模块通过 `@ConditionalOnProperty` 实现可插拔，禁用时对主业务零影响

### 2.4 数据库设计

#### 数据库表总览

| 表名 | 所属模块 | 说明 |
|---|---|---|
| `users` | user | 工人 / 管理员账号 |
| `teams` | team | 班组 |
| `team_members` | team | 班组成员关系（多对多） |
| `devices` | device | 手持设备注册信息 |
| `work_orders` | workorder | 工单主表 |
| `operations` | operation | 工序（工单子任务） |
| `operation_assignments` | operation | 工序指派关系 |
| `report_records` | report | 报工记录 |
| `inspection_records` | inspection | 质检记录 |
| `call_records` | call | 呼叫记录（Andon/质检/运输） |
| `mes_sync_logs` | mesintegration | MES 同步审计日志 |
| `mes_order_mappings` | mesintegration | 本地工单 ↔ MES 工单 ID 映射 |

#### 实体关系（核心）

```
users ──────────────────────────────────────────────────────┐
  │                                                         │
  ├── [leader_id] teams ──── team_members ──── users        │
  │                                                         │
  ├── devices (last_login_user_id)                          │
  │                                                         │
  └── operation_assignments (user_id / team_id)             │
              │                                             │
         operations ──────────────── work_orders            │
              │                           │                 │
              ├── report_records ──────────┘                │
              │       └── [user_id] ────────────────────────┘
              ├── inspection_records
              └── call_records
```

#### 软删除设计

所有表均使用 `deleted` 字段（`SMALLINT`）实现逻辑删除：

- `deleted = 0`：正常记录
- `deleted = 1`：已删除
- MyBatis Plus `@TableLogic` 注解自动在所有查询中追加 `WHERE deleted = 0`

### 2.5 安全架构

#### JWT 认证流程

```
Client                  Filter                  SecurityContext
  │                       │                          │
  │  Authorization:        │                          │
  │  Bearer <token>        │                          │
  ├───────────────────────►│                          │
  │                        │ extractUsername(token)    │
  │                        │ validateToken(token)      │
  │                        │ loadUserByUsername()      │
  │                        │──────────────────────────►│
  │                        │                           │ setAuthentication()
  │                        │                           │
  │                   filterChain.doFilter()           │
  │◄───────────────────────┴───────────────────────────┘
```

#### 路由授权规则

| 路径 | 角色要求 |
|---|---|
| `/api/auth/**` | 公开（无需认证） |
| `/api/device/login` | 公开（无需认证） |
| `/swagger-ui/**`、`/api-docs/**` | 公开（无需认证） |
| `/api/device/**` | `ROLE_WORKER` 或 `ROLE_ADMIN` |
| `/api/mes/**` | `ROLE_ADMIN` |
| `/api/admin/**` | `ROLE_ADMIN` |
| 其余路径 | 需要登录（任意角色） |

### 2.6 MES 集成事件驱动架构

MES 集成的出站推送采用 **"事务提交后异步触发"** 模式，确保：

1. **业务事务不受干扰**：MES 推送失败不会回滚工人的报工操作
2. **数据一致性**：事件仅在业务事务 `COMMIT` 后才触发，MES 永远接收到已持久化的数据
3. **推送失败自动重试**：`MesRetryScheduler` 每 5 分钟扫描失败记录，自动补发

```
Worker API Call
    │
    ▼
ReportService.reportWork() ─── @Transactional
    │
    ├── INSERT report_records
    ├── UPDATE operations
    ├── UPDATE work_orders
    └── eventPublisher.publishEvent(ReportRecordSavedEvent)
              │
              ▼ 事务 COMMIT
              │
              ▼ @TransactionalEventListener(AFTER_COMMIT)
    MesEventListener.onReportSaved()   ← 独立线程 (mes-async-*)
              │
              ▼
    MesOutboundService.pushReport()
              │
    ┌─────────┴──────────┐
    │   INSERT MesSyncLog │ (REQUIRES_NEW，独立事务保存日志)
    └─────────┬──────────┘
              │
              ▼
    MesApiClient.pushReport()  →  External MES HTTP API
              │
    ┌─────────┴──────────┐
    │ 成功: log.status=SUCCESS │
    │ 失败: log.status=FAILED  │──► MesRetryScheduler (5 min)
    └───────────────────┘
```

### 2.7 全部 API 端点

#### 认证

| 方法 | 路径 | 角色 | 说明 |
|---|---|---|---|
| POST | `/api/auth/login` | 公开 | Web / 管理端登录 |

#### 手持设备 API

| 方法 | 路径 | 角色 | 说明 |
|---|---|---|---|
| POST | `/api/device/login` | 公开 | 设备端登录 |
| GET | `/api/device/work-orders` | WORKER / ADMIN | 查询已指派工单 |
| POST | `/api/device/start` | WORKER / ADMIN | 开始工序 |
| POST | `/api/device/report` | WORKER / ADMIN | 提交报工 |
| POST | `/api/device/scan/start` | WORKER / ADMIN | 扫码开工 |
| POST | `/api/device/scan/report` | WORKER / ADMIN | 扫码报工 |
| POST | `/api/device/report/undo` | WORKER / ADMIN | 撤销报工 |
| POST | `/api/device/call/andon` | WORKER / ADMIN | 呼叫 Andon |
| POST | `/api/device/call/inspection` | WORKER / ADMIN | 呼叫质检 |
| POST | `/api/device/call/transport` | WORKER / ADMIN | 呼叫运输 |

#### 管理端 API

| 方法 | 路径 | 角色 | 说明 |
|---|---|---|---|
| POST | `/api/admin/users` | ADMIN | 创建用户 |
| GET | `/api/admin/users` | ADMIN | 用户列表（分页） |
| POST | `/api/admin/teams` | ADMIN | 创建班组 |
| GET | `/api/admin/teams` | ADMIN | 班组列表 |
| POST | `/api/admin/teams/{teamId}/members` | ADMIN | 添加班组成员 |
| POST | `/api/admin/work-orders` | ADMIN | 创建工单（含工序） |
| GET | `/api/admin/work-orders` | ADMIN | 工单列表（分页 + 状态筛选） |
| GET | `/api/admin/work-orders/{id}` | ADMIN | 工单详情 |
| POST | `/api/admin/work-orders/assign` | ADMIN | 工序指派 |
| POST | `/api/admin/inspections` | ADMIN | 提交质检结果 |
| GET | `/api/admin/statistics/dashboard` | ADMIN | 生产统计看板 |
| GET | `/api/admin/mes-integration/stats` | ADMIN | MES 同步统计 |
| GET | `/api/admin/mes-integration/logs` | ADMIN | MES 同步日志列表 |

#### MES Webhook

| 方法 | 路径 | 角色 | 说明 |
|---|---|---|---|
| POST | `/api/mes/work-orders/import` | ADMIN (JWT) | MES 推入工单（幂等） |

#### 文档

| 路径 | 说明 |
|---|---|
| `/swagger-ui.html` | Swagger 交互文档 |
| `/api-docs` | OpenAPI JSON 规范 |

---

## 3. 配置与依赖说明

### 3.1 Maven 依赖

```xml
<!-- pom.xml 关键依赖 -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.3</version>
</parent>
```

| 依赖 | 版本 | 用途 |
|---|---|---|
| `spring-boot-starter-web` | 3.2.3 | REST API、内嵌 Tomcat |
| `spring-boot-starter-security` | 3.2.3 | 认证、授权 |
| `spring-boot-starter-validation` | 3.2.3 | 请求参数校验（Jakarta Validation） |
| `spring-boot-starter-data-redis` | 3.2.3 | Redis 集成（Lettuce 连接池） |
| `postgresql` | runtime | PostgreSQL JDBC 驱动 |
| `mybatis-plus-boot-starter` | **3.5.5** | ORM（分页、逻辑删除、自动填充） |
| `jjwt-api` | **0.12.5** | JWT API |
| `jjwt-impl` | 0.12.5 | JWT 实现（runtime） |
| `jjwt-jackson` | 0.12.5 | JWT JSON 序列化（runtime） |
| `springdoc-openapi-starter-webmvc-ui` | **2.3.0** | Swagger UI + OpenAPI 文档 |
| `lombok` | (Spring Boot 管理) | 样板代码注解 |
| `mapstruct` | **1.5.5.Final** | DTO 映射（注解处理器生成） |
| `lombok-mapstruct-binding` | **0.2.0** | Lombok + MapStruct 兼容层 |
| `spring-boot-starter-test` | 3.2.3 | 单元测试（JUnit 5、Mockito） |

### 3.2 application.yml 完整配置说明

```yaml
spring:
  application:
    name: xiaobai-workorder           # 服务名

  # ── 数据库 ──────────────────────────────────────────────
  datasource:
    driver-class-name: org.postgresql.Driver
    url: jdbc:postgresql://localhost:5432/workorder_db
    username: workorder_user
    password: workorder_pass          # 生产环境应使用环境变量
    hikari:
      minimum-idle: 5                 # 最小空闲连接
      maximum-pool-size: 20           # 最大连接数
      idle-timeout: 300000            # 空闲连接超时（5分钟）
      connection-timeout: 20000       # 获取连接超时（20秒）
      max-lifetime: 1200000           # 连接最大存活时间（20分钟）

  # ── Redis ───────────────────────────────────────────────
  data:
    redis:
      host: localhost
      port: 6379
      database: 0
      timeout: 3000ms
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 2
          max-wait: -1ms              # 无限等待

# ── MyBatis Plus ──────────────────────────────────────────
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true   # 自动驼峰映射
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
  global-config:
    db-config:
      id-type: auto                   # 主键自增（BIGSERIAL）
      logic-delete-field: deleted     # 逻辑删除字段名
      logic-delete-value: 1           # 删除标记值
      logic-not-delete-value: 0       # 正常标记值

# ── 日志 ──────────────────────────────────────────────────
logging:
  level:
    root: INFO
    com.xiaobai.workorder: DEBUG      # 业务代码详细日志
  file:
    name: logs/workorder.log
    max-size: 10MB
    max-history: 30                   # 保留30天

# ── Swagger ───────────────────────────────────────────────
springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html

# ── 应用自定义配置 ────────────────────────────────────────
app:
  jwt:
    secret: xiaobai-workorder-jwt-secret-key-change-in-production-2024
    expiration: 86400000              # Token 有效期：24小时
    refresh-expiration: 604800000     # 刷新 Token：7天

  security:
    allowed-origins: "*"              # 生产环境应限制具体域名
    allowed-methods: GET,POST,PUT,DELETE,OPTIONS
    allowed-headers: "*"
    max-age: 3600

  mes:
    integration:
      enabled: false                  # 默认关闭，改为 true 启用
      base-url: http://mes-system:8090
      api-key: ""                     # MES 系统 API Key
      connect-timeout-ms: 5000        # HTTP 连接超时
      read-timeout-ms: 10000          # HTTP 读取超时
      retry-delay-ms: 300000          # 失败重试间隔（5分钟）
```

### 3.3 Docker Compose 说明

```yaml
# docker-compose.yml 服务组成
services:
  postgres:           # PostgreSQL 16 Alpine
    image: postgres:16-alpine
    ports: 5432:5432
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./init.sql:/docker-entrypoint-initdb.d/init.sql  # 自动初始化

  redis:              # Redis 7 Alpine
    image: redis:7-alpine
    ports: 6379:6379
    volumes:
      - redis_data:/data

  app:                # 应用服务
    build: .          # 使用项目根目录 Dockerfile 构建
    ports: 8080:8080
    depends_on:
      - postgres
      - redis
    environment:
      # 容器内覆盖数据库连接配置
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/workorder_db
      SPRING_DATASOURCE_USERNAME: workorder_user
      SPRING_DATASOURCE_PASSWORD: workorder_pass
      SPRING_DATA_REDIS_HOST: redis
```

#### Dockerfile

```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 3.4 数据库初始化

```bash
# 初始化文件：init.sql
# 自动执行时机：PostgreSQL 容器首次启动时
# 包含内容：
#   - 创建所有表（含索引、外键约束）
#   - 插入默认管理员账号

# 默认管理员账号
员工号: ADMIN001
用户名: admin
密码:   admin123（BCrypt 加密存储）
角色:   ADMIN
```

---

## 4. 使用示例

### 4.1 启动项目

#### 方式一：Docker Compose 全量启动（推荐）

```bash
# 1. 构建应用 JAR
mvn clean package -DskipTests

# 2. 启动所有服务（PostgreSQL + Redis + App）
docker-compose up -d

# 3. 查看日志
docker-compose logs -f app

# 访问地址：
# API:        http://localhost:8080
# Swagger UI: http://localhost:8080/swagger-ui.html
```

#### 方式二：本地开发启动

```bash
# 1. 启动基础设施
docker-compose up -d postgres redis

# 2. 运行 Spring Boot
mvn spring-boot:run

# 或者
java -jar target/workorder-1.0.0.jar
```

---

### 4.2 管理端：创建工单并指派

#### 第一步：管理员登录

```http
POST /api/auth/login
Content-Type: application/json

{
  "employeeNumber": "ADMIN001",
  "password": "admin123"
}
```

**响应：**

```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "employeeNumber": "ADMIN001",
    "realName": "System Admin",
    "role": "ADMIN",
    "userId": 1
  },
  "timestamp": 1710000000000
}
```

#### 第二步：创建工人账号

```http
POST /api/admin/users
Authorization: Bearer <token>
Content-Type: application/json

{
  "employeeNumber": "W001",
  "username": "zhangsan",
  "password": "worker123",
  "realName": "张三",
  "phone": "13800138001",
  "role": "WORKER"
}
```

#### 第三步：创建工单（含工序）

```http
POST /api/admin/work-orders
Authorization: Bearer <token>
Content-Type: application/json

{
  "orderNumber": "WO-2024-001",
  "orderType": "PRODUCTION",
  "productCode": "PROD-A",
  "productName": "产品 A",
  "plannedQuantity": 100,
  "priority": 1,
  "plannedStartTime": "2024-03-11T08:00:00",
  "plannedEndTime": "2024-03-11T17:00:00",
  "workshop": "一车间",
  "productionLine": "线体1",
  "operations": [
    {
      "operationName": "组装",
      "operationType": "PRODUCTION",
      "sequenceNumber": 1,
      "plannedQuantity": 100,
      "stationCode": "ST-001",
      "stationName": "组装台1"
    },
    {
      "operationName": "检验",
      "operationType": "INSPECTION",
      "sequenceNumber": 2,
      "plannedQuantity": 100,
      "stationCode": "ST-002",
      "stationName": "检验台1"
    }
  ]
}
```

**响应（含生成的工序 ID）：**

```json
{
  "code": 200,
  "data": {
    "id": 1,
    "orderNumber": "WO-2024-001",
    "status": "NOT_STARTED",
    "operations": [
      { "id": 1, "operationNumber": "WO-2024-001-OP001", "status": "NOT_STARTED" },
      { "id": 2, "operationNumber": "WO-2024-001-OP002", "status": "NOT_STARTED" }
    ]
  }
}
```

#### 第四步：指派工序给工人

```http
POST /api/admin/work-orders/assign
Authorization: Bearer <token>
Content-Type: application/json

{
  "operationId": 1,
  "assignmentType": "USER",
  "userIds": [2]
}
```

---

### 4.3 手持设备端：工人执行工单

#### 第一步：设备登录

```http
POST /api/device/login
Content-Type: application/json

{
  "employeeNumber": "W001",
  "password": "worker123",
  "deviceCode": "HH-001"
}
```

#### 第二步：查看已指派工单

```http
GET /api/device/work-orders
Authorization: Bearer <worker_token>
```

**响应（按优先级 + 计划开始时间排序）：**

```json
{
  "code": 200,
  "data": [
    {
      "id": 1,
      "orderNumber": "WO-2024-001",
      "productName": "产品 A",
      "plannedQuantity": 100,
      "completedQuantity": 0,
      "remainingQuantity": 100,
      "status": "NOT_STARTED",
      "operations": [
        {
          "id": 1,
          "operationName": "组装",
          "status": "NOT_STARTED",
          "plannedQuantity": 100,
          "completedQuantity": 0
        }
      ]
    }
  ]
}
```

#### 第三步：开始工序

```http
POST /api/device/start
Authorization: Bearer <worker_token>
Content-Type: application/json

{
  "operationId": 1
}
```

#### 第四步：报工（报告完工数量）

```http
POST /api/device/report
Authorization: Bearer <worker_token>
Content-Type: application/json

{
  "operationId": 1,
  "reportedQuantity": 80,
  "qualifiedQuantity": 78,
  "defectQuantity": 2,
  "notes": "第一批完成"
}
```

> **注意**：`reportedQuantity` 不填时默认填充剩余数量（plannedQuantity - alreadyReported）。
> 若填入值超过剩余数量，返回 HTTP 400 错误：`Reported quantity X exceeds remaining quantity Y`

#### 第五步：扫码报工（推荐用于扫码枪场景）

```http
POST /api/device/scan/report
Authorization: Bearer <worker_token>
Content-Type: application/json

{
  "barcode": "WO-2024-001"
}
```

系统自动定位该工人在该工单下最早未完成的工序，使用剩余数量执行报工。

#### 第六步：撤销错误报工

```http
POST /api/device/report/undo
Authorization: Bearer <worker_token>
Content-Type: application/json

{
  "operationId": 1,
  "undoReason": "数量填写错误"
}
```

---

### 4.4 呼叫 Andon / 质检 / 运输

```http
POST /api/device/call/andon
Authorization: Bearer <worker_token>
Content-Type: application/json

{
  "workOrderId": 1,
  "operationId": 1,
  "description": "设备 ST-001 异常，需要维修"
}
```

```http
POST /api/device/call/inspection
Authorization: Bearer <worker_token>
Content-Type: application/json

{
  "workOrderId": 1
}
```

```http
POST /api/device/call/transport
Authorization: Bearer <worker_token>
Content-Type: application/json

{
  "workOrderId": 1,
  "description": "需要补充物料：螺丝 M4×8，50 个"
}
```

---

### 4.5 管理端：提交质检结果

```http
POST /api/admin/inspections
Authorization: Bearer <admin_token>
Content-Type: application/json

{
  "workOrderId": 1,
  "inspectionResult": "PASSED",
  "inspectedQuantity": 100,
  "qualifiedQuantity": 98,
  "defectQuantity": 2,
  "defectReason": "外观划痕",
  "notes": "整体合格，2件外观缺陷"
}
```

> 提交后，工单状态自动流转：`REPORTED` → `INSPECT_PASSED`
> 若 `inspectionResult` 为 `"FAILED"`，则流转为 `INSPECT_FAILED`

---

### 4.6 MES 集成：导入工单

> 前提：MES 系统已获取一个 ADMIN 角色的 JWT Token（通过 `/api/auth/login`）

```http
POST /api/mes/work-orders/import
Authorization: Bearer <mes_service_account_token>
Content-Type: application/json

{
  "mesOrderId": "MES-ORD-2024-0311-001",
  "mesOrderNumber": "PO-20240311-001",
  "orderType": "PRODUCTION",
  "productCode": "PROD-B",
  "productName": "产品 B",
  "plannedQuantity": 200,
  "priority": 2,
  "plannedStartTime": "2024-03-12T08:00:00",
  "plannedEndTime": "2024-03-12T18:00:00",
  "workshop": "二车间",
  "productionLine": "线体2",
  "operations": [
    {
      "operationName": "焊接",
      "operationType": "PRODUCTION",
      "sequenceNumber": 1,
      "plannedQuantity": 200,
      "stationCode": "ST-201",
      "assignedEmployeeNumbers": ["W001", "W002"],
      "assignedTeamCodes": []
    }
  ]
}
```

**响应：**

```json
{
  "code": 200,
  "data": {
    "mesOrderId": "MES-ORD-2024-0311-001",
    "mesOrderNumber": "PO-20240311-001",
    "localOrderId": 5,
    "localOrderNumber": "MES-PO-20240311-001",
    "syncStatus": "SYNCED",
    "message": "Import successful"
  }
}
```

> **幂等性**：相同 `mesOrderId` 重复调用，返回原有映射，不创建重复工单。

---

### 4.7 启用 MES 出站推送

修改 `application.yml`：

```yaml
app:
  mes:
    integration:
      enabled: true
      base-url: http://your-mes-system.example.com
      api-key: your-secret-api-key
      connect-timeout-ms: 5000
      read-timeout-ms: 10000
      retry-delay-ms: 300000
```

启用后，以下事件自动触发出站推送：

| 触发时机 | 推送内容 | MES 接收路径 |
|---|---|---|
| 工人提交报工 | `MesReportPushPayload`（含报工明细、员工信息） | `POST {base-url}/api/workorder/report` |
| 工单状态变更 | `MesStatusPushPayload`（含前后状态） | `POST {base-url}/api/workorder/status` |
| 质检结果提交 | `MesInspectionPushPayload`（含质检详情） | `POST {base-url}/api/workorder/inspection` |

**查看同步状态：**

```http
GET /api/admin/mes-integration/stats
Authorization: Bearer <admin_token>
```

```json
{
  "data": {
    "totalLogs": 128,
    "successCount": 125,
    "failedCount": 1,
    "retryingCount": 2,
    "pendingCount": 0,
    "countBySyncType": {
      "REPORT_PUSH": 80,
      "STATUS_PUSH": 40,
      "INSPECTION_PUSH": 8
    }
  }
}
```

---

### 4.8 查看生产统计

```http
GET /api/admin/statistics/dashboard
Authorization: Bearer <admin_token>
```

```json
{
  "data": {
    "totalWorkOrders": 50,
    "notStartedCount": 10,
    "startedCount": 15,
    "reportedCount": 18,
    "completedCount": 7,
    "overallCompletionRate": 50.0,
    "typeStats": [
      { "orderType": "PRODUCTION", "count": 45, "completedCount": 7 }
    ],
    "workerStats": [
      {
        "userId": 2,
        "employeeNumber": "W001",
        "realName": "张三",
        "reportCount": 32,
        "totalReported": 3200
      }
    ]
  }
}
```

---

## 5. 备注：已知问题与优化建议

### 5.1 安全加固（生产环境必须处理）

| 问题 | 当前状态 | 建议 |
|---|---|---|
| JWT 签名算法 | HMAC-SHA256（对称密钥） | 改用 RS256（非对称），私钥签名、公钥验证 |
| JWT Secret | 硬编码在 `application.yml` | 使用环境变量或 Vault 注入，`${JWT_SECRET}` |
| CORS | `allowedOrigins: "*"` 通配符 | 明确指定允许的前端域名 |
| HTTPS | 未配置 TLS | 在反向代理（Nginx）或 Spring Boot 层配置 TLS 证书 |
| 密码重置 | 无接口 | 补充密码修改 API |
| 登录失败锁定 | 未实现 | 添加连续登录失败后账号锁定机制 |
| MES Webhook 验签 | 未实现 | 对入站请求增加 HMAC 签名验证 |

### 5.2 功能补全

| 缺失功能 | 说明 |
|---|---|
| 设备注册 API | 当前无创建设备的 Admin API，需手动插库 |
| 工单状态标注 `COMPLETED` | 目前流程止于 `INSPECT_PASSED`，缺少显式关闭接口 |
| 分批报工跨班次统计 | `undoReport` 只撤销最近一次，跨班次多次报工的撤销策略未明确 |
| 质检驳回重工流程 | `INSPECT_FAILED` 后如何重新开工未定义 |
| 工单优先级调整 | 创建后无法修改 priority |
| 工序指派变更 | 创建后无法取消或变更已有指派 |
| 运输/Andon 工单独立工序 | `OperationType.TRANSPORT / ANDON` 有定义但无独立状态机 |

### 5.3 性能优化

| 问题 | 说明 | 建议 |
|---|---|---|
| 统计全量扫描 | `StatisticsService` 将所有工单、报工记录全量加载到内存 | 改用 SQL 聚合查询（`GROUP BY` + `COUNT`），或引入物化视图 |
| Redis 未使用 | Bean 已配置但无缓存逻辑 | 对高频读取（用户信息、班组成员、工单详情）加 Redis 缓存层 |
| WorkOrderDTO 嵌套查询 | `toDTO()` 方法中对每个工单逐一查询工序 | 在 `WorkOrderMapper` 中用 JOIN 一次性查出，或加批量缓存 |
| MES 重试无退避 | 固定 5 分钟间隔 | 改为指数退避（1min → 2min → 4min → 8min → ...），降低 MES 压力 |

### 5.4 可观测性

| 方向 | 当前状态 | 建议 |
|---|---|---|
| 监控指标 | 无 | 集成 `spring-boot-starter-actuator` + Micrometer + Prometheus |
| 链路追踪 | 无 | 集成 Micrometer Tracing（Zipkin / Jaeger） |
| 告警 | 无 | 基于 MES 同步失败次数设置告警阈值 |
| 审计日志 | 仅 MES 同步日志 | 补充关键操作审计（谁、何时、对哪个工单做了什么） |

### 5.5 测试覆盖

| 测试类型 | 当前状态 | 建议 |
|---|---|---|
| 单元测试 | 未生成 | 为 `ReportService`、`WorkOrderService`、`MesInboundService` 编写核心业务逻辑单测 |
| 集成测试 | 未生成 | 使用 `@SpringBootTest` + Testcontainers（PostgreSQL、Redis）做端到端测试 |
| MES 集成测试 | 未生成 | Mock MES 服务端，测试重试、幂等、推送失败回滚等场景 |

### 5.6 未来扩展方向

| 方向 | 说明 |
|---|---|
| 多工厂 / 多车间支持 | 当前 `workshop` 为字符串字段，扩展为独立实体并关联权限 |
| 班次管理 | 添加班次（早班/中班/晚班）实体，报工记录关联班次 |
| 物料管理 | 当前只有工单，可扩展 BOM（物料清单）关联 |
| WebSocket 实时推送 | Andon 呼叫发生时实时通知管理端，无需手动刷新 |
| 微服务拆分 | 当模块规模增大时，`mesintegration`、`statistics` 可独立为微服务 |
| 国际化（i18n） | 当前错误信息为英文，可引入 `MessageSource` 支持中英文切换 |

---

> **文档生成工具**：本文档根据源代码自动分析生成。
> **项目路径**：`com.xiaobai.workorder`
> **API 文档**：启动后访问 [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

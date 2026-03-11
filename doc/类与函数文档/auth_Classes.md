# auth 模块 — 类与函数文档

## 1. 模块概述

`auth` 模块负责系统的身份认证功能，是整个工单系统的安全入口。模块通过 Spring Security 的 `AuthenticationManager` 完成用户凭证校验，并基于 JJWT 库生成 JWT（JSON Web Token）令牌，供后续请求鉴权使用。模块同时支持设备登录记录，适用于 Web 端与移动/硬件设备端的统一认证场景。

模块包结构如下：

```
modules/auth/
├── controller/   # HTTP 接口层（AuthController）
├── dto/          # 数据传输对象（LoginRequest、LoginResponse）
└── service/      # 业务逻辑层（AuthService）
```

---

## 2. 核心类列表

| 类名 | 所在文件 | 职责描述 |
|------|---------|---------|
| `AuthController` | `controller/AuthController.java` | HTTP 入口控制器，暴露 `/api/auth/login` 登录接口，接收请求并委托给 `AuthService` 处理 |
| `AuthService` | `service/AuthService.java` | 认证业务逻辑核心，完成凭证验证、用户状态检查、设备登录记录、JWT 生成等完整登录流程 |
| `LoginRequest` | `dto/LoginRequest.java` | 登录请求数据传输对象，携带工号、密码、设备码等入参，包含 Bean Validation 约束 |
| `LoginResponse` | `dto/LoginResponse.java` | 登录响应数据传输对象，封装 JWT 令牌及用户基本信息，作为登录成功的返回体 |

---

## 3. 类详细说明

### 3.1 AuthController

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/auth/controller/AuthController.java`
**类型：** Controller
**职责：** RESTful 控制器，负责将 HTTP 登录请求映射到服务层，并将服务层结果包装为统一 API 响应格式返回给客户端。使用 Swagger（OpenAPI 3）注解完善接口文档。

#### 方法列表

| 方法名 | 访问修饰符 | 返回类型 | 功能描述 |
|--------|-----------|---------|---------|
| `login(LoginRequest)` | `public` | `ApiResponse<LoginResponse>` | 处理用户登录请求，验证通过后返回 JWT 令牌及用户信息 |

#### 方法详情

##### login(@Valid @RequestBody LoginRequest request)
- **功能：** 接收客户端 POST 请求，将登录参数传递给 `AuthService.login()` 完成完整认证流程，并将结果包装在 `ApiResponse` 中返回。
- **HTTP 映射：** `POST /api/auth/login`
- **参数：**
  - `request` (LoginRequest)：请求体，包含工号、密码及可选的设备码，由 `@Valid` 触发参数校验
- **返回值：** `ApiResponse<LoginResponse>` — 统一响应封装，`data` 为含 JWT token 及用户信息的 `LoginResponse`
- **异常：** `BusinessException(401)` 工号或密码错误；`BusinessException(403)` 账号已禁用；`MethodArgumentNotValidException` 字段校验失败

---

### 3.2 AuthService

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/auth/service/AuthService.java`
**类型：** Service
**职责：** 认证模块核心业务类，编排完整的登录流程：凭证鉴别 → 用户查询 → 状态检查 → 设备登录记录 → JWT 签发 → 响应构建。

#### 依赖注入

| 依赖 | 类型 | 用途 |
|------|------|------|
| `authenticationManager` | `AuthenticationManager` | Spring Security 认证管理器，执行工号密码校验 |
| `jwtTokenProvider` | `JwtTokenProvider` | JWT 令牌生成工具（config 包） |
| `userMapper` | `UserMapper` | 查询用户实体 |
| `deviceService` | `DeviceService` | 记录设备登录事件 |

#### 方法列表

| 方法名 | 访问修饰符 | 返回类型 | 功能描述 |
|--------|-----------|---------|---------|
| `login(LoginRequest)` | `public` | `LoginResponse` | 执行完整的用户登录流程，验证凭证、检查状态、记录设备、签发 JWT |

#### 方法详情

##### login(LoginRequest request)
- **功能：** 以工号和密码为凭证，通过 Spring Security `AuthenticationManager` 执行认证；认证通过后查询用户详情、校验账号状态；若携带设备码则记录设备登录；最终生成并返回 JWT 令牌及用户信息。
- **参数：**
  - `request` (LoginRequest)：`employeeNumber` 工号；`password` 登录密码；`deviceCode` 可选设备码
- **返回值：** `LoginResponse` — 包含 `token`、`employeeNumber`、`realName`、`role`、`userId`
- **异常：**
  - `BusinessException(401)` — 工号或密码错误（捕获 `AuthenticationException` 后重新包装）
  - `BusinessException(400, "User not found")` — 数据库中无此用户
  - `BusinessException(403)` — 账号非 `ACTIVE` 状态
- **备注：**
  - 业务执行步骤：
    1. `authenticationManager.authenticate(...)` 执行凭证校验
    2. `userMapper.findByEmployeeNumber(...)` 查询用户实体
    3. 校验 `user.getStatus() == "ACTIVE"`
    4. 若 `deviceCode` 非空：`deviceService.recordLogin(deviceCode, userId)` 记录设备登录
    5. `jwtTokenProvider.generateToken(employeeNumber, userId, role)` 生成 JWT
    6. 构建并返回 `LoginResponse`
  - 使用 `@Slf4j` 记录成功登录日志：`User {} logged in successfully`
  - 设备登录记录为非强制流程，仅 `deviceCode` 非空且非空白时执行

---

### 3.3 LoginRequest

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/auth/dto/LoginRequest.java`
**类型：** DTO（请求体）
**职责：** 封装用户登录接口的请求参数，使用 Lombok `@Data` 自动生成 getter/setter，通过 Jakarta Validation 注解约束必填字段。

#### 字段说明

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| `employeeNumber` | `String` | `@NotBlank(message = "Employee number is required")` | 用户工号，不能为空 |
| `password` | `String` | `@NotBlank(message = "Password is required")` | 登录密码，不能为空 |
| `deviceCode` | `String` | 无 | 可选，设备端登录时传入设备编码，用于记录设备登录历史 |

---

### 3.4 LoginResponse

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/auth/dto/LoginResponse.java`
**类型：** DTO（响应体）
**职责：** 封装登录成功后返回给客户端的数据，包含 JWT 令牌和用户基本信息。使用 Lombok `@Data` + `@AllArgsConstructor` 生成全参数构造器。

#### 字段说明

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `token` | `String` | JWT 令牌字符串，客户端后续请求在 `Authorization: Bearer {token}` 头部携带 |
| `employeeNumber` | `String` | 用户工号 |
| `realName` | `String` | 用户真实姓名 |
| `role` | `String` | 用户角色（`WORKER` 或 `ADMIN`） |
| `userId` | `Long` | 用户主键 ID |

---

## 4. 模块内调用关系

```
HTTP POST /api/auth/login
        │
        ▼
AuthController.login(request)
        │  @Valid 触发参数校验
        ▼
AuthService.login(request)
        │
        ├─① AuthenticationManager.authenticate(UsernamePasswordAuthenticationToken)
        │         └─ 内部调用 UserDetailsServiceImpl.loadUserByUsername(employeeNumber)
        │                    └─ UserMapper.findByEmployeeNumber(employeeNumber)
        │
        ├─② UserMapper.findByEmployeeNumber(employeeNumber)   [再次查询以获取 User 实体]
        │
        ├─③ 校验 user.status == "ACTIVE"
        │
        ├─④ DeviceService.recordLogin(deviceCode, userId)   [仅 deviceCode 非空时执行]
        │         └─ DeviceMapper.findByDeviceCode(deviceCode)
        │         └─ DeviceMapper.updateById(device)
        │
        ├─⑤ JwtTokenProvider.generateToken(employeeNumber, userId, role)
        │
        └─⑥ new LoginResponse(token, employeeNumber, realName, role, userId)
```

**跨模块依赖：**

| 被依赖类 | 所在模块 | 用途 |
|---------|---------|------|
| `JwtTokenProvider` | `config` | 生成 JWT 令牌 |
| `UserMapper` | `modules.user` | 查询用户实体 |
| `DeviceService` | `modules.device` | 记录设备登录事件 |
| `BusinessException` | `common.exception` | 业务异常抛出 |
| `ApiResponse` | `common.response` | 统一响应体封装 |

---

## 5. 重要业务逻辑说明

### 5.1 完整登录流程
登录流程分为五个串行步骤：凭证鉴别 → 用户查询 → 状态检查 → 设备记录 → JWT 签发。任一步骤失败均会抛出对应的 `BusinessException`，由全局异常处理器 `GlobalExceptionHandler` 统一转换为 HTTP 错误响应。

### 5.2 Spring Security 认证机制
`AuthService` 通过调用 `AuthenticationManager.authenticate()` 触发 Spring Security 认证链。认证链内部回调 `UserDetailsServiceImpl.loadUserByUsername()`（以工号为 username），查询用户并与 BCrypt 编码的密码比对。认证失败抛出 `AuthenticationException`，被捕获后转换为 `BusinessException(401)`。

### 5.3 设备登录记录
当请求携带非空 `deviceCode` 时，`AuthService` 调用 `DeviceService.recordLogin()` 更新设备的 `lastLoginAt` 和 `lastLoginUserId` 字段，记录哪台设备、哪个用户在何时登录。设备状态非 `ACTIVE` 时 `DeviceService` 会抛出 `BusinessException`，阻止登录流程继续。

### 5.4 JWT 结构与有效期
`JwtTokenProvider.generateToken()` 生成的 JWT Claims 包含：
- `sub`：工号（employeeNumber）
- `userId`：用户主键 ID
- `role`：用户角色（如 `WORKER`、`ADMIN`）
- `iat`：签发时间
- `exp`：过期时间（由 `application.yml` 中 `app.jwt.expiration` 配置，默认 86400000 ms = 24 小时）

### 5.5 双入口设计
系统存在两个登录入口：
- `AuthController`（`/api/auth/login`）：Web/Admin 端登录，`ADMIN` 和 `WORKER` 角色均可使用
- `DeviceController`（`/api/device/login`）：设备端登录，内部同样委托 `AuthService.login()`，两者共用同一认证逻辑，通过 `deviceCode` 字段区分是否为设备端

### 5.6 账号状态校验
`AuthService` 在 Spring Security 认证成功后额外校验 `user.getStatus() == "ACTIVE"`。若账号被禁用（非 `ACTIVE` 状态），即使密码正确也拒绝登录，返回 403。这与 Spring Security 的 `UserDetails.isEnabled()` 等机制形成补充，提供了业务层面的账号状态控制。

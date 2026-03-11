# user 模块 — 类与函数文档

## 1. 模块概述

user 模块是 EasyWork 工单系统的用户管理核心模块，负责用户的创建、查询、认证等基础能力。该模块基于 Spring Boot + MyBatis-Plus 构建，集成了 Spring Security 的用户认证体系，提供面向管理员的 RESTful API 接口。

模块包结构如下：

```
modules/user/
├── controller/   # HTTP 接口层（AdminUserController）
├── dto/          # 数据传输对象（CreateUserRequest、UserDTO）
├── entity/       # 数据库实体（User）
├── repository/   # 数据访问层（UserMapper）
└── service/      # 业务逻辑层（UserService、UserDetailsServiceImpl）
```

---

## 2. 核心类列表

| 类名 | 所在文件 | 职责描述 |
|------|---------|---------|
| `AdminUserController` | `controller/AdminUserController.java` | 管理员用户管理 REST 控制器，提供创建用户和分页查询用户列表接口 |
| `CreateUserRequest` | `dto/CreateUserRequest.java` | 创建用户请求的数据传输对象，携带输入校验注解 |
| `UserDTO` | `dto/UserDTO.java` | 用户信息的对外输出数据传输对象，屏蔽敏感字段（如密码） |
| `User` | `entity/User.java` | 用户数据库实体，映射 `users` 表，支持逻辑删除与自动填充审计字段 |
| `UserMapper` | `repository/UserMapper.java` | MyBatis-Plus Mapper 接口，提供基础 CRUD 及按工号查询的扩展方法 |
| `UserDetailsServiceImpl` | `service/UserDetailsServiceImpl.java` | Spring Security 用户认证服务实现，将系统用户转换为安全框架所需的 UserDetails |
| `UserService` | `service/UserService.java` | 用户核心业务服务，封装用户创建、查询及实体转 DTO 等业务逻辑 |

---

## 3. 类详细说明

### 3.1 AdminUserController

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/user/controller/AdminUserController.java`
**类型：** Controller
**职责：** 对外暴露管理员用户管理相关的 HTTP 接口，路由前缀为 `/api/admin/users`。

#### 方法列表

| 方法名 | 访问修饰符 | 返回类型 | 功能描述 |
|--------|-----------|---------|---------|
| `createUser` | `public` | `ApiResponse<UserDTO>` | 创建新用户，对请求体进行参数校验后委托 Service 层处理 |
| `listUsers` | `public` | `ApiResponse<List<UserDTO>>` | 分页查询所有用户列表，支持页码与每页条数参数 |

#### 方法详情

##### createUser(@Valid @RequestBody CreateUserRequest request)
- **功能：** 接收 POST 请求，校验参数后调用 `UserService.createUser`，将结果包装为统一响应体返回。
- **HTTP 映射：** `POST /api/admin/users`
- **参数：**
  - `request` (CreateUserRequest)：创建用户的请求数据，`@Valid` 触发 Bean Validation 校验
- **返回值：** `ApiResponse<UserDTO>` — 包含新创建用户信息的统一响应对象

##### listUsers(int page, int size)
- **功能：** 接收分页查询请求，返回系统中所有用户的分页数据。
- **HTTP 映射：** `GET /api/admin/users?page={page}&size={size}`
- **参数：**
  - `page` (int)：页码，默认值为 `1`
  - `size` (int)：每页条数，默认值为 `20`
- **返回值：** `ApiResponse<List<UserDTO>>` — 包含当前页用户列表的统一响应对象

---

### 3.2 CreateUserRequest

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/user/dto/CreateUserRequest.java`
**类型：** DTO（请求体）
**职责：** 承载创建用户接口的请求数据，使用 Jakarta Validation 注解对关键字段进行约束校验。

#### 字段说明

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| `employeeNumber` | `String` | `@NotBlank` | 工号，不能为空 |
| `username` | `String` | `@NotBlank` | 用户名，不能为空 |
| `password` | `String` | `@NotBlank`、`@Size(min=6)` | 密码，不能为空且至少 6 位 |
| `realName` | `String` | 无 | 真实姓名，可选 |
| `phone` | `String` | 无 | 手机号，可选 |
| `email` | `String` | 无 | 邮箱地址，可选 |
| `role` | `String` | 默认值 `"WORKER"` | 用户角色，默认为普通工人 |

---

### 3.3 UserDTO

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/user/dto/UserDTO.java`
**类型：** DTO（响应体）
**职责：** 用户信息的对外输出载体，仅暴露安全字段，不包含密码等敏感信息。

#### 字段说明

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `id` | `Long` | 用户主键 ID |
| `employeeNumber` | `String` | 工号 |
| `username` | `String` | 用户名 |
| `realName` | `String` | 真实姓名 |
| `phone` | `String` | 手机号 |
| `email` | `String` | 邮箱地址 |
| `role` | `String` | 角色标识 |
| `status` | `String` | 账号状态 |

---

### 3.4 User

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/user/entity/User.java`
**类型：** Entity（数据库实体）
**职责：** 映射数据库 `users` 表，集成 MyBatis-Plus 逻辑删除与审计字段自动填充能力。

#### 字段说明

| 字段名 | 类型 | 注解 | 说明 |
|--------|------|------|------|
| `id` | `Long` | `@TableId(type = IdType.AUTO)` | 主键，数据库自增 |
| `employeeNumber` | `String` | — | 工号，业务唯一标识 |
| `username` | `String` | — | 用户名 |
| `password` | `String` | — | BCrypt 加密后的密码 |
| `realName` | `String` | — | 真实姓名 |
| `phone` | `String` | — | 手机号 |
| `email` | `String` | — | 邮箱 |
| `role` | `String` | — | 角色 |
| `status` | `String` | — | 账号状态 |
| `deleted` | `Integer` | `@TableLogic` | 逻辑删除标志：0 正常，1 已删除 |
| `createdAt` | `LocalDateTime` | `@TableField(fill = INSERT)` | 创建时间，插入时自动填充 |
| `updatedAt` | `LocalDateTime` | `@TableField(fill = INSERT_UPDATE)` | 更新时间，插入和更新时自动填充 |
| `createdBy` | `Long` | — | 创建人用户 ID |
| `updatedBy` | `Long` | — | 最后更新人用户 ID |

---

### 3.5 UserMapper

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/user/repository/UserMapper.java`
**类型：** Mapper（数据访问接口）
**职责：** 继承 MyBatis-Plus `BaseMapper<User>`，自动获得全套 CRUD 方法，并通过 `default` 方法扩展了按工号查询用户的业务能力。

#### 方法列表

| 方法名 | 访问修饰符 | 返回类型 | 功能描述 |
|--------|-----------|---------|---------|
| `findByEmployeeNumber` | `public default` | `Optional<User>` | 根据工号查询未删除的用户 |

#### 方法详情

##### findByEmployeeNumber(String employeeNumber)
- **功能：** 用 `LambdaQueryWrapper` 按工号精确查询且过滤已删除记录。
- **参数：**
  - `employeeNumber` (String)：用户工号
- **返回值：** `Optional<User>` — 存在则包含实体，不存在则为 `Optional.empty()`
- **备注：** 等价 SQL：`WHERE employee_number = ? AND deleted = 0`

---

### 3.6 UserDetailsServiceImpl

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/user/service/UserDetailsServiceImpl.java`
**类型：** Service（Spring Security 集成）
**职责：** 实现 `UserDetailsService`，将系统 `User` 实体转换为 Spring Security 的 `UserDetails`。

#### 方法列表

| 方法名 | 访问修饰符 | 返回类型 | 功能描述 |
|--------|-----------|---------|---------|
| `loadUserByUsername` | `public` | `UserDetails` | 根据工号加载用户认证信息 |

#### 方法详情

##### loadUserByUsername(String username)
- **功能：** 认证流程回调。以工号查询用户，封装为 Spring Security User 对象，角色添加 `ROLE_` 前缀。
- **参数：**
  - `username` (String)：实际为工号（employeeNumber）
- **返回值：** `UserDetails` — 含工号、密码哈希、权限列表
- **异常：** `UsernameNotFoundException` — 工号不存在时抛出
- **备注：** 直接依赖 `UserMapper`，不经过 `UserService`，避免循环依赖。

---

### 3.7 UserService

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/user/service/UserService.java`
**类型：** Service（核心业务）
**职责：** 封装用户模块核心业务逻辑，含创建、查询、分页及实体转 DTO。

#### 方法列表

| 方法名 | 访问修饰符 | 返回类型 | 功能描述 |
|--------|-----------|---------|---------|
| `findByEmployeeNumber` | `public` | `User` | 按工号查询用户实体，不存在则抛业务异常 |
| `findById` | `public` | `User` | 按主键 ID 查询用户实体，不存在或已删除则抛业务异常 |
| `createUser` | `public` | `UserDTO` | 创建新用户，含唯一性校验、密码加密、持久化 |
| `listUsers` | `public` | `List<UserDTO>` | 分页查询所有未删除用户，按 ID 升序 |
| `toDTO` | `private` | `UserDTO` | 实体转 DTO，屏蔽敏感字段 |

#### 方法详情

##### findByEmployeeNumber(String employeeNumber)
- **功能：** 调用 Mapper 查询，不存在则抛出 `BusinessException`。
- **参数：**
  - `employeeNumber` (String)：用户工号
- **返回值：** `User` — 数据库用户实体
- **异常：** `BusinessException` — `"User not found: {employeeNumber}"`

##### findById(Long userId)
- **功能：** 按主键查询，对 null 和逻辑删除做双重防御校验。
- **参数：**
  - `userId` (Long)：用户主键 ID
- **返回值：** `User` — 数据库用户实体
- **异常：** `BusinessException` — `"User not found: {userId}"`

##### createUser(CreateUserRequest request)
- **功能：** 校验工号唯一性 → 构建实体（密码 BCrypt 加密）→ 插入数据库 → 转换并返回 DTO。
- **参数：**
  - `request` (CreateUserRequest)：创建请求对象
- **返回值：** `UserDTO` — 新创建用户的 DTO（不含密码）
- **异常：** `BusinessException` — `"Employee number already exists: {employeeNumber}"`
- **备注：** 标注 `@Transactional`；角色为 null 时默认 `"WORKER"`；状态默认 `"ACTIVE"`。

##### listUsers(int page, int size)
- **功能：** 分页查询未删除用户，按 ID 升序，结果转为 DTO 列表。
- **参数：**
  - `page` (int)：页码（从 1 开始）
  - `size` (int)：每页条数
- **返回值：** `List<UserDTO>` — 当前页用户 DTO 列表

##### toDTO(User user)
- **功能：** 手动映射实体字段到 DTO，排除密码、deleted 等内部字段。
- **参数：**
  - `user` (User)：数据库用户实体
- **返回值：** `UserDTO` — 安全的对外传输对象
- **备注：** 私有方法，被 `createUser` 和 `listUsers` 复用。

---

## 4. 模块内调用关系

```
HTTP 请求
    │
    ▼
AdminUserController
    │  createUser(request) / listUsers(page, size)
    ▼
UserService
    │  userMapper.findByEmployeeNumber()   ← 工号唯一性校验 / 工号查询
    │  userMapper.selectById()             ← ID 查询
    │  userMapper.insert()                 ← 持久化新用户
    │  userMapper.selectPage()             ← 分页查询
    │  passwordEncoder.encode()            ← 密码加密
    │  toDTO()                             ← 内部实体转 DTO
    ▼
UserMapper（BaseMapper<User> + 自定义 default 方法）
    ▼
数据库 users 表

──── Spring Security 认证（独立链路）────

AuthenticationManager
    ▼
UserDetailsServiceImpl
    │  userMapper.findByEmployeeNumber()
    ▼
UserMapper → 数据库 users 表
```

---

## 5. 重要业务逻辑说明

### 5.1 工号作为系统唯一业务标识
系统以 `employeeNumber` 而非 `username` 作为唯一业务标识。Spring Security 登录时传入的 username 参数实际是工号，`UserDetailsServiceImpl` 据此查询用户并构建认证主体，与企业 HR 系统保持一致。

### 5.2 密码安全存储
`UserService.createUser` 使用注入的 `PasswordEncoder`（BCrypt 算法）加密密码后再落库。BCrypt 每次加密随机生成盐值，相同明文产生不同哈希，有效防范彩虹表攻击。

### 5.3 逻辑删除机制
`deleted` 字段配合 `@TableLogic` 实现软删除。MyBatis-Plus 自动在查询中追加 `AND deleted = 0`，删除操作更新该字段而非执行 `DELETE` SQL，保留历史数据可追溯性。

### 5.4 审计字段自动填充
`createdAt` 和 `updatedAt` 通过 `@TableField(fill = ...)` 声明策略，由 common 模块的 `MetaObjectHandler` 在 ORM 层自动注入时间，业务代码无需手动赋值。

### 5.5 角色权限模型
当前为单角色模型，`UserDetailsServiceImpl` 统一为角色添加 `ROLE_` 前缀（如 `WORKER` → `ROLE_WORKER`），符合 Spring Security `hasRole()` 约定，可平滑扩展为多角色模型。

### 5.6 DTO 与 Entity 的隔离设计
- `CreateUserRequest`：携带校验约束的输入对象
- `User`：持久层实体，含密码、逻辑删除等内部字段
- `UserDTO`：对外响应对象，仅含安全字段

三者职责分明，通过 `UserService.toDTO()` 在业务层完成转换，防止数据库结构直接暴露到 API 层。

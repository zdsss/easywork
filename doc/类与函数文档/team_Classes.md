# team 模块 — 类与函数文档

## 1. 模块概述

team（班组）模块是 EasyWork 工单系统的班组管理核心模块，提供班组的创建、查询以及成员管理等功能。该模块遵循标准的 Spring Boot 分层架构，由 Controller → Service → Repository（Mapper）→ Entity 四层组成，并通过 DTO 对象在各层之间传递数据，对外暴露 RESTful 管理接口，供管理端调用。

模块包结构如下：

```
modules/team/
├── controller/   # HTTP 接口层（AdminTeamController）
├── dto/          # 数据传输对象（CreateTeamRequest、TeamDTO）
├── entity/       # 数据库实体（Team、TeamMember）
├── repository/   # 数据访问层（TeamMapper、TeamMemberMapper）
└── service/      # 业务逻辑层（TeamService）
```

---

## 2. 核心类列表

| 类名 | 所在文件 | 职责描述 |
|------|---------|---------|
| `AdminTeamController` | `controller/AdminTeamController.java` | 管理端班组 REST 控制器，对外暴露班组创建、查询、添加成员接口 |
| `CreateTeamRequest` | `dto/CreateTeamRequest.java` | 创建班组的请求数据传输对象，包含参数校验注解 |
| `TeamDTO` | `dto/TeamDTO.java` | 班组信息响应数据传输对象，含成员信息内部类 `MemberInfo` |
| `Team` | `entity/Team.java` | 班组数据库实体类，映射 `teams` 表 |
| `TeamMember` | `entity/TeamMember.java` | 班组成员数据库实体类，映射 `team_members` 表 |
| `TeamMapper` | `repository/TeamMapper.java` | 班组数据访问接口，继承 MyBatis-Plus `BaseMapper`，扩展按编码查询方法 |
| `TeamMemberMapper` | `repository/TeamMemberMapper.java` | 班组成员数据访问接口，继承 MyBatis-Plus `BaseMapper`，扩展按班组/用户查询方法 |
| `TeamService` | `service/TeamService.java` | 班组核心业务逻辑层，处理创建、添加成员、查询等业务，并完成实体到 DTO 的转换 |

---

## 3. 类详细说明

### 3.1 AdminTeamController

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/team/controller/AdminTeamController.java`
**类型：** Controller
**职责：** 管理端班组管理 REST 控制器，路由前缀为 `/api/admin/teams`，提供班组创建、班组列表查询、班组成员添加三个 HTTP 接口。

#### 方法列表

| 方法名 | 访问修饰符 | 返回类型 | 功能描述 |
|--------|-----------|---------|---------|
| `createTeam` | `public` | `ApiResponse<TeamDTO>` | 创建新班组，返回创建后的班组详情 |
| `listTeams` | `public` | `ApiResponse<List<TeamDTO>>` | 查询所有有效班组列表 |
| `addMembers` | `public` | `ApiResponse<Void>` | 向指定班组批量添加成员 |

#### 方法详情

##### createTeam(@Valid @RequestBody CreateTeamRequest request)
- **功能：** 接收创建班组请求，获取当前用户 ID 作为创建人，调用 `TeamService.createTeam` 完成班组创建，返回新建班组的 DTO 信息。
- **HTTP 方法：** `POST /api/admin/teams`
- **参数：**
  - `request` (CreateTeamRequest)：班组创建请求体，带 `@Valid` 校验
- **返回值：** `ApiResponse<TeamDTO>` — 封装创建成功的班组详情数据

##### listTeams()
- **功能：** 查询系统内所有未删除的班组，以列表形式返回。
- **HTTP 方法：** `GET /api/admin/teams`
- **返回值：** `ApiResponse<List<TeamDTO>>` — 封装班组 DTO 列表

##### addMembers(@PathVariable Long teamId, @RequestBody Map<String, List<Long>> body)
- **功能：** 向指定班组批量添加成员，幂等处理。
- **HTTP 方法：** `POST /api/admin/teams/{teamId}/members`
- **参数：**
  - `teamId` (Long)：路径参数，目标班组 ID
  - `body` (Map<String, List<Long>>)：请求体，格式为 `{ "userIds": [1, 2, 3] }`
- **返回值：** `ApiResponse<Void>` — 成功时返回提示信息

---

### 3.2 CreateTeamRequest

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/team/dto/CreateTeamRequest.java`
**类型：** DTO（请求体）
**职责：** 承载创建班组的请求数据，使用 Jakarta Validation 注解对关键字段进行约束校验。

#### 字段说明

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| `teamCode` | `String` | `@NotBlank` | 班组编码，全局唯一 |
| `teamName` | `String` | `@NotBlank` | 班组名称 |
| `leaderUserId` | `Long` | 无 | 班组长用户 ID，可选 |
| `memberUserIds` | `List<Long>` | 无 | 初始成员 ID 列表，可选 |

---

### 3.3 TeamDTO

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/team/dto/TeamDTO.java`
**类型：** DTO（响应体）
**职责：** 班组信息的对外输出载体，包含班组基本信息及成员列表。

#### 字段说明

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `id` | `Long` | 班组主键 ID |
| `teamCode` | `String` | 班组编码 |
| `teamName` | `String` | 班组名称 |
| `leaderName` | `String` | 班组长姓名 |
| `status` | `String` | 班组状态 |
| `members` | `List<MemberInfo>` | 成员信息列表 |

#### 内部类 MemberInfo

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `userId` | `Long` | 成员用户 ID |
| `realName` | `String` | 成员真实姓名 |
| `employeeNumber` | `String` | 成员工号 |

---

### 3.4 Team

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/team/entity/Team.java`
**类型：** Entity（数据库实体）
**职责：** 映射数据库 `teams` 表，承载班组基本信息。

#### 字段说明

| 字段名 | 类型 | 注解 | 说明 |
|--------|------|------|------|
| `id` | `Long` | `@TableId(type = IdType.AUTO)` | 主键，数据库自增 |
| `teamCode` | `String` | — | 班组编码，业务唯一标识 |
| `teamName` | `String` | — | 班组名称 |
| `leaderUserId` | `Long` | — | 班组长用户 ID |
| `status` | `String` | — | 班组状态（ACTIVE 等） |
| `deleted` | `Integer` | `@TableLogic` | 逻辑删除标志 |
| `createdAt` | `LocalDateTime` | `@TableField(fill = INSERT)` | 创建时间 |
| `updatedAt` | `LocalDateTime` | `@TableField(fill = INSERT_UPDATE)` | 更新时间 |
| `createdBy` | `Long` | — | 创建人用户 ID |

---

### 3.5 TeamMember

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/team/entity/TeamMember.java`
**类型：** Entity（数据库实体）
**职责：** 映射数据库 `team_members` 表，记录班组与用户之间的多对多关联关系。

#### 字段说明

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `id` | `Long` | 主键，数据库自增 |
| `teamId` | `Long` | 所属班组 ID |
| `userId` | `Long` | 成员用户 ID |
| `deleted` | `Integer` | 逻辑删除标志 |
| `createdAt` | `LocalDateTime` | 加入时间 |

---

### 3.6 TeamMapper

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/team/repository/TeamMapper.java`
**类型：** Mapper（数据访问接口）
**职责：** 继承 MyBatis-Plus `BaseMapper<Team>`，扩展了按班组编码查询的 default 方法。

#### 方法列表

| 方法名 | 访问修饰符 | 返回类型 | 功能描述 |
|--------|-----------|---------|---------|
| `findByTeamCode` | `public default` | `Optional<Team>` | 按班组编码查询未删除的班组记录 |

#### 方法详情

##### findByTeamCode(String teamCode)
- **功能：** 根据班组编码精确查询一条未被逻辑删除的班组记录。
- **参数：**
  - `teamCode` (String)：班组唯一编码
- **返回值：** `Optional<Team>` — 存在则包含实体，否则为空

---

### 3.7 TeamMemberMapper

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/team/repository/TeamMemberMapper.java`
**类型：** Mapper（数据访问接口）
**职责：** 继承 MyBatis-Plus `BaseMapper<TeamMember>`，扩展了按班组 ID 和用户 ID 查询成员的方法。

#### 方法列表

| 方法名 | 访问修饰符 | 返回类型 | 功能描述 |
|--------|-----------|---------|---------|
| `findByTeamId` | `public default` | `List<TeamMember>` | 查询指定班组下所有未删除的成员记录 |
| `findByUserIdAndTeamId` | `public default` | `Optional<TeamMember>` | 查询特定用户是否属于指定班组 |

---

### 3.8 TeamService

**文件路径：** `src/main/java/com/xiaobai/workorder/modules/team/service/TeamService.java`
**类型：** Service（核心业务）
**职责：** 封装班组相关的核心业务逻辑，包含创建班组、批量添加成员、查询班组列表等。

#### 方法列表

| 方法名 | 访问修饰符 | 返回类型 | 功能描述 |
|--------|-----------|---------|---------|
| `createTeam` | `public` | `TeamDTO` | 校验编码唯一性后创建班组，并批量绑定初始成员 |
| `addMembers` | `public` | `void` | 幂等批量添加成员，重复成员自动跳过 |
| `listTeams` | `public` | `List<TeamDTO>` | 查询所有有效班组，含负责人姓名和成员详细信息 |
| `toDTO` | `private` | `TeamDTO` | 实体转 DTO，关联查询负责人和成员用户信息 |

#### 方法详情

##### createTeam(CreateTeamRequest request, Long createdBy)
- **功能：** 校验 `teamCode` 唯一性 → 构建并插入班组实体 → 批量插入初始成员 → 转换并返回 DTO。
- **参数：**
  - `request` (CreateTeamRequest)：创建请求
  - `createdBy` (Long)：创建人用户 ID
- **返回值：** `TeamDTO` — 新建班组及其成员信息
- **异常：** `BusinessException` — `"Team code already exists: {teamCode}"`
- **备注：** `@Transactional`，新建班组状态固定为 `"ACTIVE"`

##### addMembers(Long teamId, List<Long> userIds)
- **功能：** 查询班组合法性 → 逐一检查成员是否已存在 → 插入不存在的成员记录。
- **参数：**
  - `teamId` (Long)：目标班组 ID
  - `userIds` (List<Long>)：待添加的用户 ID 列表
- **返回值：** `void`
- **异常：** `BusinessException` — 班组不存在时抛出
- **备注：** 幂等操作，可安全重复调用

##### listTeams()
- **功能：** 查询所有 `deleted=0` 的班组，逐个调用 `toDTO` 转换。
- **返回值：** `List<TeamDTO>` — 班组列表（含成员信息）
- **备注：** 存在 N+1 查询问题，建议后续优化

---

## 4. 模块内调用关系

```
AdminTeamController
    ├── createTeam → TeamService.createTeam
    │                    ├── TeamMapper.findByTeamCode（唯一性校验）
    │                    ├── TeamMapper.insert（插入班组）
    │                    ├── TeamMemberMapper.insert × N（插入初始成员）
    │                    └── toDTO → UserMapper + TeamMemberMapper
    │
    ├── listTeams  → TeamService.listTeams
    │                    ├── TeamMapper.selectList
    │                    └── toDTO × M
    │
    └── addMembers → TeamService.addMembers
                         ├── TeamMapper.selectById（校验班组）
                         ├── TeamMemberMapper.selectList（幂等检查）
                         └── TeamMemberMapper.insert × M（插入新成员）
```

**跨模块依赖：** `TeamService` 依赖 `modules.user.repository.UserMapper` 查询用户信息，构建成员姓名、工号等展示字段。

---

## 5. 重要业务逻辑说明

### 5.1 班组编码唯一性
创建前校验 `teamCode` 在有效记录中的唯一性，逻辑删除的记录不参与校验，允许复用已删除班组的编码。

### 5.2 逻辑删除
通过 `@TableLogic` 实现软删除，MyBatis-Plus 自动在查询中追加 `deleted = 0` 过滤条件。

### 5.3 添加成员幂等性
`addMembers` 在插入前逐一检查成员是否已存在，已存在则跳过，保证接口可重复安全调用。

### 5.4 事务保护
`createTeam` 和 `addMembers` 均使用 `@Transactional`，保证班组与成员数据的原子性写入。

### 5.5 N+1 查询性能风险
`toDTO` 方法在转换时对每位成员单独查询用户信息，`listTeams` 时总查询次数为 `M×(N+2)`，建议后续优化为批量查询或 JOIN。

### 5.6 双路径工单派工集成
`team_members` 表被 `workorder` 模块的 `WorkOrderMapper` 通过 SQL JOIN 用于"班组分配"路径的工单查询，是跨模块协作的核心桥梁表。

# 趣溪 Tracci — 后端服务

面向「趣溪 Tracci」全旅程旅行管理产品（H5 + 微信小程序 + iOS）的 Spring Boot 后端：**旅前规划 → 旅中记录 → 旅后分享**，提供统一 REST API、JWT 鉴权、旅行域数据与第三方集成扩展点。

---

## 目录

- [功能概览](#功能概览)
- [技术栈](#技术栈)
- [仓库与文档](#仓库与文档)
- [环境要求](#环境要求)
- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [运行与构建](#运行与构建)
- [API 与接口文档](#api-与接口文档)
- [架构与健康检查](#架构与健康检查)
- [贡献与约定](#贡献与约定)

---

## 功能概览

后端业务主功能已实现（协作、AA、反馈、统计 analysis、公开分享、阶段概览等）。

| 域 | 说明 |
| --- | --- |
| 认证与用户 | 邮箱密码、微信（小程序 + iOS App）、账号注销；短信 Service 预留 |
| 旅行 | CRUD、行程、天气、协作（成员/邀请/活动日志）、公开分享 |
| 开支 | 多币种记账、AA 分摊与 settlement |
| 旅行日志 | 时间轴、图片、精选照片（每 trip 最多 9 张） |
| 聚合 | Summary、Stats（含 analysis）、Trip stage overview、Export assets |
| 反馈 | 帮助与反馈提交与列表 |
| 未实现 | `/utils/*`、`trip_exports`、会员订阅 |

架构与实现状态以代码为准；数据库结构见 Flyway 迁移脚本 `src/main/resources/db/migration/`。

---

## 技术栈

与当前工程依赖一致（见 `build.gradle`）：

| 类别 | 选型 |
| --- | --- |
| 语言 / 运行时 | Java 21 |
| 框架 | Spring Boot 3.3.x |
| 持久化 | PostgreSQL 17、MyBatis-Plus |
| 缓存 | Redis（Spring Data Redis） |
| 安全 | JWT（JJWT 0.12.x） |
| API 文档 | SpringDoc OpenAPI（Swagger UI） |
| 可观测 | Spring Boot Actuator |
| 构建 | Gradle（Kotlin DSL 风格配置于 `build.gradle`） |

## 仓库与文档

接口契约以 SpringDoc 注解与 Swagger UI 为准；数据库结构见 Flyway 迁移脚本 `src/main/resources/db/migration/`。

---

## 环境要求

- **JDK 21**（与 Gradle toolchain 一致）
- **PostgreSQL 17**：开发环境默认库名见 `application-dev.yml`（如 `tracci_dev`），时间字段按 UTC 写入与读取
- **Redis**：用于验证码、刷新令牌、汇率缓存等

---

## 快速开始

### 1. 准备数据库

创建空库（如 `tracci_dev`）。**Schema 由 Flyway 在应用启动时自动迁移**（`src/main/resources/db/migration/V*.sql`）。

创建空库后直接启动即可，Flyway 会自动完成全部迁移。

### 2. 配置数据源与 Redis

编辑 `src/main/resources/application-dev.yml`（或在本机使用与配置文件一致的库名、账号与 Redis 地址）。默认示例指向本机 PostgreSQL 与 Redis，**生产环境禁止提交真实密钥**，应通过环境变量或部署平台密钥管理注入。

### 3. 启动应用

在项目根目录执行：

```bash
./gradlew bootRun
```

默认端口为 **8080**（见 `application.yml`）。激活的 profile 默认为 `dev`（`spring.profiles.active`）。

---

## 配置说明

- **公共配置**：`src/main/resources/application.yml`
- **开发 / 生产**：`application-dev.yml`、`application-prod.yml`

生产环境与敏感配置项（JWT 密钥、云厂商 AK/SK、微信 AppSecret、数据库与 Redis 密码等）**不应写入仓库**，应通过环境变量注入；变量名见各 `application*.yml` 中的 `${...}` 占位符。

开发环境可对短信、微信等集成开启 Mock（示例见 `application-dev.yml` 中 `tracci.integration.*.mock` 与 `tracci.wechat.mini-program.mock`）。

微信小程序登录使用后端换取微信身份，生产环境需配置：

| 环境变量 | 说明 |
|---|---|
| `WECHAT_MINI_APP_ID` | 微信小程序 AppID |
| `WECHAT_MINI_APP_SECRET` | 微信小程序 AppSecret |
| `WECHAT_MINI_MOCK` | 开发环境 mock 开关，默认 `true` |

---

## 运行与构建

| 操作 | 命令 |
| --- | --- |
| 运行（开发） | `./gradlew bootRun` |
| 打包可执行 JAR | `./gradlew bootJar` |
| 测试 | `./gradlew test` |

制品路径一般为 `build/libs/` 下的 `*-SNAPSHOT.jar`（具体以构建输出为准）。

---

## API 与接口文档

- **Base URL 前缀**：`/api/v1`
- **统一响应**：`{ "code", "message", "data" }`
- **本地 Swagger UI**（默认未改端口时）：  
  `http://localhost:8080/swagger-ui.html`  
  OpenAPI JSON：`http://localhost:8080/v3/api-docs`

---

## 架构与健康检查

- **分层与模块**：Controller → Service → Mapper；数据权限在 Service 层校验。
- **健康检查**：Actuator 已启用 `health`、`info`（见 `application.yml`）。默认示例：  
  `http://localhost:8080/actuator/health`

---

## 贡献与约定

- 统一异常、DTO 命名、数据归属等约定以现有代码风格为准。
- 接口变更请保持 SpringDoc 注解与实现一致。
- 提交前在本地执行 `./gradlew test`，确保改动可编译、测试通过。

---

## 许可证

[MIT](LICENSE)

---

*产品：趣溪 Tracci · 后端仓库：`tracci-backend`*

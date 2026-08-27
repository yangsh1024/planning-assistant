# 服务端开发约束

## 技术与构建

- 使用 JDK 21、Spring Boot 3.3、MyBatis-Plus、MySQL 8 和 Maven 多模块结构。
- 从本目录构建：`mvn test`；若本机默认 Java 版本不为 21，使用 Temurin 21 的 `JAVA_HOME` 后再执行 Maven。
- 可执行应用模块是 `planning-assistant-app`；`planning-assistant-common` 存放公共响应、异常和安全代码，`planning-assistant-core` 存放业务领域代码。
- 数据库初始化由外部执行 `scripts/init-dev-database.sql`；不要假定应用启动时会自动建表。新增或变更表结构时，同步更新该脚本与 `planning-assistant-app/src/main/resources/schema.sql`。

## 分层与权限

- Controller 只处理 HTTP 输入输出，不捕获业务异常；业务失败由 `BizException` 和 `GlobalExceptionHandler` 统一转换。
- Service 层不接受外部传入的 `userId`；从 `UserContext` / Spring Security 上下文取得当前用户。
- `agent` 包（后续阶段）只能依赖各业务模块的 `service` 包，禁止直接依赖 `repository` 包或直连数据库。
- 避免循环查询；需要批处理时使用 MyBatis-Plus 批量能力或明确的批量 SQL。

## 风格与数据

- Java 类使用 `UpperCamelCase`，方法和变量使用 `lowerCamelCase`，常量使用 `UPPER_SNAKE_CASE`；包名全小写；表和列使用 `lower_snake_case`。
- 新增通用逻辑前，先检查 `planning-assistant-common` 是否已有合适实现。
- 修改数据库结构时同步更新领域对象、Mapper、DTO、服务逻辑、`schema.sql`、`ARCHITECTURE.md` 和 `api-contract.yaml`。
- 不在源码或 `application.yml` 中写入真实数据库凭据、JWT 密钥或微信密钥。
